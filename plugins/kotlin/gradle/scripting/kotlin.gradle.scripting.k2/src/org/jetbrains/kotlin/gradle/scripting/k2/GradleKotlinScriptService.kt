// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.gradle.scripting.k2.definition.withIdeKeys
import org.jetbrains.kotlin.gradle.scripting.k2.importing.GradleScriptData
import org.jetbrains.kotlin.gradle.scripting.k2.importing.GradleScriptModel
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.KotlinGradleScriptEntitySource
import org.jetbrains.kotlin.gradle.scripting.shared.definition.GradleScriptDefinition
import org.jetbrains.kotlin.gradle.scripting.shared.definition.loadGradleDefinitions
import org.jetbrains.kotlin.idea.core.script.k2.asEntity
import org.jetbrains.kotlin.idea.core.script.k2.configurations.sdkId
import org.jetbrains.kotlin.idea.core.script.k2.configurations.toVirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.core.script.k2.highlighting.KotlinScriptResolutionService.Companion.dropKotlinScriptCaches
import org.jetbrains.kotlin.idea.core.script.k2.modules.*
import org.jetbrains.kotlin.idea.core.script.v1.indexSourceRootsEagerly
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.v1.scriptingWarnLog
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.adjustByDefinition
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlin.utils.addIfNotNull
import java.io.File
import java.util.function.Predicate
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

@Service(Service.Level.PROJECT)
class GradleKotlinScriptService(val project: Project) : ScriptConfigurationProviderExtension {
    private val urlManager: VirtualFileUrlManager
        get() = project.workspaceModel.getVirtualFileUrlManager()

    private val VirtualFile.virtualFileUrl: VirtualFileUrl
        get() = toVirtualFileUrl(urlManager)

