// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
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
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel
import java.io.File
import java.util.function.Predicate
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

@Service(Service.Level.PROJECT)
class GradleKotlinScriptEntityProvider(override val project: Project) : KotlinScriptEntityProvider(project) {
    private val urlManager: VirtualFileUrlManager
        get() = project.workspaceModel.getVirtualFileUrlManager()

    override suspend fun updateWorkspaceModel(
        virtualFile: VirtualFile,
        definition: ScriptDefinition
    ) {
        val configuration = refineScriptCompilationConfiguration(VirtualFileScriptSource(virtualFile), definition, project)

        project.updateKotlinScriptEntities(KotlinGradleScriptEntitySource) { storage ->
            updateStorage(storage, virtualFile.virtualFileUrl, configuration, null)
        }
    }

    fun getUpdatedStorage(
        scriptData: GradleScriptData,
        storageToUpdate: MutableEntityStorage
    ): ImmutableEntityStorage {
        val javaHome = scriptData.definitionsParams.javaHome
        val definitions = loadGradleDefinitions(scriptData.definitionsParams).map { it.withIdeKeys(project) }
        return getUpdatedStorage(storageToUpdate, scriptData.models, definitions, javaHome)
    }

    fun getUpdatedStorage(
        storage: MutableEntityStorage,
        models: Collection<GradleScriptModel>,
        definitions: Collection<GradleScriptDefinition>,
        javaHome: String? = null
    ): ImmutableEntityStorage {
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

            val configurationResult = refineScriptCompilationConfiguration(sourceCode, definition, project, configuration)
            updateStorage(storage, model.virtualFile.virtualFileUrl, configurationResult, model.classpathModel)
        }

