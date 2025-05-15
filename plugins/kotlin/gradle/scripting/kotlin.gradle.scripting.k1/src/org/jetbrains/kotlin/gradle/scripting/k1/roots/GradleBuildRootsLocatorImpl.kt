// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k1.roots

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.gradle.scripting.k1.roots.GradleScriptingSupport.Companion.isApplicable
import org.jetbrains.kotlin.gradle.scripting.shared.kotlinDslScriptsModelImportSupported
import org.jetbrains.kotlin.gradle.scripting.shared.roots.*
import org.jetbrains.kotlin.gradle.scripting.shared.scriptConfigurationsNeedToBeUpdated
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptingSupport
import org.jetbrains.kotlin.idea.core.script.configuration.ScriptingSupport
import org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsBuilder
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import java.nio.file.Path

/**
 * [org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRoot] is a linked gradle build (don't confuse with gradle project and included build).
 * Each [org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRoot] may have it's own Gradle version, Java home and other settings.
 *
 * Typically, IntelliJ project have no more than one [org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRoot].
 *
 * This manager allows to find related Gradle build by the Gradle Kotlin script file path.
 * Each imported build have info about all of it's Kotlin Build Scripts.
 * It is populated by calling [update], stored in FS and will be loaded from FS on next project opening
 *
 * It also used to show related notification and floating actions depending on root kind, state and script state itself.
 *
 * Roots may be:
 * - [org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRoot] - Linked project, that may be itself:
 *   - [org.jetbrains.kotlin.gradle.scripting.shared.roots.Legacy] - Gradle build with old Gradle version (<6.0)
 *   - [org.jetbrains.kotlin.gradle.scripting.shared.roots.New] - not yet imported
 *   - [org.jetbrains.kotlin.gradle.scripting.shared.roots.Imported] - imported
 */
class GradleBuildRootsLocatorImpl(val project: Project, private val coroutineScope: CoroutineScope) : GradleBuildRootsLocator(project, coroutineScope) {
    private val manager: CompositeScriptConfigurationManager
        get() = ScriptConfigurationManager.getInstance(project) as CompositeScriptConfigurationManager

    private val updater
        get() = manager.updater

    var enabled: Boolean = true
        set(value) {
            if (value != field) {
                field = value
                roots.list.toList().forEach {
                    reloadBuildRoot(it.pathPrefix, null)
                }
            }
        }

    override fun getScriptInfo(localPath: String): GradleScriptInfo? =
        manager.getLightScriptInfo(localPath) as? GradleScriptInfo

    override fun updateStandaloneScripts(update: StandaloneScriptsUpdater.() -> Unit) {
        val changes = StandaloneScriptsUpdater.collectChanges(delegate = roots, update)

        updateNotifications { it in changes.new || it in changes.removed }
        loadStandaloneScriptConfigurations(changes.new)
    }

    override fun loadLinkedRoot(settings: GradleProjectSettings, version: String): GradleBuildRoot {
        if (!enabled) {
            return Legacy(settings)
        }

        val supported = kotlinDslScriptsModelImportSupported(version)

        return if (supported) {
            tryLoadFromFsCache(settings, version) ?: New(settings)
        } else {
            Legacy(settings)
        }
    }

    override fun add(newRoot: GradleBuildRoot) {
        val old = roots.add(newRoot)
        if (old is Imported && newRoot !is Imported) {
            removeData(old.pathPrefix)
        }
        if (old !is Legacy || newRoot !is Legacy) {
            updater.invalidateAndCommit()
        }

        updateNotifications { it.startsWith(newRoot.pathPrefix) }
    }

    private fun tryLoadFromFsCache(settings: GradleProjectSettings, version: String): Imported? {
        return tryCreateImportedRoot(settings.externalProjectPath) {
            GradleBuildRootDataSerializer.getInstance().read(it)?.let { data ->
                val gradleHome = data.gradleHome
                if (gradleHome.isNotBlank() && GradleInstallationManager.getGradleVersion(Path.of(gradleHome)) != version) return@let null

                addFromSettings(data, settings)
            }
        }
    }

    private fun addFromSettings(
        data: GradleBuildRootData,
        settings: GradleProjectSettings
    ) = data.copy(projectRoots = data.projectRoots.toSet() + settings.modules)

