// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.gradle.scripting.shared.GradleScriptModel
import org.jetbrains.kotlin.gradle.scripting.shared.GradleScriptModelData
import org.jetbrains.kotlin.gradle.scripting.shared.KotlinGradleScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId
import org.jetbrains.kotlin.idea.core.script.k2.configurations.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.idea.core.script.k2.configurations.toVirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptRefinedConfigurationResolver
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptWorkspaceModelManager
import org.jetbrains.kotlin.idea.core.script.shared.indexSourceRootsEagerly
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.v1.scriptingWarnLog
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.adjustByDefinition
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
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

        val updatedStorage = MutableEntityStorage.create().apply {
            enrichStorage(configurations)
        }

        if (storageToUpdate == null) {
            project.workspaceModel.update("updating .gradle.kts scripts") { storage ->
                storage.replaceBySource({ it is KotlinGradleScriptEntitySource }, updatedStorage)
            }
        } else {
            storageToUpdate.replaceBySource({ it is KotlinGradleScriptEntitySource }, updatedStorage)
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
        val fileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

        for ((scriptFile, configurationWithSdk) in configurations) {
            val configuration = configurationWithSdk.scriptConfiguration.valueOrNull() ?: continue

            val scriptUrl = scriptFile.toVirtualFileUrl(fileUrlManager)

            val classes = configuration.dependenciesClassPath.sorted().map { it.path.toVirtualFileUrl(fileUrlManager) }.toMutableSet()
            val sources = configuration.dependenciesSources.sorted().map {it.path.toVirtualFileUrl(fileUrlManager) }.toMutableSet()

            val dependencies = buildList {
                add(
                    extractRootsByPredicate(classes, sources) {
                        it.url.contains("kotlin-stdlib")
                    }
                )

                if (indexSourceRootsEagerly()) {
                    addAll(createDependenciesWithSources(classes, sources))

                    add(extractRootsByPredicate(classes, sources) {
                        it.url.contains("accessors")
                    })

                    add(extractRootsByPredicate(classes, sources) {
                        it.url.contains("groovy")
                    })
                }

                addAll(
                    classes.map {
                        getOrCreateScriptLibrary(it)
                    }
                )
            }

            this addEntity KotlinScriptEntity(scriptUrl, dependencies, KotlinGradleScriptEntitySource)
        }
    }

    private fun MutableEntityStorage.getOrCreateScriptLibrary(
        url: VirtualFileUrl
    ): KotlinScriptLibraryEntityId {
        val id = KotlinScriptLibraryEntityId(url)

        if (!contains(id)) {
            this addEntity KotlinScriptLibraryEntity(
                listOf(url),
                emptyList(),
                KotlinGradleScriptEntitySource
            )
        }

        return id
    }

    private fun MutableEntityStorage.extractRootsByPredicate(
        classes: MutableSet<VirtualFileUrl>,
        sources: MutableSet<VirtualFileUrl>,
        predicate: Predicate<VirtualFileUrl>
    ): KotlinScriptLibraryEntityId {
        val groupedClasses = classes.removeOnMatch(predicate)
        val groupedSources = sources.removeOnMatch(predicate)

        val id = KotlinScriptLibraryEntityId(groupedClasses, groupedSources)
        if (!this.contains(id)) {
            this addEntity KotlinScriptLibraryEntity(groupedClasses, groupedSources, KotlinGradleScriptEntitySource)
        }

        return id
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

    private fun MutableEntityStorage.createDependenciesWithSources(
        classes: MutableSet<VirtualFileUrl>, sources: MutableSet<VirtualFileUrl>
    ): List<KotlinScriptLibraryEntityId> {
        val result: MutableList<KotlinScriptLibraryEntityId> = mutableListOf()
        val sourcesNames = sources.associateBy { it.fileName }

        val jar = ".jar"
        val pairs = classes.filter { it.fileName.contains(jar) }.associateWith {
            sourcesNames[it.fileName] ?: sourcesNames[it.fileName.replace(jar, "-sources.jar")]
        }.filterValues { it != null }

        for ((left, right) in pairs) {
            if (right != null) {
                val classesUrl = listOf(left)
                val sourcesUrl = listOf(right)

                val id = KotlinScriptLibraryEntityId(classesUrl, sourcesUrl)
                if (!this.contains(id)) {
                    this addEntity KotlinScriptLibraryEntity(classesUrl, sourcesUrl, KotlinGradleScriptEntitySource)
                }

                classes.remove(left)
                sources.remove(right)
                result.add(id)
            }
        }

        return result
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): GradleScriptRefinedConfigurationProvider = project.service()
    }
}