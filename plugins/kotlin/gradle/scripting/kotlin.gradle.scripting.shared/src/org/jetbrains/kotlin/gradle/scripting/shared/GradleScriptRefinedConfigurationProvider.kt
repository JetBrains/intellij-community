// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.core.script.*
import org.jetbrains.kotlin.idea.core.script.dependencies.indexSourceRootsEagerly
import org.jetbrains.kotlin.idea.core.script.k2.*
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.adjustByDefinition
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

@Service(Service.Level.PROJECT)
class GradleScriptRefinedConfigurationProvider(
    val project: Project, val coroutineScope: CoroutineScope
) : ScriptRefinedConfigurationResolver, ScriptWorkspaceModelManager {
    private val data = AtomicReference<Map<VirtualFile, ScriptConfigurationWithSdk>>(emptyMap())

    override suspend fun create(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptConfigurationWithSdk? = null

    override fun get(virtualFile: VirtualFile): ScriptConfigurationWithSdk? = data.get()[virtualFile]

    suspend fun updateConfigurations(scripts: Iterable<GradleScriptModel>) {
        val configurations = scripts.associate { it: GradleScriptModel ->
            val sourceCode = VirtualFileScriptSource(it.virtualFile)
            val definition = findScriptDefinition(project, sourceCode)

            val sdk = project.serviceAsync<ProjectRootManager>().projectSdk?.takeIf { it.sdkType is JavaSdkType }
                ?: it.javaHome?.let { ExternalSystemJdkUtil.lookupJdkByPath(it) }

            val javaHomePath = sdk?.homePath?.let { Path.of(it) }

            val configuration = definition.compilationConfiguration.with {
                javaHomePath?.let {
                    jvm.jdkHome(it.toFile())
                }
                defaultImports(it.imports)
                dependencies(JvmDependency(it.classPath.map { File(it) }))
                ide.dependenciesSources(JvmDependency(it.sourcePath.map { File(it) }))
            }.adjustByDefinition(definition)

            val updatedConfiguration = smartReadAction(project) {
                refineScriptCompilationConfiguration(sourceCode, definition, project, configuration)
            }

            it.virtualFile to ScriptConfigurationWithSdk(updatedConfiguration, sdk)
        }

        data.set(configurations)
    }

    override suspend fun updateWorkspaceModel(configurationPerFile: Map<VirtualFile, ScriptConfigurationWithSdk>) {
        project.workspaceModel.update("updating .gradle.kts scripts") { storage ->
            val storageWithGradleScriptModules = getUpdatedStorage(configurationPerFile)

            storage.replaceBySource({ it is KotlinGradleScriptModuleEntitySource }, storageWithGradleScriptModules)
        }
    }

    private fun getUpdatedStorage(
        configurations: Map<VirtualFile, ScriptConfigurationWithSdk>,
    ): MutableEntityStorage {
        val result = MutableEntityStorage.create()

        val urlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
        val virtualFileCache = ScriptVirtualFileCache()
        val dependencyFactory = ScriptDependencyFactory(result, configurations, virtualFileCache)

        for ((scriptFile, configurationWithSdk) in configurations) {
            val configuration = configurationWithSdk.scriptConfiguration.valueOrNull() ?: continue
            val source = KotlinGradleScriptModuleEntitySource(scriptFile.toVirtualFileUrl(urlManager))

            val definition = findScriptDefinition(project, VirtualFileScriptSource(scriptFile))
            val definitionName = definition.name
            val externalProjectPath = definition.compilationConfiguration[ScriptCompilationConfiguration.gradle.externalProjectPath]
            val scriptRelativeLocation = project.scriptModuleRelativeLocation(scriptFile)

            val sdkDependency = configurationWithSdk.sdk?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

            val classes = configuration.dependenciesClassPath.mapNotNull { virtualFileCache.findVirtualFile(it.path) }.toMutableSet()

            val allDependencies = listOfNotNull(sdkDependency) + buildList {
                val sources = configuration.dependenciesSources.mapNotNull { virtualFileCache.findVirtualFile(it.path) }.toMutableSet()
                add(
                    result.groupRootsByPredicate(classes, sources, source, "kotlin-stdlib dependencies") {
                        it.name.contains("kotlin-stdlib")
                    }
                )

                if (indexSourceRootsEagerly()) {
                    addAll(result.createDependenciesWithSources(classes, sources, source))
                    add(
                        result.groupRootsByPredicate(
                            classes, sources, source, "$scriptRelativeLocation accessors dependencies"
                        ) {
                            it.path.contains("accessors")
                        })
                    add(
                        result.groupRootsByPredicate(
                            classes, sources, source, "$scriptRelativeLocation groovy dependencies"
                        ) {
                            it.path.contains("groovy")
                        })
                }

                addAll(classes.map {
                    dependencyFactory.get(it, source)
                })
            }.distinct().sortedBy { it.library.name }


            val moduleName = "$KOTLIN_SCRIPTS_MODULE_NAME.$definitionName.$scriptRelativeLocation"
            result addEntity ModuleEntity(moduleName, allDependencies, source) {
                this.exModuleOptions = ExternalSystemModuleOptionsEntity(source) {
                    this.externalSystem = GradleConstants.SYSTEM_ID.id
                    this.module = this@ModuleEntity
                    this.rootProjectPath = externalProjectPath
                    this.linkedProjectId = moduleName
                }
            }
        }

        return result
    }

    private fun MutableEntityStorage.groupRootsByPredicate(
        classes: MutableSet<VirtualFile>,
        sources: MutableSet<VirtualFile>,
        source: KotlinScriptEntitySource,
        dependencyName: String,
        predicate: Predicate<VirtualFile>
    ): LibraryDependency {
        val groupedClasses = classes.removeOnMatch(predicate)
        val groupedSources = sources.removeOnMatch(predicate)

        val classRoots = groupedClasses.map { it.compiledLibraryRoot(project) }.sortedWith(ROOT_COMPARATOR)
        val sourceRoots = groupedSources.map { it.sourceLibraryRoot(project) }.sortedWith(ROOT_COMPARATOR)

        return createOrUpdateLibrary(dependencyName, classRoots + sourceRoots, source)
    }

    private fun <T> MutableCollection<T>.removeOnMatch(predicate: Predicate<T>): MutableList<T> {
        val removed = mutableListOf<T>()
        val iterator = iterator()
        while (iterator.hasNext()) {
            val element = iterator.next()
            if (predicate.test(element)) {
                removed.add(element)
                iterator.remove()
            }
        }
        return removed
    }

    inner class ScriptDependencyFactory(
        private val entityStorage: MutableEntityStorage,
        scripts: Map<VirtualFile, ScriptConfigurationWithSdk>,
        virtualFileCache: ScriptVirtualFileCache,
    ) {
        private val nameCache = HashMap<String, Set<VirtualFile>>()

        init {
            val classes = scripts.mapNotNull { it.value.scriptConfiguration.valueOrNull() }.flatMap {
                it.dependenciesClassPath
            }.toSet()

            classes.mapNotNull { virtualFileCache.findVirtualFile(it.path) }.forEach {
                nameCache.compute(it.name) { _, list ->
                    (list ?: emptySet()) + it
                }
            }
        }

        fun get(file: VirtualFile, source: KotlinScriptEntitySource): LibraryDependency {
            val filesWithSameName = nameCache[file.name]
            val root = listOf(file.compiledLibraryRoot(project))

            return if (filesWithSameName == null || filesWithSameName.size == 1) {
                entityStorage.getOrCreateLibrary(file.name, root, source)
            } else {
                val commonAncestor = findCommonAncestor(filesWithSameName.map { it.path })
                if (commonAncestor == null) {
                    entityStorage.getOrCreateLibrary(file.name, root, source)
                } else {
                    val libraryName = file.path.replace("$commonAncestor/", "")
                    entityStorage.getOrCreateLibrary(libraryName, root, source)
                }
            }
        }

        private fun findCommonAncestor(paths: Collection<String>): String? {
            if (paths.isEmpty()) return null

            val splitPaths = paths.map { it.split("/") }
            val shortestSize = splitPaths.minOf { it.size }

            val commonParts = mutableListOf<String>()
            for (i in 0 until shortestSize) {
                val segment = splitPaths[0][i]
                if (splitPaths.all { it[i] == segment }) {
                    commonParts.add(segment)
                } else {
                    break
                }
            }

            val commonPath = commonParts.joinToString("/")
            if (commonPath.isBlank()) return null

            return commonPath
        }
    }

    private fun MutableEntityStorage.createDependenciesWithSources(
        classes: MutableSet<VirtualFile>, sources: MutableSet<VirtualFile>, source: KotlinScriptEntitySource
    ): List<LibraryDependency> {
        val result: MutableList<LibraryDependency> = mutableListOf()
        val sourcesNames = sources.associateBy { it.name }

        val jar = ".jar"
        val pairs = classes.filter { it.name.contains(jar) }.associateWith {
            sourcesNames[it.name] ?: sourcesNames[it.name.replace(jar, "-sources.jar")]
        }.filterValues { it != null }

        for ((left, right) in pairs) {
            if (right != null) {
                val roots = listOf(left.compiledLibraryRoot(project), right.sourceLibraryRoot(project))
                val dependency = createOrUpdateLibrary(left.name, roots, source)

                classes.remove(left)
                sources.remove(right)
                result.add(dependency)
            }
        }

        return result
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): GradleScriptRefinedConfigurationProvider = project.service()
    }
}