    override fun remove(rootPath: String) {
        val removed = roots.remove(rootPath)
        if (removed is Imported) {
            removeData(rootPath)
            updater.invalidateAndCommit()
        }

        updateNotifications { it.startsWith(rootPath) }
    }

    override fun updateNotifications(
        restartAnalyzer: Boolean,
        shouldUpdatePath: (String) -> Boolean
    ) {
        if (!project.isOpen) return

        // import notification is a balloon, so should be shown only for selected editor
        FileEditorManager.getInstance(project).selectedEditor?.file?.let {
            if (shouldUpdatePath(it.path) && maybeAffectedGradleProjectFile(it.path)) {
                updateFloatingAction(it)
            }
        }

        val openedScripts = FileEditorManager.getInstance(project).selectedEditors
            .mapNotNull { it.file }
            .filter {
                shouldUpdatePath(it.path) && maybeAffectedGradleProjectFile(it.path)
            }

        if (openedScripts.isEmpty()) return

        coroutineScope.launch(Dispatchers.EDT) {
            //maybe readaction
          writeIntentReadAction {
            if (project.isDisposed) return@writeIntentReadAction

            openedScripts.forEach {
              if (isApplicable(it, project)) {
                DefaultScriptingSupport.getInstance(project).ensureNotificationsRemoved(it)
              }

              if (restartAnalyzer) {
                val kotlinCodeBlockModificationListenerClass = Class.forName(
                  "org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener")
                kotlinCodeBlockModificationListenerClass
                  .getMethod("incModificationCount")
                  .invoke(
                    @Suppress("IncorrectServiceRetrieving")
                    project.getService(kotlinCodeBlockModificationListenerClass),
                  )

                // this required only for "pause" state
                PsiManager.getInstance(project).findFile(it)?.let { ktFile ->
                  DaemonCodeAnalyzer.getInstance(project).restart(ktFile)
                }

              }

              EditorNotifications.getInstance(project).updateAllNotifications()
            }
          }
        }
    }

    private fun updateFloatingAction(file: VirtualFile) {
        if (isConfigurationOutOfDate(file)) {
          scriptConfigurationsNeedToBeUpdated(project, file)
        }
    }

    private fun loadStandaloneScriptConfigurations(files: MutableSet<String>) {
      runReadAction {
        files.forEach {
          val virtualFile = LocalFileSystem.getInstance().findFileByPath(it)
          if (virtualFile != null) {
            val ktFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile
            if (ktFile != null) {
              DefaultScriptingSupport.getInstance(project)
                .ensureUpToDatedConfigurationSuggested(ktFile, skipNotification = true)
            }
          }
        }
      }
    }
}

class GradleScriptingSupport(val project: Project) : ScriptingSupport {
    private val manager: GradleBuildRootsLocator
        get() = GradleBuildRootsLocator.getInstance(project)

    override fun isApplicable(file: VirtualFile): Boolean {
        with(manager) {
            val scriptUnderRoot = findScriptBuildRoot(file) ?: return false
            if (scriptUnderRoot.nearest is Legacy) return false
            if (roots.isStandaloneScript(file.path)) return false
            return true
        }
    }

    override fun isConfigurationLoadingInProgress(file: KtFile): Boolean {
        with(manager) {
            return findScriptBuildRoot(file.originalFile.virtualFile)?.nearest?.isImportingInProgress() ?: return false
        }
    }

    override fun collectConfigurations(builder: ScriptClassRootsBuilder) {
        with(manager) {
            roots.list.forEach { root ->
                if (root is Imported) {
                    root.collectConfigurations(builder)
                }
            }
        }
    }

    override fun afterUpdate() {
        with(manager) {
            roots.list.forEach { root ->
                if (root.importing.compareAndSet(GradleBuildRoot.ImportingStatus.updatingCaches, GradleBuildRoot.ImportingStatus.updated)) {
                    updateNotifications { it.startsWith(root.pathPrefix) }
                }
            }
        }
    }

    companion object {
        fun isApplicable(file: VirtualFile, project: Project): Boolean {
            val support = ScriptingSupport.EP_NAME.findExtension(GradleScriptingSupport::class.java, project) ?: return false
            return support.isApplicable(file)
        }
    }
}