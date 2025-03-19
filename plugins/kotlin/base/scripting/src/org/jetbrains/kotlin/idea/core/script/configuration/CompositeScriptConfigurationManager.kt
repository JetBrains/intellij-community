// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.configuration

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.util.caching.findSdkBridge
import org.jetbrains.kotlin.idea.base.util.caching.getChanges
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.configuration.listener.ScriptChangesNotifier
import org.jetbrains.kotlin.idea.core.script.configuration.listener.ScriptChangesNotifierK1
import org.jetbrains.kotlin.idea.core.script.configuration.listener.ScriptChangesNotifierK2
import org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsBuilder
import org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsCache
import org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsUpdater
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import java.nio.file.Path

/**
 * The [CompositeScriptConfigurationManager] will provide redirection of [ScriptConfigurationManager] calls to the
 * custom [ScriptingSupport] or [DefaultScriptingSupport] if that script lack of custom [ScriptingSupport].
 *
 * The [ScriptConfigurationManager] is implemented by caching all scripts using the [ScriptClassRootsCache].
 * The [ScriptClassRootsCache] is always available and never blacked by the writer, as all writes occurs
 * using the copy-on-write strategy.
 *
 * This cache is loaded on start and will be updating asynchronously using the [updater].
 * Sync updates still may be occurred from the [DefaultScriptingSupport].
 *
 * We are also watching all script documents:
 * [notifier] will call first applicable [ScriptChangesNotifier.listeners] when editor is activated or document changed.
 * Listener should do something to invalidate configuration and schedule reloading.
 */
class CompositeScriptConfigurationManager(val project: Project, val scope: CoroutineScope) : ScriptConfigurationManager {
    private val notifier = if (KotlinPluginModeProvider.isK2Mode()) ScriptChangesNotifierK2() else ScriptChangesNotifierK1(project)
    private val classpathRoots: ScriptClassRootsCache
        get() = updater.classpathRoots

    private val plugins
        get() = ScriptingSupport.EPN.getPoint(project).extensionList

    val default = DefaultScriptingSupport(this)

    val updater = object : ScriptClassRootsUpdater(project, this, scope) {
        override fun gatherRoots(builder: ScriptClassRootsBuilder) {
            default.collectConfigurations(builder)
            plugins.forEach { it.collectConfigurations(builder) }
        }

        override fun afterUpdate() {
            plugins.forEach { it.afterUpdate() }
        }

        override fun onTrivialUpdate() {
            plugins.forEach { it.onTrivialUpdate() }
        }

        override fun onUpdateException(exception: Exception) {
            plugins.forEach { it.onUpdateException(exception) }
        }
    }

    fun updateScriptDependenciesIfNeeded(file: VirtualFile) {
        notifier.updateScriptDependenciesIfNeeded(file)
    }

    fun tryGetScriptDefinitionFast(locationId: String): ScriptDefinition? {
        return classpathRoots.getLightScriptInfo(locationId)?.definition
    }

    private fun getOrLoadConfiguration(
        virtualFile: VirtualFile,
        preloadedKtFile: KtFile? = null
    ): ScriptCompilationConfigurationWrapper? {
        val scriptConfiguration = classpathRoots.getScriptConfiguration(virtualFile)
        if (scriptConfiguration != null) return scriptConfiguration

        // check that this script should be loaded later in special way (e.g. gradle project import)
        // (and not for syntactic diff files)
        if (!OutsidersPsiFileSupport.isOutsiderFile(virtualFile)) {
            val plugin = plugins.firstOrNull { it.isApplicable(virtualFile) }
            if (plugin != null) {
                return plugin.getConfigurationImmediately(virtualFile)?.also {
                    updater.addConfiguration(virtualFile, it)
                }
            }
        }

        return default.getOrLoadConfiguration(virtualFile, preloadedKtFile)
    }

    private val KtFile.alwaysVirtualFile: VirtualFile get() = originalFile.virtualFile ?: viewProvider.virtualFile

    override fun getConfiguration(file: KtFile) =
        getOrLoadConfiguration(file.alwaysVirtualFile, file)

