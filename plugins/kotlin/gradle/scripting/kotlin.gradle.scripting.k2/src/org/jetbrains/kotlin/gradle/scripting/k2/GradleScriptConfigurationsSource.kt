// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
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

        val storageWithGradleScriptModules = getUpdatedStorage(data.get())

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

    private suspend fun getUpdatedStorage(configurations: Map<VirtualFile, ScriptConfigurationWithSdk>): MutableEntityStorage {
        val result = MutableEntityStorage.create()

        val urlManager = project.serviceAsync<WorkspaceModel>().getVirtualFileUrlManager()
        val dependencyFactory = ScriptDependencyFactory(result, configurations)

        for ((scriptFile, configurationWithSdk) in configurations) {
            val configuration = configurationWithSdk.scriptConfiguration.valueOrNull() ?: continue
            val source = KotlinGradleScriptModuleEntitySource(scriptFile.toVirtualFileUrl(urlManager))

            val definitionName = findScriptDefinition(project, VirtualFileScriptSource(scriptFile)).name

            val definitionScriptModuleName = "$KOTLIN_SCRIPTS_MODULE_NAME.$definitionName"
            val locationName = scriptFile.relativeName(project).replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')

            val sdkDependency = configurationWithSdk.sdk?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

            val classes = toVfsRoots(configuration.dependenciesClassPath).toMutableSet()

            val allDependencies = listOfNotNull(sdkDependency) + buildList {
                val sources = toVfsRoots(configuration.dependenciesSources).toMutableSet()
                add(
                    result.groupRootsByPredicate(classes, sources, source, "kotlin-stdlib dependencies") {
                        it.name.contains("kotlin-stdlib")
                    }
                )

                if (indexSourceRootsEagerly()) {
                    addAll(result.createDependenciesWithSources(classes, sources, source))
                    add(
                        result.groupRootsByPredicate(
                            classes, sources, source, "$locationName accessors dependencies"
                        ) {
                            it.path.contains("accessors")
                        })
                    add(
                        result.groupRootsByPredicate(
                            classes, sources, source, "$locationName groovy dependencies"
                        ) {
                            it.path.contains("groovy")
                        })
                }

                addAll(classes.map {
                    dependencyFactory.get(it, source)
                })
            }.distinct().sortedBy { it.library.name }

            result.addEntity(ModuleEntity("$definitionScriptModuleName.$locationName", allDependencies, source))
        }

        return result
    }

    private val VirtualFile.sourceLibraryRoot: LibraryRoot
        get() {
            val urlManager = project.workspaceModel.getVirtualFileUrlManager()
            return LibraryRoot(toVirtualFileUrl(urlManager), LibraryRootTypeId.SOURCES)
        }

    private val VirtualFile.compiledLibraryRoot: LibraryRoot
        get() {
            val urlManager = project.workspaceModel.getVirtualFileUrlManager()
            return LibraryRoot(toVirtualFileUrl(urlManager), LibraryRootTypeId.COMPILED)
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

        val classRoots = groupedClasses.map { it.compiledLibraryRoot }.sortedWith(ROOT_COMPARATOR)
        val sourceRoots = groupedSources.map { it.sourceLibraryRoot }.sortedWith(ROOT_COMPARATOR)

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
    ) {
        private val nameCache = HashMap<String, Set<VirtualFile>>()

        init {
            val classes = scripts.mapNotNull { it.value.scriptConfiguration.valueOrNull() }.flatMap {
                it.dependenciesClassPath
            }.toSet()

            toVfsRoots(classes).forEach {
                nameCache.compute(it.name) { _, list ->
                    (list ?: emptySet()) + it
                }
            }
        }

        fun get(file: VirtualFile, source: KotlinScriptEntitySource): LibraryDependency {
            val filesWithSameName = nameCache[file.name]
            return if (filesWithSameName == null || filesWithSameName.size == 1) {
                entityStorage.getOrCreateLibrary(file.name, listOf(file.compiledLibraryRoot), source)
            } else {
                val commonAncestor = findCommonAncestor(filesWithSameName.map { it.path })
                if (commonAncestor == null) {
                    entityStorage.getOrCreateLibrary(file.name, listOf(file.compiledLibraryRoot), source)
                } else {
                    val libraryName = file.path.replace("$commonAncestor/", "")
                    entityStorage.getOrCreateLibrary(libraryName, listOf(file.compiledLibraryRoot), source)
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
                val roots = listOf(left.compiledLibraryRoot, right.sourceLibraryRoot)
                val dependency = createOrUpdateLibrary(left.name, roots, source)

                classes.remove(left)
                sources.remove(right)
                result.add(dependency)
            }
        }

        return result
    }

    data class KotlinGradleScriptModuleEntitySource(override val virtualFileUrl: VirtualFileUrl) : KotlinScriptEntitySource(virtualFileUrl)
}

private fun MutableEntityStorage.createOrUpdateLibrary(
    libraryName: String, roots: List<LibraryRoot>, source: KotlinScriptEntitySource
): LibraryDependency {
    val libraryId = LibraryId(libraryName, LibraryTableId.ProjectLibraryTableId)
    val existingLibrary = this.resolve(libraryId)

    if (existingLibrary == null) {
        createLibrary(libraryId, roots, source)
    } else {
        val existingRoots = existingLibrary.roots.toSet()
        if (!existingRoots.containsAll(roots)) {
            modifyEntity(LibraryEntity.Builder::class.java, existingLibrary) {
                this.roots = (this.roots.toSet() + roots).sortedWith(ROOT_COMPARATOR).toMutableList()
            }
        }
    }

    return LibraryDependency(libraryId, false, DependencyScope.COMPILE)
}

private fun MutableEntityStorage.getOrCreateLibrary(
    libraryName: String, roots: List<LibraryRoot>, source: KotlinScriptEntitySource
): LibraryDependency {
    val libraryId = LibraryId(libraryName, LibraryTableId.ProjectLibraryTableId)
    if (!this.contains(libraryId)) createLibrary(libraryId, roots, source)

    return LibraryDependency(libraryId, false, DependencyScope.COMPILE)
}

private fun MutableEntityStorage.createLibrary(
    libraryId: LibraryId, roots: List<LibraryRoot>, source: KotlinScriptEntitySource
): LibraryEntity {
    val sortedRoots = roots.sortedWith(ROOT_COMPARATOR)
    val libraryEntity = LibraryEntity(libraryId.name, libraryId.tableId, sortedRoots, source)

    return addEntity(libraryEntity)
}

private val ROOT_COMPARATOR: Comparator<LibraryRoot> = Comparator { o1, o2 ->
    when {
        o1 == o2 -> 0
        o1 == null -> -1
        o2 == null -> 1
        else -> o1.url.url.compareTo(o2.url.url)
    }
}