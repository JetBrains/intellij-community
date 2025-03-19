// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.kotlin.idea.core.script.KOTLIN_SCRIPTS_MODULE_NAME
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.toVfsRoots
import org.jetbrains.kotlin.idea.core.script.dependencies.indexSourceRootsEagerly
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationsSource
import org.jetbrains.kotlin.idea.core.script.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.idea.core.script.ucache.relativeName
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.adjustByDefinition
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

internal class GradleScriptModel(
    override val virtualFile: VirtualFile,
    val classPath: List<String> = listOf(),
    val sourcePath: List<String> = listOf(),
    val imports: List<String> = listOf(),
    val javaHome: String? = null
) : BaseScriptModel(virtualFile) {
    override fun toString(): String {
        return "GradleScriptModel(virtualFile=$virtualFile)"
    }
}

internal open class GradleScriptConfigurationsSource(override val project: Project) :
    ScriptConfigurationsSource<GradleScriptModel>(project) {
    private val gradleEntitySourceFilter: (EntitySource) -> Boolean =
        { entitySource -> entitySource is KotlinGradleScriptModuleEntitySource }

    override suspend fun updateModules(storage: MutableEntityStorage?) {
        if (storage == null) return

        val storageWithGradleScriptModules = getUpdatedStorage()

        storage.replaceBySource(gradleEntitySourceFilter, storageWithGradleScriptModules)
    }

    override fun getDefinitions(): Sequence<ScriptDefinition>? =
        project.scriptDefinitionsSourceOfType<GradleScriptDefinitionsSource>()?.definitions

    override suspend fun updateConfigurations(scripts: Iterable<GradleScriptModel>) {
        val configurations = scripts.associate { it: GradleScriptModel ->
            val sourceCode = VirtualFileScriptSource(it.virtualFile)
            val definition = findScriptDefinition(project, sourceCode)

            val sdk = ProjectRootManager.getInstance(project).projectSdk?.takeIf { it.sdkType is JavaSdkType }
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

    private fun getUpdatedStorage(): MutableEntityStorage {
        val updatedStorage = MutableEntityStorage.create()

        val fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
        val data = data.get()
        val dependencyFactory = GradleLibraryDependencyFactory(fileUrlManager, updatedStorage, data)

        for ((scriptFile, configurationWithSdk) in data) {
            val configuration = configurationWithSdk.scriptConfiguration.valueOrNull() ?: continue
            val source = KotlinGradleScriptModuleEntitySource(scriptFile.toVirtualFileUrl(fileUrlManager))

            val definitionName = findScriptDefinition(project, VirtualFileScriptSource(scriptFile)).name

            val definitionScriptModuleName = "$KOTLIN_SCRIPTS_MODULE_NAME.$definitionName"
            val locationName = scriptFile.relativeName(project).replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')

            val sdkDependency = configurationWithSdk.sdk?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

            val classes = toVfsRoots(configuration.dependenciesClassPath).toMutableSet()

            val allDependencies = listOfNotNull(sdkDependency) + buildList {
                val sources = toVfsRoots(configuration.dependenciesSources).toMutableSet()
                add(
                    updatedStorage.groupClassesSourcesByPredicate(
                        classes,
                        sources,
                        source,
                        fileUrlManager,
                        "$locationName kotlin-stdlib dependencies"
                    ) {
                        it.name.contains("kotlin-stdlib")
                    })

                if (indexSourceRootsEagerly()) {
                    addAll(updatedStorage.createDependenciesWithSources(classes, sources, source, fileUrlManager))
                    add(
                        updatedStorage.groupClassesSourcesByPredicate(
                            classes,
                            sources,
                            source,
                            fileUrlManager,
                            "$locationName accessors dependencies"
                        ) {
                            it.path.contains("accessors")
                        })
                    add(
                        updatedStorage.groupClassesSourcesByPredicate(
                            classes,
                            sources,
                            source,
                            fileUrlManager,
                            "$locationName groovy dependencies"
                        ) {
                            it.path.contains("groovy")
                        })
                }

                addAll(classes.mapNotNull { dependencyFactory.get(it, source) })
            }.sortedBy { it.library.name }

            updatedStorage.addEntity(ModuleEntity("$definitionScriptModuleName.$locationName", allDependencies, source))
        }

        return updatedStorage
    }

    private fun MutableEntityStorage.createDependenciesWithSources(
        classes: MutableSet<VirtualFile>, sources: MutableSet<VirtualFile>, source: KotlinScriptEntitySource, manager: VirtualFileUrlManager
    ): List<LibraryDependency> {

        val dependencies: MutableList<LibraryDependency> = mutableListOf()
        val sourcesNames = sources.associateBy { it.name }

        val jar = ".jar"
        val pairs = classes.filter { it.name.contains(jar) }.associateWith {
            sourcesNames[it.name] ?: sourcesNames[it.name.replace(jar, "-sources.jar")]
        }.filterValues { it != null }

        for ((left, right) in pairs) {
            if (right != null) {
                val libraryId = LibraryId("Scripts: ${left.name}", LibraryTableId.ProjectLibraryTableId)
                val dependency = if (contains(libraryId)) {
                    LibraryDependency(libraryId, false, DependencyScope.COMPILE)
                } else {
                    val classRoot = LibraryRoot(left.toVirtualFileUrl(manager), LibraryRootTypeId.COMPILED)
                    val sourceRoot = LibraryRoot(right.toVirtualFileUrl(manager), LibraryRootTypeId.SOURCES)

                    val dependencyLibrary = addEntity(
                        LibraryEntity(
                            libraryId.name, libraryId.tableId, listOf(classRoot, sourceRoot), source
                        )
                    )

                    LibraryDependency(dependencyLibrary.symbolicId, false, DependencyScope.COMPILE)
                }

                classes.remove(left)
                sources.remove(right)
                dependencies.add(dependency)
            }
        }

        return dependencies
    }

    private fun MutableEntityStorage.groupClassesSourcesByPredicate(
        classes: MutableSet<VirtualFile>,
        sources: MutableSet<VirtualFile>,
        source: KotlinScriptEntitySource,
        manager: VirtualFileUrlManager,
        dependencyName: String,
        predicate: Predicate<VirtualFile>
    ): LibraryDependency {

        val groupedClasses = classes.removeOnMatch(predicate)
        val groupedSources = sources.removeOnMatch(predicate)

        val classRoots = groupedClasses.sortedBy { it.name }.map {
            LibraryRoot(it.toVirtualFileUrl(manager), LibraryRootTypeId.COMPILED)
        }

        val sourceRoots = groupedSources.sortedBy { it.name }.map {
            LibraryRoot(it.toVirtualFileUrl(manager), LibraryRootTypeId.SOURCES)
        }

        val dependencyLibrary = addEntity(
            LibraryEntity(
                dependencyName, LibraryTableId.ProjectLibraryTableId, classRoots + sourceRoots, source
            )
        )

        return LibraryDependency(dependencyLibrary.symbolicId, false, DependencyScope.COMPILE)
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

    data class KotlinGradleScriptModuleEntitySource(override val virtualFileUrl: VirtualFileUrl) : KotlinScriptEntitySource(virtualFileUrl)
}

class GradleLibraryDependencyFactory(
    private val fileUrlManager: VirtualFileUrlManager,
    private val entityStorage: MutableEntityStorage,
    scripts: Map<VirtualFile, ScriptConfigurationWithSdk>,
) {
    private val nameCache = HashMap<String, Set<VirtualFile>>()

    init {
        var classes = scripts.mapNotNull { it.value.scriptConfiguration.valueOrNull() }.flatMap {
            it.dependenciesClassPath
        }.toSet()

        toVfsRoots(classes).forEach {
            nameCache.compute(it.name) { _, list ->
                (list ?: emptySet()) + it
            }
        }
    }

    fun get(file: VirtualFile, source: KotlinScriptEntitySource): LibraryDependency? {
        val filesWithSameName = nameCache[file.name] ?: return null
        return if (filesWithSameName.size == 1) {
            getOrCreateLibrary(file = file, source = source)
        } else {
            val commonAncestor = findCommonAncestor(filesWithSameName.map { it.path })
            if (commonAncestor == null) {
                getOrCreateLibrary(file = file, source = source)
            } else {
                val libraryName = file.path.replace("$commonAncestor/", "")
                getOrCreateLibrary(file = file, libraryName = libraryName, source = source)
            }
        }
    }

    private fun getOrCreateLibrary(
        file: VirtualFile,
        libraryName: String = file.name,
        source: KotlinScriptEntitySource
    ): LibraryDependency {
        val libraryId = LibraryId("Scripts: $libraryName", LibraryTableId.ProjectLibraryTableId)
        if (!entityStorage.contains(libraryId)) {
            val fileUrl = file.toVirtualFileUrl(fileUrlManager)
            val libraryRoot = LibraryRoot(fileUrl, LibraryRootTypeId.COMPILED)
            entityStorage.addEntity(LibraryEntity(libraryId.name, libraryId.tableId, listOf(libraryRoot), source))
        }

        return LibraryDependency(libraryId, false, DependencyScope.COMPILE)
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
