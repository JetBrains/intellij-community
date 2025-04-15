// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.ProjectRootManager
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
import org.jetbrains.kotlin.idea.core.script.KOTLIN_SCRIPTS_MODULE_NAME
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.LibraryDependencyFactory
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.toVfsRoots
import org.jetbrains.kotlin.idea.core.script.dependencies.indexSourceRootsEagerly
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationsSource
import org.jetbrains.kotlin.idea.core.script.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.idea.core.script.ucache.relativeName
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.adjustByDefinition
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlin.tools.projectWizard.transformers.Predicate
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.nio.file.Path
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.util.PropertiesCollection

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

        val storageWithGradleScriptModules = getUpdatedStorage(
            project, data.get()
        ) { KotlinGradleScriptModuleEntitySource(it) }

        storage.replaceBySource(gradleEntitySourceFilter, storageWithGradleScriptModules)
    }

    override fun getScriptDefinitionsSource(): ScriptDefinitionsSource? =
        project.scriptDefinitionsSourceOfType<GradleScriptDefinitionsSource>()

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

    private fun getUpdatedStorage(
        project: Project,
        configurations: Map<VirtualFile, ScriptConfigurationWithSdk>,
        entitySourceSupplier: (virtualFileUrl: VirtualFileUrl) -> KotlinScriptEntitySource,
    ): MutableEntityStorage {
        val updatedStorage = MutableEntityStorage.create()

        val fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
        val updatedFactory = LibraryDependencyFactory(fileUrlManager, updatedStorage)

        for ((scriptFile, configurationWithSdk) in configurations) {
            val configuration = configurationWithSdk.scriptConfiguration.valueOrNull() ?: continue
            val source = entitySourceSupplier(scriptFile.toVirtualFileUrl(fileUrlManager))

            val definition = findScriptDefinition(project, VirtualFileScriptSource(scriptFile))
            val definitionName = definition.name
            val externalProjectPath = definition.compilationConfiguration[ScriptCompilationConfiguration.gradle.externalProjectPath]

            val definitionScriptModuleName = "$KOTLIN_SCRIPTS_MODULE_NAME.$definitionName"
            val locationName = scriptFile.relativeName(project).replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')

            val sdkDependency = configurationWithSdk.sdk?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

            val classes = toVfsRoots(configuration.dependenciesClassPath).toMutableSet()

            val allDependencies = listOfNotNull(sdkDependency) + buildList {
                if (indexSourceRootsEagerly()) {
                    val sources = toVfsRoots(configuration.dependenciesSources).toMutableSet()
                    addAll(getDependenciesFromGradleLibs(classes, sources, fileUrlManager, project))
                    addAll(updatedStorage.createDependenciesWithSources(classes, sources, source, fileUrlManager))
                    add(updatedStorage.groupClassesSourcesByPredicate(classes, sources, source, fileUrlManager, "$locationName accessors dependencies") {
                        it.path.contains("accessors")
                    })
                    add(updatedStorage.groupClassesSourcesByPredicate(classes, sources, source, fileUrlManager, "$locationName groovy dependencies") {
                        it.path.contains("groovy")
                    })
                    add(updatedStorage.groupClassesSourcesByPredicate(classes, sources, source, fileUrlManager, "$locationName kotlin dependencies") {
                        it.name.contains("kotlin")
                    })
                }

                addAll(classes.map { updatedFactory.get(it, source) })
            }.sortedBy { it.library.name }

            val moduleName = "$definitionScriptModuleName.$locationName"
            updatedStorage addEntity ModuleEntity(moduleName, allDependencies, source) {
                this.exModuleOptions = ExternalSystemModuleOptionsEntity(source) {
                    this.externalSystem = GradleConstants.SYSTEM_ID.id
                    this.module = this@ModuleEntity
                    this.rootProjectPath = "kotlin-scripts:$externalProjectPath"
                    this.linkedProjectId = moduleName
                }
            }
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
        val sourcesNames = sources.associateBy { it.name }

        val jar = ".jar"
        val pairs = classes.filter { it.name.contains(jar) }.associateWith {
            sourcesNames[it.name] ?: sourcesNames[it.name.replace(jar, "-sources.jar")]
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
            if (predicate.invoke(element)) {
                removed.add(element)
                iterator.remove()
            }
        }
        return removed
    }

    data class KotlinGradleScriptModuleEntitySource(override val virtualFileUrl: VirtualFileUrl) : KotlinScriptEntitySource(virtualFileUrl)
}

interface GradleScriptCompilationConfigurationKeys

open class GradleScriptCompilationConfigurationBuilder : PropertiesCollection.Builder(),
                                                         GradleScriptCompilationConfigurationKeys {
    companion object : GradleScriptCompilationConfigurationKeys
}

val ScriptCompilationConfigurationKeys.gradle: GradleScriptCompilationConfigurationBuilder
    get() = GradleScriptCompilationConfigurationBuilder()

val GradleScriptCompilationConfigurationKeys.externalProjectPath: PropertiesCollection.Key<String?> by PropertiesCollection.key()