        return storage.toSnapshot()
    }

    private fun String?.resolveSdk(): Sdk? {
        if (this == null) {
            scriptingWarnLog("Gradle javaHome is null")
            return null
        }
        return ExternalSystemJdkUtil.lookupJdkByPath(project, this).also {
            scriptingDebugLog { "resolved gradle sdk=$it, javaHome=$this" }
        }
    }

    private fun updateStorage(
        storage: MutableEntityStorage,
        scriptUrl: VirtualFileUrl,
        configurationResult: ScriptCompilationConfigurationResult,
        classpathModel: GradleBuildScriptClasspathModel?
    ) {
        val configurationWrapper = configurationResult.valueOrNull() ?: return
        if (storage.getVirtualFileUrlIndex().findEntitiesByUrl(scriptUrl).filterIsInstance<KotlinScriptEntity>().any()) return

        val classes =
            configurationWrapper.dependenciesClassPath.sorted().map { it.path.toVirtualFileUrl(urlManager) }.toMutableSet()
        val sources = configurationWrapper.dependenciesSources.sorted().map { it.path.toVirtualFileUrl(urlManager) }.toMutableSet()

        val dependencies = buildList {
            addIfNotNull(
                extractRootsByPredicate(storage, scriptUrl, classes, sources) {
                    it.url.contains("kotlin-stdlib")
                })

            addIfNotNull(
                extractRootsByPredicate(storage, scriptUrl, classes, sources) {
                    it.url.contains("accessors")
                })

            addIfNotNull(
                extractRootsByPredicate(storage, scriptUrl, classes, sources) {
                    it.url.contains("kotlin-gradle-plugin")
                })

            if (indexSourceRootsEagerly() || AdvancedSettings.getBoolean("gradle.attach.scripts.dependencies.sources")) {
                addAll(extractDependenciesWithSources(
                    storage = storage,
                    scriptUrl = scriptUrl,
                    classes = classes,
                    sources = sources
                ))

                groupSourcesByParent(sources)

                addAll(
                    classes.map {
                        getOrCreateScriptLibrary(
                            storage = storage,
                            classUrl = it,
                            sources = sources,
                            scriptUrl = scriptUrl
                        )
                    })
            } else {
                addAll(
                    classes.map {
                        getOrCreateScriptLibrary(
                            storage = storage,
                            classUrl = it,
                            scriptUrl = scriptUrl
                        )
                    })
            }
        }

        storage addEntity KotlinScriptEntity(
            scriptUrl, dependencies, KotlinGradleScriptEntitySource
        ) {
            this.configuration = configurationWrapper.configuration?.asEntity()
            this.reports = configurationResult.reports.map(ScriptDiagnostic::map).toMutableList()
            this.sdkId = configurationWrapper.configuration?.sdkId
            this.relatedModuleIds = classpathModel?.let { getRelatedModules(storage, it) }.orEmpty().toMutableList()
        }
    }


    private fun getRelatedModules(storage: MutableEntityStorage, classpathModel: GradleBuildScriptClasspathModel): MutableSet<ModuleId> {

        val virtualFileUrls = classpathModel.classpath.flatMap { it.sources }.mapNotNull {
            it.toVirtualFileUrl(urlManager)
        }.filter { it.virtualFile != null }

        val result = mutableSetOf<ModuleId>()
        for (url in virtualFileUrls) {
            var current: VirtualFileUrl? = url
            while (current != null && current.url != project.basePath) {
                val moduleIds = storage.getVirtualFileUrlIndex().findEntitiesByUrl(current)
                    .filterIsInstance<ContentRootEntity>().map { it.module.symbolicId }.toSet()

                if (result.addAll(moduleIds)) {
                    break
                }

                current = current.parent
            }
        }

        return result
    }

    private fun getOrCreateScriptLibrary(
        storage: MutableEntityStorage,
        classUrl: VirtualFileUrl,
        sources: Collection<VirtualFileUrl> = listOf(),
        scriptUrl: VirtualFileUrl
    ): KotlinScriptLibraryEntityId {
        val id = KotlinScriptLibraryEntityId(classUrl)
        val existingLibrary = storage.resolve(id)

        if (existingLibrary == null) {
            storage addEntity KotlinScriptLibraryEntity(
                classes = id.classes,
                usedInScripts = setOf(scriptUrl),
                entitySource = KotlinGradleScriptEntitySource
            ) {
                this.sources += sources
            }
        } else {
            storage.modifyKotlinScriptLibraryEntity(existingLibrary) {
                this.usedInScripts += scriptUrl
            }
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
    private fun extractDependenciesWithSources(
        storage: MutableEntityStorage,
        scriptUrl: VirtualFileUrl,
        classes: MutableSet<VirtualFileUrl>,
        sources: MutableSet<VirtualFileUrl>
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
            val id = KotlinScriptLibraryEntityId(classUrl)
            val existingLibrary = storage.resolve(id)
            if (existingLibrary == null) {
                storage addEntity KotlinScriptLibraryEntity(
                    classes = id.classes,
                    usedInScripts = setOf(scriptUrl),
                    entitySource = KotlinGradleScriptEntitySource
                ) {
                    this.sources += sourceUrl
                }
            } else {
                storage.modifyKotlinScriptLibraryEntity(existingLibrary) {
                    this.sources += sourceUrl
                    this.usedInScripts += scriptUrl
                }
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

    private fun extractRootsByPredicate(
        storage: MutableEntityStorage,
        scriptUrl: VirtualFileUrl,
        classes: MutableSet<VirtualFileUrl>,
        sources: MutableSet<VirtualFileUrl>,
        predicate: Predicate<VirtualFileUrl>
    ): KotlinScriptLibraryEntityId? {
        val groupedClasses = classes.removeOnMatch(predicate)
        if (groupedClasses.isEmpty()) return null

        val groupedSources = sources.removeOnMatch(predicate)

        val id = KotlinScriptLibraryEntityId(groupedClasses)
        val existingLibrary = storage.resolve(id)

        if (existingLibrary == null) {
            storage addEntity KotlinScriptLibraryEntity(
                classes = groupedClasses,
                usedInScripts = setOf(scriptUrl),
                entitySource = KotlinGradleScriptEntitySource,
            ) {
                this.sources += groupedSources
            }
        } else {
            storage.modifyKotlinScriptLibraryEntity(existingLibrary) {
                this.sources += groupedSources
                this.usedInScripts += scriptUrl
            }
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
    private class GradleWorkspaceModelListener(val project: Project) : WorkspaceModelChangeListener {
        override fun changed(event: VersionedStorageChange) {
            val definitionChanges = event.getChanges(GradleScriptDefinitionEntity::class.java)

            if (definitionChanges.any()) {
                ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): GradleKotlinScriptEntityProvider = project.service()
    }
}
