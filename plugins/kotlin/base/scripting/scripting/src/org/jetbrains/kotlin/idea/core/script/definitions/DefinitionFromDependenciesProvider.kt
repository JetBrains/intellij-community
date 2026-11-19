// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definitions

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.core.script.definition.loadDefinitionsFromTemplates
import org.jetbrains.kotlin.idea.core.script.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.scriptingWarnLog
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.core.script.settings.parsedClassNames
import org.jetbrains.kotlin.idea.core.script.settings.parsedClasspath
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_PATH
import org.jetbrains.kotlin.utils.addIfNotNull
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

data class DefinitionTemplates(
    val fqns: List<String>,
    val classpath: List<String>,
    /** Maps a discovered definition FQN to the marker file that declared it. */
    val markerOrigins: Map<String, ScriptTemplateMarkerOrigin> = emptyMap(),
)

/**
 * Where a marker file declared a definition.
 *
 * [rootName] names the JAR or the class directory, and [jarPath] is its whole path.
 */
data class ScriptTemplateMarkerOrigin(
    val rootName: String? = null,
    val jarPath: String? = null,
)

private const val MAIN_KTS = "org.jetbrains.kotlin.mainKts.MainKtsScript.classname"

class DefinitionFromDependenciesProvider(val project: Project) : ScriptDefinitionsProvider {
    override val id: String = KotlinScriptDefinitionsProviderId.FROM_DEPENDENCIES.id

    /** The templates that the last load found. */
    @Volatile
    var templates: DefinitionTemplates = DefinitionTemplates(emptyList(), emptyList())
        private set

    override fun provideDefinitions(
        baseHostConfiguration: ScriptingHostConfiguration,
        loadedScriptDefinitions: List<ScriptDefinition>,
    ): List<ScriptDefinition> {
        templates = ScriptTemplateDiscovery.discover(project)
        val fqns = definitionClasses()
        if (fqns.isEmpty()) return emptyList()
        return loadDefinitionsFromTemplates(fqns, templateClasspath(), baseHostConfiguration = baseHostConfiguration)
            .map { ScriptDefinition(it.compilationConfiguration, it.evaluationConfiguration ?: ScriptEvaluationConfiguration.Default) }
            .toList()
    }

    /** Where the marker file of [definitionId] lives, or null when no marker declared it. */
    internal fun markerOriginFor(definitionId: String): ScriptTemplateMarkerOrigin? = templates.markerOrigins[definitionId]

    fun definitionClasses(): List<String> {
        val explicitFqns = KotlinScriptingSettings.getInstance(project).state.parsedClassNames
        return (explicitFqns + templates.fqns).distinct()
    }

    fun templateClasspath(): List<Path> {
        val settings = KotlinScriptingSettings.getInstance(project).state
        val explicitClasspath = settings.parsedClasspath
        val discoveredClasspath = templates.classpath
        val autoResolvedClasspath = tryToGuessClasspath(settings.parsedClassNames)
        return buildList {
            addAll(explicitClasspath)
            addAll(discoveredClasspath)
            addAll(autoResolvedClasspath)
        }.distinct().mapNotNull { entry ->
            val path = entry.toNioPathOrNull()
            if (path == null) scriptingWarnLog("Invalid script definition classpath entry is skipped: $entry")
            path
        }
    }

    private fun tryToGuessClasspath(fqns: List<String>): List<String> {
        if (fqns.isEmpty() || project.isDefault) return emptyList()
        return runReadActionBlocking {
            val dumbService = DumbService.getInstance(project)
            if (dumbService.isDumb) {
                dumbService.runWhenSmart { ScriptDefinitionProviderImpl.getInstance(project).scheduleLoading() }
                return@runReadActionBlocking emptyList()
            }
            val javaFacade = JavaPsiFacade.getInstance(project)
            val projectScope = GlobalSearchScope.projectScope(project)
            val projectFileIndex = ProjectFileIndex.getInstance(project)
            val classpath = linkedSetOf<Path>()
            for (fqn in fqns) {
                val psiClass = javaFacade.findClass(fqn, projectScope) ?: continue
                val classFile = psiClass.containingFile?.virtualFile ?: continue
                val module = projectFileIndex.getModuleForFile(classFile) ?: continue
                for (virtualFile in OrderEnumerator.orderEntries(module).withoutSdk().productionOnly().classes().usingCache().roots) {
                    val localVirtualFile = VfsUtil.getLocalFile(virtualFile)
                    classpath.addIfNotNull(localVirtualFile.fileSystem.getNioPath(localVirtualFile))
                }
            }
            classpath.map { it.absolutePathString() }
        }
    }

