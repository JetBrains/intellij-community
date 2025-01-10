// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.idea.core.script.KOTLIN_SCRIPTS_MODULE_NAME
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.LibraryDependencyFactory
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.toVfsRoots
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurations
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationsSource
import org.jetbrains.kotlin.idea.core.script.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.adjustByDefinition
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlin.tools.projectWizard.transformers.Predicate
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString
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
) : BaseScriptModel(virtualFile)

internal open class GradleScriptConfigurationsSource(override val project: Project) :
    ScriptConfigurationsSource<GradleScriptModel>(project) {
    private val gradleEntitySourceFilter: (EntitySource) -> Boolean =
        { entitySource -> entitySource is KotlinGradleScriptModuleEntitySource }

    override suspend fun updateModules(storage: MutableEntityStorage?) {
        if (storage == null) return

        val storageWithGradleScriptModules = getUpdatedStorage(
            project, data.get()
        ) { KotlinGradleScriptModuleEntitySource(it) }

        storage.replaceBySource(gradleEntitySourceFilter, storageWithGradleScriptModules)
    }

    override fun getScriptDefinitionsSource(): ScriptDefinitionsSource? =
        project.scriptDefinitionsSourceOfType<GradleScriptDefinitionsSource>()

    override suspend fun updateConfigurations(scripts: Iterable<GradleScriptModel>) {
        val sdks = mutableMapOf<Path, Sdk>()

        val newConfigurations = mutableMapOf<VirtualFile, ResultWithDiagnostics<ScriptCompilationConfigurationWrapper>>()

        for (script in scripts) {
            val sourceCode = VirtualFileScriptSource(script.virtualFile)
            val definition = findScriptDefinition(project, sourceCode)

            val javaProjectSdk = ProjectRootManager.getInstance(project).projectSdk?.takeIf { it.sdkType is JavaSdkType }

            val javaHomePath = (javaProjectSdk?.homePath ?: script.javaHome)?.let { Path.of(it) }

            val configuration = definition.compilationConfiguration.with {
                javaHomePath?.let {
                    jvm.jdkHome(it.toFile())
                }
                defaultImports(script.imports)
                dependencies(JvmDependency(script.classPath.map { File(it) }))
                ide.dependenciesSources(JvmDependency(script.sourcePath.map { File(it) }))
            }.adjustByDefinition(definition)

            val updatedConfiguration = smartReadAction(project) {
                refineScriptCompilationConfiguration(sourceCode, definition, project, configuration)
            }
            newConfigurations[script.virtualFile] = updatedConfiguration

            if (javaProjectSdk != null) {
                javaProjectSdk.homePath?.let { path ->
                    sdks.computeIfAbsent(Path.of(path)) { javaProjectSdk }
                }
            } else if (javaHomePath != null) {
                sdks.computeIfAbsent(javaHomePath) {
                    ExternalSystemJdkUtil.lookupJdkByPath(it.pathString)
                }
            }
        }

        data.set(ScriptConfigurations(newConfigurations, sdks))
    }

    private fun getUpdatedStorage(
        project: Project,
        configurationsData: ScriptConfigurations,
        entitySourceSupplier: (virtualFileUrl: VirtualFileUrl) -> KotlinScriptEntitySource,
    ): MutableEntityStorage {
        val updatedStorage = MutableEntityStorage.create()

        val projectPath = project.basePath?.let { Path.of(it) } ?: return updatedStorage

        val manager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

        val fileUrlManager = manager
        val updatedFactory = LibraryDependencyFactory(fileUrlManager, updatedStorage)

        for ((scriptFile, configurationWrapper) in configurationsData.configurations) {
            val configuration = configurationWrapper.valueOrNull() ?: continue
            val source = entitySourceSupplier(scriptFile.toVirtualFileUrl(fileUrlManager))

            val basePath = projectPath.toFile()
            val file = Path.of(scriptFile.path).toFile()
            val relativeLocation = FileUtil.getRelativePath(basePath, file) ?: continue

            val definitionName = findScriptDefinition(project, VirtualFileScriptSource(scriptFile)).name

            val definitionScriptModuleName = "$KOTLIN_SCRIPTS_MODULE_NAME.$definitionName"
            val locationName = relativeLocation.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')
            val moduleName = "$definitionScriptModuleName.$locationName"

            val sdkDependency = configuration.javaHome?.toPath()?.let { configurationsData.sdks[it] }
                ?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

            val classes = toVfsRoots(configuration.dependenciesClassPath).toMutableSet()
            val sources = toVfsRoots(configuration.dependenciesSources).toMutableSet()

            val allDependencies = buildList {
                addIfNotNull(sdkDependency)
                addAll(getDependenciesFromGradleLibs(classes, sources, manager, project))
                addAll(updatedStorage.createDependenciesWithSources(classes, sources, source, manager))
                add(updatedStorage.createDependencyWithKeyword(classes, sources, source, manager, locationName, "accessors"))
                add(updatedStorage.createDependencyWithKeyword(classes, sources, source, manager, locationName, "groovy"))
                addAll(classes.sortedBy { it.name }.map { updatedFactory.get(it, source) })
            }
            updatedStorage.addEntity(ModuleEntity(moduleName, allDependencies, source))
        }

        return updatedStorage
    }

    private fun getDependenciesFromGradleLibs(
        classes: MutableSet<VirtualFile>, sources: MutableSet<VirtualFile>, manager: VirtualFileUrlManager, project: Project
    ): List<LibraryDependency> {
        val currentSnapshot = project.workspaceModel.currentSnapshot

        val entityByRootUrl = mutableMapOf<VirtualFileUrl, LibraryEntity>()
        val entities = currentSnapshot.entities(LibraryEntity::class.java).filter { it.name.startsWith("Gradle:") }
        entities.forEach {
            it.roots.forEach { (url, _, _) ->
                entityByRootUrl[url] = it
            }
        }

        val classesEntities = mutableListOf<LibraryEntity>()
        val sourcesEntities = mutableListOf<LibraryEntity>()

        val classesIterator = classes.iterator()
        while (classesIterator.hasNext()) {
            val classVirtualFile = classesIterator.next()
            val found = entityByRootUrl[classVirtualFile.toVirtualFileUrl(manager)]
            if (found != null) {
                classesEntities.add(found)
                classesIterator.remove()
                sources.removeAll(found.roots.map { it.url.virtualFile })
            }
        }

        val sourcesIterator = sources.iterator()
        while (sourcesIterator.hasNext()) {
            val sourceVirtualFile = sourcesIterator.next()
            val found = entityByRootUrl[sourceVirtualFile.toVirtualFileUrl(manager)]
            if (found != null) {
                sourcesEntities.add(found)
                sourcesIterator.remove()
            }
        }

        return (classesEntities + sourcesEntities).toSet().map {
            LibraryDependency(it.symbolicId, false, DependencyScope.COMPILE)
        }
    }

    private fun MutableEntityStorage.createDependenciesWithSources(
        classes: MutableSet<VirtualFile>, sources: MutableSet<VirtualFile>, source: KotlinScriptEntitySource, manager: VirtualFileUrlManager
    ): List<LibraryDependency> {

        val dependencies: MutableList<LibraryDependency> = mutableListOf()
        val sourcesNames = sources.associate { it.name to it }

        val pairs = classes.associate {
            val sourcesName = it.name.replace(".jar", "-sources.jar")
            it to sourcesNames[sourcesName]
        }.filterValues { it != null }

        for ((left, right) in pairs) {
            if (right != null) {
                val libraryId = LibraryId("${left.name} (with sources)", LibraryTableId.ProjectLibraryTableId)
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

    private fun MutableEntityStorage.createDependencyWithKeyword(
        classes: MutableSet<VirtualFile>,
        sources: MutableSet<VirtualFile>,
        source: KotlinScriptEntitySource,
        manager: VirtualFileUrlManager,
        locationName: String,
        keywordToSearch: String
    ): LibraryDependency {

        val groupedClasses = classes.removeOnMatch { it.path.contains(keywordToSearch) }
        val groupedSources = sources.removeOnMatch { it.path.contains(keywordToSearch) }

        val classRoots = groupedClasses.sortedBy { it.name }.map {
            LibraryRoot(it.toVirtualFileUrl(manager), LibraryRootTypeId.COMPILED)
        }

        val sourceRoots = groupedSources.sortedBy { it.name }.map {
            LibraryRoot(it.toVirtualFileUrl(manager), LibraryRootTypeId.SOURCES)
        }

        val dependencyLibrary = addEntity(
            LibraryEntity(
                "$locationName $keywordToSearch dependencies", LibraryTableId.ProjectLibraryTableId, classRoots + sourceRoots, source
            )
        )

        return LibraryDependency(dependencyLibrary.symbolicId, false, DependencyScope.COMPILE)
    }

    private fun <T> MutableCollection<T>.removeOnMatch(predicate: Predicate<T>): MutableList<T> {
        val removed = mutableListOf<T>()
        val iterator = iterator()
        while (iterator.hasNext()) {
            val element = iterator.next()
            if (predicate.invoke(element)) {
                removed.add(element)
                iterator.remove()
            }
        }
        return removed
    }

    data class KotlinGradleScriptModuleEntitySource(override val virtualFileUrl: VirtualFileUrl) : KotlinScriptEntitySource(virtualFileUrl)
}
