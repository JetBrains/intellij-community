// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.gradle.scripting.shared.*
import org.jetbrains.kotlin.idea.core.script.*
import org.jetbrains.kotlin.idea.core.script.k2.configurations.scriptModuleRelativeLocation
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptRefinedConfigurationResolver
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptWorkspaceModelManager
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.adjustByDefinition
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
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

    suspend fun processScripts(scriptsData: GradleScriptModelData, storageToUpdate: MutableEntityStorage? = null) {
        val sdk = scriptsData.javaHome.resolveSdk()
        val javaHomePath = sdk?.homePath?.let { File(it) }

        val configurations = scriptsData.models.associate { gradleScript: GradleScriptModel ->
            val sourceCode = VirtualFileScriptSource(gradleScript.virtualFile)
            val definition = findScriptDefinition(project, sourceCode)

            val configuration = definition.compilationConfiguration.with {
                if (javaHomePath != null) {
                    jvm.jdkHome(javaHomePath)
                }
                defaultImports(gradleScript.imports)
                dependencies(JvmDependency(gradleScript.classPath.map { File(it) }))
                ide.dependenciesSources(JvmDependency(gradleScript.sourcePath.map { File(it) }))
            }.adjustByDefinition(definition)

            val updatedConfiguration = refineScriptCompilationConfiguration(sourceCode, definition, project, configuration)

            gradleScript.virtualFile to ScriptConfigurationWithSdk(updatedConfiguration, sdk)
        }

        val updatedStorage = MutableEntityStorage.create()
        if (storageToUpdate == null) {
            project.workspaceModel.update("updating .gradle.kts scripts") { storage ->
                updatedStorage.enrichStorage(configurations) // under writeAction from workspaceModel.update
                storage.replaceBySource({ it is KotlinGradleScriptModuleEntitySource }, updatedStorage)
            }
        } else {
            updatedStorage.enrichStorage(configurations) // do not care about the locks here
            storageToUpdate.replaceBySource({ it is KotlinGradleScriptModuleEntitySource }, updatedStorage)
        }

        data.set(configurations)
    }

    private fun String?.resolveSdk(): Sdk? {
        if (this == null) {
            scriptingWarnLog("Gradle javaHome is null")
            return null
        }
        return ExternalSystemJdkUtil.lookupJdkByPath(this).also {
            scriptingDebugLog { "resolved gradle sdk=$it, javaHome=$this" }
        }
    }

    override suspend fun updateWorkspaceModel(configurationPerFile: Map<VirtualFile, ScriptConfigurationWithSdk>) {
        if (configurationPerFile.size == 1) {
            val currentStorage = project.workspaceModel.currentSnapshot.toBuilder()
            project.workspaceModel.update("updating .gradle.kts scripts") { storage ->
                currentStorage.enrichStorage(configurationPerFile) // under writeAction from workspaceModel.update
                storage.applyChangesFrom(currentStorage)
            }
        }
    }

    private fun MutableEntityStorage.enrichStorage(
        configurations: Map<VirtualFile, ScriptConfigurationWithSdk>,
    ) {
        val urlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
        val virtualFileCache = ScriptVirtualFileCache()
        val dependencyFactory = ScriptDependencyFactory(this, configurations)

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
                    groupRootsByPredicate(classes, sources, source, "kotlin-stdlib dependencies") {
                        it.name.contains("kotlin-stdlib")
                    }
                )

                if (indexSourceRootsEagerly()) {
                    addAll(createDependenciesWithSources(classes, sources, source))
                    add(
                        groupRootsByPredicate(
                            classes, sources, source, "$scriptRelativeLocation accessors dependencies"
                        ) {
                            it.path.contains("accessors")
                        })
                    add(
                        groupRootsByPredicate(
                            classes, sources, source, "$scriptRelativeLocation groovy dependencies"
                        ) {
                            it.path.contains("groovy")
                        })
                }

                addAll(classes.map {
                    dependencyFactory.get(it, source)
                })
            }.distinct().sortedBy { it.library.name }

            val scriptModuleId = ModuleId("${KOTLIN_SCRIPTS_MODULE_NAME}.$definitionName.$scriptRelativeLocation")
            if (!this.contains(scriptModuleId)) {
                this addEntity ModuleEntity(scriptModuleId.name, allDependencies, source) {
                    this.exModuleOptions = ExternalSystemModuleOptionsEntity.Companion(source) {
                        this.externalSystem = GradleConstants.SYSTEM_ID.id
                        this.module = this@ModuleEntity
                        this.rootProjectPath = "kotlin-scripts:$externalProjectPath"
                        this.linkedProjectId = scriptModuleId.name
                    }
                }
            }
        }
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
        scripts: Map<VirtualFile, ScriptConfigurationWithSdk>
    ) {
        private val nameCache: Map<String, Int> = scripts.asSequence()
            .mapNotNull { it.value.scriptConfiguration.valueOrNull() }
            .flatMap { it.dependenciesClassPath }
            .distinct()
            .groupingBy { it.name }
            .eachCount()

        fun get(file: VirtualFile, source: KotlinScriptEntitySource): LibraryDependency {
            val libraryName = resolveLibraryName(file)
            val roots = listOf(file.compiledLibraryRoot(project))
            return entityStorage.getOrCreateLibrary(libraryName, roots, source)
        }

        private fun resolveLibraryName(file: VirtualFile): String {
            val filesWithSameName = nameCache[file.name] ?: return file.name
            if (filesWithSameName == 1) return file.name
            val path = file.path.substringAfterLastOrNull(GradleConstants.GRADLE_CACHE_DIR_NAME) ?: return file.path
            return GradleConstants.GRADLE_CACHE_DIR_NAME + path
        }

        private fun String.substringAfterLastOrNull(delimiter: String): String? {
            val index = lastIndexOf(delimiter)
            return if (index == -1) null else substring(index + delimiter.length, length)
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