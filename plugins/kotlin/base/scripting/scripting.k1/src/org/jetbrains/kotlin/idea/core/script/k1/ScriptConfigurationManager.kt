// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1

import com.intellij.codeInsight.daemon.SyntheticPsiFileSupport
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.base.util.caching.findSdkBridge
import org.jetbrains.kotlin.idea.base.util.caching.getChanges
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.script.k1.configuration.DefaultScriptingSupport
import org.jetbrains.kotlin.idea.core.script.k1.configuration.ScriptingSupport
import org.jetbrains.kotlin.idea.core.script.k1.configuration.listener.ScriptChangesNotifier
import org.jetbrains.kotlin.idea.core.script.k1.ucache.ScriptClassRootsBuilder
import org.jetbrains.kotlin.idea.core.script.k1.ucache.ScriptClassRootsCache
import org.jetbrains.kotlin.idea.core.script.shared.LightScriptInfo
import org.jetbrains.kotlin.idea.core.script.shared.getScriptReports
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependencyAware
import org.jetbrains.kotlin.idea.core.script.v1.alwaysVirtualFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import java.nio.file.Path
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.makeFailureResult

class ScriptConfigurationManager(val myProject: Project, val scope: CoroutineScope) : ScriptConfigurationsProvider(myProject), ScriptDependencyAware {
    private val notifier = ScriptChangesNotifier(project)

    private val classpathRoots: ScriptClassRootsCache
        get() = updater.classpathRoots

    private val plugins
        get() = ScriptingSupport.EP_NAME.getPoint(project).extensionList

    val default: DefaultScriptingSupport = DefaultScriptingSupport(this)

    val updater: ScriptClassRootsUpdater = object : ScriptClassRootsUpdater(project, scope) {
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

    override fun getScriptConfigurationResult(file: KtFile): ScriptCompilationConfigurationResult? {
        val configuration = getScriptConfiguration(file)
        val reports = getScriptReports(file)
        if (configuration == null && reports.isNotEmpty()) {
            return makeFailureResult(reports)
        }
        return configuration?.asSuccess(reports)
    }

    override fun getScriptConfiguration(file: KtFile): ScriptCompilationConfigurationWrapper? {
        // return only already loaded configurations OR force to load gradle-related configurations
        return if (DefaultScriptingSupport.getInstance(project).isLoadedFromCache(file) || !ScratchUtil.isScratch(file.virtualFile)) {
            getConfiguration(file)
        } else {
            null
        }
    }

    fun updateScriptDependenciesIfNeeded(file: VirtualFile) {
        notifier.updateScriptDependenciesIfNeeded(file)
    }

    fun getConfiguration(file: KtFile): ScriptCompilationConfigurationWrapper? {
        val virtualFile = file.alwaysVirtualFile
        val scriptConfiguration = classpathRoots.getScriptConfiguration(virtualFile)
        if (scriptConfiguration != null) return scriptConfiguration

        // check that this script should be loaded later in special way (e.g. gradle project import)
        // (and not for syntactic diff files)
        if (!SyntheticPsiFileSupport.isOutsiderFile(virtualFile)) {
            val plugin = plugins.firstOrNull { it.isApplicable(virtualFile) }
            if (plugin != null) {
                return plugin.getConfigurationImmediately(virtualFile)?.also {
                    updater.addConfiguration(virtualFile, it)
                }
            }
        }

        return default.getOrLoadConfiguration(virtualFile, file)
    }

    @TestOnly
    fun hasConfiguration(file: KtFile): Boolean =
        file.alwaysVirtualFile.path in classpathRoots.scripts

    fun isConfigurationLoadingInProgress(file: KtFile): Boolean =
        plugins.firstOrNull { it.isApplicable(file.alwaysVirtualFile) }?.isConfigurationLoadingInProgress(file)
            ?: (default.getCachedConfigurationState(file.originalFile.virtualFile)?.applied == null)

    fun getLightScriptInfo(file: String): LightScriptInfo? =
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

    override fun getScriptSdk(virtualFile: VirtualFile): Sdk? =
        if (ScratchUtil.isScratch(virtualFile)) {
            ProjectRootManager.getInstance(project).projectSdk
        } else {
            classpathRoots.getScriptSdk(virtualFile)
        }

    override fun getScriptDependenciesClassFilesScope(file: VirtualFile): GlobalSearchScope =
        classpathRoots.getScriptDependenciesClassFilesScope(file)

    override fun getAllScriptsDependenciesClassFilesScope(): GlobalSearchScope =
        classpathRoots.allDependenciesClassFilesScope

    override fun getAllScriptDependenciesSourcesScope(): GlobalSearchScope =
        classpathRoots.allDependenciesSourcesScope

    override fun getAllScriptsDependenciesClassFiles(): Collection<VirtualFile> =
        classpathRoots.allDependenciesClassFiles

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

    fun getScriptDependenciesSourceFiles(file: VirtualFile): Collection<VirtualFile> =
        classpathRoots.getScriptDependenciesSourceFiles(file)

    companion object {
        @JvmStatic
        fun getInstance(project: Project): ScriptConfigurationManager = project.service<ScriptConfigurationsProvider>() as ScriptConfigurationManager

        @JvmStatic
        fun getInstanceSafe(project: Project): ScriptConfigurationManager? = project.serviceOrNull<ScriptConfigurationsProvider>() as? ScriptConfigurationManager

        @Suppress("TestOnlyProblems")
        @TestOnly
        fun updateScriptDependenciesSynchronously(file: PsiFile) {
            val defaultScriptingSupport = getInstanceSafe(file.project)?.default ?: return
            when (file) {
                is KtFile -> {
                    defaultScriptingSupport.updateScriptDependenciesSynchronously(file)
                }

                else -> {
                    val project = file.project
                    val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
                    object : PsiRecursiveElementVisitor() {
                        override fun visitElement(element: PsiElement) {
                            injectedLanguageManager.enumerate(element) { psi, _ ->
                                defaultScriptingSupport.updateScriptDependenciesSynchronously(psi)
                            }
                            super.visitElement(element)
                        }
                    }.visitFile(file)
                }
            }
        }

        @TestOnly
        fun clearCaches(project: Project) {
            getInstanceSafe(project)?.default?.updateScriptDefinitionsReferences()
        }
    }
}