    override fun hasConfiguration(file: KtFile): Boolean =
        classpathRoots.contains(file.alwaysVirtualFile)

    override fun isConfigurationLoadingInProgress(file: KtFile): Boolean =
        plugins.firstOrNull { it.isApplicable(file.alwaysVirtualFile) }?.isConfigurationLoadingInProgress(file)
            ?: default.isConfigurationLoadingInProgress(file)

    fun getLightScriptInfo(file: String): ScriptClassRootsCache.LightScriptInfo? =
        updater.classpathRoots.getLightScriptInfo(file)

    override fun updateScriptDefinitionReferences() {
        ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()

        default.updateScriptDefinitionsReferences()

        if (classpathRoots.customDefinitionsUsed) {
            updater.invalidateAndCommit()
        }
    }

    init {
        val connection = project.messageBus.connect(KotlinPluginDisposable.getInstance(project))
        connection.subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
            override fun beforeChanged(event: VersionedStorageChange) {
                val storageBefore = event.storageBefore
                val storageAfter = event.storageAfter
                val changes = event.getChanges<SdkEntity>().ifEmpty { return }

                changes.asSequence()
                    .mapNotNull(EntityChange<SdkEntity>::newEntity)
                    .mapNotNull { it.findSdkBridge(storageAfter) }
                    .firstOrNull()?.let {
                        updater.checkInvalidSdks()
                        return
                    }

                val outdated: List<Sdk> = changes.asSequence()
                    .mapNotNull(EntityChange<SdkEntity>::oldEntity)
                    .mapNotNull { it.findSdkBridge(storageBefore) }
                    .toList()

                if (outdated.isNotEmpty()) {
                    updater.checkInvalidSdks(*outdated.toTypedArray())
                }
            }
        })
    }

    override fun getScriptSdk(file: VirtualFile): Sdk? =
        if (ScratchUtil.isScratch(file)) {
            ProjectRootManager.getInstance(project).projectSdk
        } else {
            classpathRoots.getScriptSdk(file)
        }

    override fun getFirstScriptsSdk(): Sdk? =
        classpathRoots.firstScriptSdk

    override fun getScriptDependenciesClassFilesScope(file: VirtualFile): GlobalSearchScope =
        classpathRoots.getScriptDependenciesClassFilesScope(file)

    override fun getAllScriptsDependenciesClassFilesScope(): GlobalSearchScope =
        classpathRoots.allDependenciesClassFilesScope

    override fun getAllScriptDependenciesSourcesScope(): GlobalSearchScope =
        classpathRoots.allDependenciesSourcesScope

    override fun getAllScriptsDependenciesClassFiles(): Collection<VirtualFile> =
        classpathRoots.allDependenciesClassFiles

    override fun getAllScriptDependenciesSources(): Collection<VirtualFile> =
        classpathRoots.allDependenciesSources

    override fun getAllScriptsSdkDependenciesClassFiles(): Collection<VirtualFile> =
        classpathRoots.sdks.nonIndexedClassRoots

    override fun getAllScriptSdkDependenciesSources(): Collection<VirtualFile> =
        classpathRoots.sdks.nonIndexedSourceRoots

    override fun getScriptDependingOn(dependencies: Collection<String>): VirtualFile? =
        classpathRoots.scriptsPaths().firstNotNullOfOrNull { scriptPath ->
            VfsUtil.findFile(Path.of(scriptPath), true)?.takeIf { scriptVirtualFile ->
                getScriptDependenciesClassFiles(scriptVirtualFile).any { scriptDependency ->
                    dependencies.contains(scriptDependency.presentableUrl)
                }
            }
        }

    override fun getScriptDependenciesClassFiles(file: VirtualFile): Collection<VirtualFile> =
        classpathRoots.getScriptDependenciesClassFiles(file)

    override fun getScriptDependenciesSourceFiles(file: VirtualFile): Collection<VirtualFile> =
        classpathRoots.getScriptDependenciesSourceFiles(file)
}