    /**
     * Reloads the definitions when the class roots now declare other templates than the last load found.
     * It does nothing before the first lookup of a definition.
     */
    fun refresh() {
        val provider = project.serviceIfCreated<ScriptDefinitionProvider>() as? ScriptDefinitionProviderImpl ?: return
        ReadAction.nonBlocking<Unit> {
            if (ScriptTemplateDiscovery.discover(project) != templates) {
                provider.scheduleLoading()
            }
        }
            .coalesceBy(this)
            .expireWhen { project.isDisposed }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    companion object {
        fun getInstance(project: Project): DefinitionFromDependenciesProvider =
            ScriptDefinitionsProvider.EP_NAME.getExtensionList(project).firstNotNullOf { it as? DefinitionFromDependenciesProvider }
    }
}

private object ScriptTemplateDiscovery {
    fun discover(project: Project): DefinitionTemplates =
        ReadAction.nonBlocking<DefinitionTemplates> { collectDefinitionTemplates(project) }.executeSynchronously()

    private fun collectDefinitionTemplates(project: Project): DefinitionTemplates {
        val templates = linkedSetOf<String>()
        val markerOrigins = linkedMapOf<String, ScriptTemplateMarkerOrigin>()
        val ownerModules = linkedSetOf<Module>()
        val markerRoots = linkedSetOf<VirtualFile>()
        for (classRoot in markerCandidateRoots(project)) {
            ProgressManager.checkCanceled()
            val markerFiles = classRoot.findFileByRelativePath(SCRIPT_DEFINITION_MARKERS_PATH)?.children.orEmpty()
                .filter { it.name.endsWith(SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT) && it.name != MAIN_KTS }
                .sortedBy { it.name }
            if (markerFiles.isEmpty()) continue

            scriptingDebugLog { "Root matching SCRIPT_DEFINITION_MARKERS_PATH found: ${classRoot.path}" }
            ownerModules += ownerModules(project, classRoot)
            markerRoots += classRoot
            for (markerFile in markerFiles) {
                val fqn = markerFile.name.removeSuffix(SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT)
                templates += fqn
                markerOrigins.putIfAbsent(fqn, ScriptTemplateMarkerOrigin(classRoot.name, classRoot.presentableUrl))
            }
        }

        val classpath = ownerModules
            .flatMapTo(markerRoots) { OrderEnumerator.orderEntries(it).withoutSdk().productionOnly().classes().usingCache().roots.asIterable() }
            .mapNotNull { virtualFile ->
                val localVirtualFile = VfsUtil.getLocalFile(virtualFile)
                localVirtualFile.fileSystem.getNioPath(localVirtualFile)?.absolutePathString()
            }
        return DefinitionTemplates(templates.toList(), classpath, markerOrigins)
    }

    /** The production outputs of all modules, and the class roots of all libraries in any scope. */
    private fun markerCandidateRoots(project: Project): Set<VirtualFile> {
        val outputRoots = OrderEnumerator.orderEntries(project).productionOutputRoots()
        val libraryRoots = OrderEnumerator.orderEntries(project).withoutSdk().withoutModuleSourceEntries().classes().usingCache().roots
        return linkedSetOf(*outputRoots, *libraryRoots)
    }

    private fun ownerModules(project: Project, classRoot: VirtualFile): List<Module> {
        val libraryOwners = ProjectFileIndex.getInstance(project).getOrderEntriesForFile(classRoot)
            .filter { it is LibraryOrSdkOrderEntry && classRoot in it.getRootFiles(OrderRootType.CLASSES) }
            .map { it.ownerModule }
        if (libraryOwners.isNotEmpty()) return libraryOwners
        return ModuleManager.getInstance(project).modules.filter {
            classRoot in OrderEnumerator.orderEntries(it).productionOutputRoots()
        }
    }

    private fun OrderEnumerator.productionOutputRoots(): Array<VirtualFile> =
        withoutSdk().withoutLibraries().withoutDepModules().productionOnly().classes().usingCache().roots
}