    override suspend fun create(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptCompilationConfigurationResult {
        val configuration = refineScriptCompilationConfiguration(VirtualFileScriptSource(virtualFile), definition, project)

        val currentStorage = project.workspaceModel.currentSnapshot.toBuilder()
        project.updateKotlinScriptEntities(KotlinGradleScriptEntitySource) { storage ->
            currentStorage.updateStorage(virtualFile, configuration)
            storage.applyChangesFrom(currentStorage)
        }

        return configuration
    }

    fun updateStorage(
        scriptData: GradleScriptData,
        storageToUpdate: MutableEntityStorage
    ) {
        val javaHome = scriptData.definitionsParams.javaHome
        val definitions = loadGradleDefinitions(scriptData.definitionsParams).map { it.withIdeKeys(project) }
        val updatedStorage = MutableEntityStorage.create().apply {
            updateStorage(this, scriptData.models, definitions, javaHome)
        }
        storageToUpdate.replaceBySource({ it is KotlinGradleScriptEntitySource }, updatedStorage)
    }

    fun updateStorage(
        storage: MutableEntityStorage,
        models: Collection<GradleScriptModel>,
        definitions: Collection<GradleScriptDefinition>,
        javaHome: String? = null
    ) {
        definitions.forEach {
            storage addEntity GradleScriptDefinitionEntity(
                it.definitionId,
                it.compilationConfiguration.asEntity(),
                it.hostConfiguration.asEntity(),
                KotlinGradleScriptEntitySource
            ) {
                evaluationConfiguration = it.evaluationConfiguration?.asEntity()
            }
        }

        val javaHomePath = javaHome.resolveSdk()?.homePath?.let { File(it) }
        val configurations = mutableMapOf<VirtualFile, ScriptCompilationConfigurationResult>()

        for (model in models) {
            val sourceCode = VirtualFileScriptSource(model.virtualFile)
            val definition = definitions.firstOrNull { it.isScript(sourceCode) } ?: continue

            val configuration = definition.compilationConfiguration.with {
                if (javaHomePath != null) {
                    jvm.jdkHome(javaHomePath)
                }
                defaultImports(model.imports)
                dependencies(JvmDependency(model.classPath.map { File(it) }))
                ide.dependenciesSources(JvmDependency(model.sourcePath.map { File(it) }))
            }.adjustByDefinition(definition)

            configurations[model.virtualFile] = refineScriptCompilationConfiguration(sourceCode, definition, project, configuration)
        }

        configurations.forEach { (virtualFile, configurationResult) -> storage.updateStorage(virtualFile, configurationResult) }
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

    private fun MutableEntityStorage.updateStorage(virtualFile: VirtualFile, configurationResult: ScriptCompilationConfigurationResult) {
        val configurationWrapper = configurationResult.valueOrNull() ?: return
        if (getVirtualFileUrlIndex().findEntitiesByUrl(virtualFile.virtualFileUrl).any()) return

        val classes =
            configurationWrapper.dependenciesClassPath.sorted().map { it.path.toVirtualFileUrl(urlManager) }.toMutableSet()
        val sources = configurationWrapper.dependenciesSources.sorted().map { it.path.toVirtualFileUrl(urlManager) }.toMutableSet()

        val dependencies = buildList {
            addIfNotNull(
                extractRootsByPredicate(classes, sources) {
                    it.url.contains("kotlin-stdlib")
                })

            addIfNotNull(
                extractRootsByPredicate(classes, sources) {
                    it.url.contains("accessors")
                })

            addIfNotNull(
                extractRootsByPredicate(classes, sources) {
                    it.url.contains("kotlin-gradle-plugin")
                })

            if (indexSourceRootsEagerly() || AdvancedSettings.getBoolean("gradle.attach.scripts.dependencies.sources")) {
                addAll(extractDependenciesWithSources(classes, sources))

                groupSourcesByParent(sources)

                addAll(
                    classes.map {
                        getOrCreateScriptLibrary(it, sources)
                    })
            } else {
                addAll(
                    classes.map {
                        getOrCreateScriptLibrary(it)
                    })
            }
        }

        this addEntity KotlinScriptEntity(
            virtualFile.virtualFileUrl, dependencies, KotlinGradleScriptEntitySource
        ) {
            this.configuration = configurationWrapper.configuration?.asEntity()
            this.reports = configurationResult.reports.map { it.toData() }.toMutableList()
            this.sdkId = configurationWrapper.configuration?.sdkId
        }
    }

    private fun MutableEntityStorage.getOrCreateScriptLibrary(
        jar: VirtualFileUrl, sources: Collection<VirtualFileUrl>
    ): KotlinScriptLibraryEntityId {
        val id = KotlinScriptLibraryEntityId(listOf(jar), sources.toList())

        if (!contains(id)) {
            this addEntity KotlinScriptLibraryEntity(
                id.classes, id.sources, KotlinGradleScriptEntitySource
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
                id.classes, id.sources, KotlinGradleScriptEntitySource
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
        classes: MutableSet<VirtualFileUrl>, sources: MutableSet<VirtualFileUrl>, predicate: Predicate<VirtualFileUrl>
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

    @Suppress("unused")
    private class GradleWorkspaceModelListener(val project: Project, val scope: CoroutineScope) : WorkspaceModelChangeListener {
        override fun beforeChanged(event: VersionedStorageChange) {
            val definitionChanges = event.getChanges(GradleScriptDefinitionEntity::class.java)
            val configurationChanges = event.getChanges(KotlinScriptEntity::class.java)

            if (definitionChanges.any() || configurationChanges.any()) {
                dropKotlinScriptCaches(project)
            }
        }

        override fun changed(event: VersionedStorageChange) = scope.launchTracked {
            val definitionChanges = event.getChanges(GradleScriptDefinitionEntity::class.java)
            val configurationChanges = event.getChanges(KotlinScriptEntity::class.java)
            if (definitionChanges.none() && configurationChanges.none()) return@launchTracked

            if (definitionChanges.any()) {
                ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): GradleKotlinScriptService = project.service()
    }
}
