// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.gradle.scripting.shared.GradleScriptModel
import org.jetbrains.kotlin.gradle.scripting.shared.GradleScriptModelData
import org.jetbrains.kotlin.gradle.scripting.shared.KotlinGradleScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.k2.configurations.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.idea.core.script.k2.configurations.toVirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.k2.modules.*
import org.jetbrains.kotlin.idea.core.script.v1.indexSourceRootsEagerly
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
            val sources = configuration.dependenciesSources.sorted().map { it.path.toVirtualFileUrl(fileUrlManager) }.toMutableSet()

            val dependencies = buildList {
                addIfNotNull(
                    extractRootsByPredicate(classes, sources) {
                        it.url.contains("kotlin-stdlib")
                    }
                )

                addIfNotNull(
                    extractRootsByPredicate(classes, sources) {
                        it.url.contains("accessors")
                    }
                )

                addIfNotNull(
                    extractRootsByPredicate(classes, sources) {
                        it.url.contains("kotlin-gradle-plugin")
                    }
                )

                if (indexSourceRootsEagerly() || AdvancedSettings.getBoolean("gradle.attach.scripts.dependencies.sources")) {
                    addAll(extractDependenciesWithSources(classes, sources))

                    groupSourcesByParent(sources)

                    addAll(
                        classes.map {
                            getOrCreateScriptLibrary(it, sources)
                        }
                    )
                } else {
                    addAll(
                        classes.map {
                            getOrCreateScriptLibrary(it)
                        }
                    )
                }
            }

            val sdk = configurationWithSdk.sdk?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) } ?: InheritedSdkDependency

            this addEntity KotlinScriptEntity(scriptUrl, dependencies, sdk, KotlinGradleScriptEntitySource)
        }
    }

    private fun MutableEntityStorage.getOrCreateScriptLibrary(
        jar: VirtualFileUrl,
        sources: Collection<VirtualFileUrl>
    ): KotlinScriptLibraryEntityId {
        val id = KotlinScriptLibraryEntityId(listOf(jar), sources.toList())

        if (!contains(id)) {
            this addEntity KotlinScriptLibraryEntity(
                id.classes,
                id.sources,
                KotlinGradleScriptEntitySource
            )
        }

        return id
    }

    private fun MutableEntityStorage.getOrCreateScriptLibrary(
        url: VirtualFileUrl
    ): KotlinScriptLibraryEntityId {
        val id = KotlinScriptLibraryEntityId(url)

        if (!contains(id)) {
            this addEntity KotlinScriptLibraryEntity(
                id.classes,
                id.sources,
                KotlinGradleScriptEntitySource
            )
        }

        return id
    }

    /**
     * Extracts and registers Kotlin Script library dependencies by pairing class JAR files with their corresponding source JAR files.
     * This method searches for matching `-sources.jar` files in the given `sources` set based on the file names of files in `classes`.
     *
     * @param classes a mutable set of [VirtualFileUrl]s pointing to class JAR files
     * @param sources a mutable set of [VirtualFileUrl]s pointing to source JAR files
     * @return a list of [KotlinScriptLibraryEntityId]s that were successfully created and registered
     */
    private fun MutableEntityStorage.extractDependenciesWithSources(
        classes: MutableSet<VirtualFileUrl>, sources: MutableSet<VirtualFileUrl>
    ): List<KotlinScriptLibraryEntityId> {
        val result: MutableList<KotlinScriptLibraryEntityId> = mutableListOf()
        val jar = ".jar!/"
        val sourcesJar = "-sources.jar!/"

        val sourcesNames = sources.filter { it.url.endsWith(sourcesJar) }.associateBy {
            it.url.removeSuffix(sourcesJar).substringAfterLast("/")
        }

        sequence {
            classes.filter { it.url.endsWith(jar) }.forEach { classUrl ->
                val matchingSourceUrl = sourcesNames[classUrl.url.removeSuffix(jar).substringAfterLast("/")]
                if (matchingSourceUrl != null && matchingSourceUrl != classUrl) {
                    yield(classUrl to matchingSourceUrl)
                }
            }
        }.forEach { (classUrl, sourceUrl) ->
            val id = KotlinScriptLibraryEntityId(listOf(classUrl), listOf(sourceUrl))
            if (!this.contains(id)) {
                this addEntity KotlinScriptLibraryEntity(id.classes, id.sources, KotlinGradleScriptEntitySource)
            }

            classes.remove(classUrl)
            sources.remove(sourceUrl)
            result.add(id)
        }

        return result
    }

    private fun groupSourcesByParent(sources: MutableSet<VirtualFileUrl>) {
        sources.groupBy { it.parent }.forEach { (parent, sourcesToRemove) ->
            if (parent != null) {
                sources.add(parent)
                sources.removeAll(sourcesToRemove.toSet())
            }
        }
    }

    private fun MutableEntityStorage.extractRootsByPredicate(
        classes: MutableSet<VirtualFileUrl>,
        sources: MutableSet<VirtualFileUrl>,
        predicate: Predicate<VirtualFileUrl>
    ): KotlinScriptLibraryEntityId? {
        val groupedClasses = classes.removeOnMatch(predicate)
        if (groupedClasses.isEmpty()) return null

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

    companion object {
        @JvmStatic
        fun getInstance(project: Project): GradleScriptRefinedConfigurationProvider = project.service()
    }
}