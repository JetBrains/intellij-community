// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.kotlin.gradle.scripting.k2.importing.GradleScriptModel
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleKotlinScriptEntitySource
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity
import org.jetbrains.kotlin.gradle.scripting.shared.definition.GradleScriptDefinition
import org.jetbrains.kotlin.idea.core.script.k2.asEntity
import org.jetbrains.kotlin.idea.core.script.k2.configurations.sdkId
import org.jetbrains.kotlin.idea.core.script.k2.configurations.toVirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.core.script.k2.getOrCreateScriptConfigurationId
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId
import org.jetbrains.kotlin.idea.core.script.k2.modules.map
import org.jetbrains.kotlin.idea.core.script.k2.modules.modifyKotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.shared.smartRefineScriptCompilationConfiguration
import org.jetbrains.kotlin.idea.core.script.v1.indexSourceRootsEagerly
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.v1.scriptingWarnLog
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.adjustByDefinition
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel
import java.io.File
import java.util.function.Predicate
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

@Service(Service.Level.PROJECT)
class GradleKotlinScriptEntityProvider(val project: Project) {
    private val urlManager: VirtualFileUrlManager
        get() = project.workspaceModel.getVirtualFileUrlManager()

    suspend fun getUpdatedStorage(
        storage: MutableEntityStorage,
        entitySource: GradleKotlinScriptEntitySource,
        models: Collection<GradleScriptModel>,
        definitions: Collection<GradleScriptDefinition>,
        javaHome: String? = null
    ): ImmutableEntityStorage {
        definitions.forEach {
            storage addEntity GradleScriptDefinitionEntity(
                it.definitionId,
                it.compilationConfiguration.asEntity(),
                it.hostConfiguration.asEntity(),
                entitySource
            ) {
                evaluationConfiguration = it.evaluationConfiguration?.asEntity()
            }
        }

        val javaHomePath = javaHome.resolveSdk()?.homePath

        for (model in models) {
            val sourceCode = VirtualFileScriptSource(model.virtualFile)
            val definition = definitions.firstOrNull { it.isScript(sourceCode) } ?: continue

            val configuration = definition.compilationConfiguration.with {
                if (javaHomePath != null) {
                    jvm.jdkHome(File(javaHomePath))
                }
                defaultImports(model.imports)
                dependencies(JvmDependency(model.classPath.map { File(it) }))
                ide.dependenciesSources(JvmDependency(model.sourcePath.map { File(it) }))
            }.adjustByDefinition(definition)

            val configurationResult = smartRefineScriptCompilationConfiguration(sourceCode, definition, project, configuration)
            updateStorage(storage, entitySource, model.virtualFile.toVirtualFileUrl(urlManager), configurationResult, model.classpathModel)
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
        entitySource: GradleKotlinScriptEntitySource,
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
                extractRootsByPredicate(storage, entitySource, scriptUrl, classes, sources) {
                    it.url.contains("kotlin-stdlib")
                })

            addIfNotNull(
                extractRootsByPredicate(storage, entitySource, scriptUrl, classes, sources) {
                    it.url.contains("accessors")
                })

            addIfNotNull(
                extractRootsByPredicate(storage, entitySource, scriptUrl, classes, sources) {
                    it.url.contains("kotlin-gradle-plugin")
                })

            if (indexSourceRootsEagerly() || AdvancedSettings.getBoolean("gradle.attach.scripts.dependencies.sources")) {
                addAll(
                    extractDependenciesWithSources(
                        storage = storage,
                        entitySource = entitySource,
                        scriptUrl = scriptUrl,
                        classes = classes,
                        sources = sources
                    )
                )

                groupSourcesByParent(sources)

                addAll(
                    classes.map {
                        getOrCreateScriptLibrary(
                            storage = storage,
                            entitySource = entitySource,
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
                            entitySource = entitySource,
                            classUrl = it,
                            scriptUrl = scriptUrl
                        )
                    })
            }
        }

        storage addEntity KotlinScriptEntity(
            scriptUrl, dependencies, entitySource
        ) {
            this.configurationId = configurationWrapper.configuration?.getOrCreateScriptConfigurationId(storage, entitySource)
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
        entitySource: GradleKotlinScriptEntitySource,
        classUrl: VirtualFileUrl,
        sources: Collection<VirtualFileUrl> = listOf(),
        scriptUrl: VirtualFileUrl
    ): KotlinScriptLibraryEntityId {
        val libraryClasses = listOf(classUrl)
        val libraryScope = entitySource.kotlinScriptLibraryScope()
        val id = KotlinScriptLibraryEntityId(libraryScope, libraryClasses)
        val existingLibrary = storage.resolve(id)

        if (existingLibrary == null) {
            storage addEntity KotlinScriptLibraryEntity(
                scope = libraryScope,
                classes = libraryClasses,
                usedInScripts = setOf(scriptUrl),
                entitySource = entitySource
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
        entitySource: GradleKotlinScriptEntitySource,
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
            val libraryClasses = listOf(classUrl)
            val libraryScope = entitySource.kotlinScriptLibraryScope()
            val id = KotlinScriptLibraryEntityId(libraryScope, libraryClasses)
            val existingLibrary = storage.resolve(id)
            if (existingLibrary == null) {
                storage addEntity KotlinScriptLibraryEntity(
                    scope = libraryScope,
                    classes = libraryClasses,
                    usedInScripts = setOf(scriptUrl),
                    entitySource = entitySource
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
        entitySource: GradleKotlinScriptEntitySource,
        scriptUrl: VirtualFileUrl,
        classes: MutableSet<VirtualFileUrl>,
        sources: MutableSet<VirtualFileUrl>,
        predicate: Predicate<VirtualFileUrl>
    ): KotlinScriptLibraryEntityId? {
        val groupedClasses = classes.removeOnMatch(predicate)
        if (groupedClasses.isEmpty()) return null

        val groupedSources = sources.removeOnMatch(predicate)

        val libraryClasses = groupedClasses.toList()
        val libraryScope = entitySource.kotlinScriptLibraryScope()
        val id = KotlinScriptLibraryEntityId(libraryScope, libraryClasses)
        val existingLibrary = storage.resolve(id)

        if (existingLibrary == null) {
            storage addEntity KotlinScriptLibraryEntity(
                scope = libraryScope,
                classes = libraryClasses,
                usedInScripts = setOf(scriptUrl),
                entitySource = entitySource,
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

    private fun GradleKotlinScriptEntitySource.kotlinScriptLibraryScope(): String = "Gradle ($projectPath)"

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
