// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.application.readAction
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
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.gradle.scripting.k2.definition.withIdeKeys
import org.jetbrains.kotlin.gradle.scripting.k2.importing.GradleScriptData
import org.jetbrains.kotlin.gradle.scripting.k2.importing.GradleScriptModel
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.asEntity
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.deserialize
import org.jetbrains.kotlin.gradle.scripting.shared.KotlinGradleScriptEntitySource
import org.jetbrains.kotlin.gradle.scripting.shared.definition.GradleScriptDefinition
import org.jetbrains.kotlin.gradle.scripting.shared.loadGradleDefinitions
import org.jetbrains.kotlin.idea.core.script.k2.configurations.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.idea.core.script.k2.configurations.toVirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.core.script.k2.highlighting.DefaultScriptResolutionStrategy
import org.jetbrains.kotlin.idea.core.script.k2.modules.*
import org.jetbrains.kotlin.idea.core.script.v1.indexSourceRootsEagerly
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.v1.scriptingWarnLog
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.adjustByDefinition
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlin.utils.addIfNotNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

@Service(Service.Level.PROJECT)
class GradleKotlinScriptService(val project: Project) : ScriptRefinedConfigurationResolver, ScriptWorkspaceModelManager {
    private val data = ConcurrentHashMap<VirtualFileUrl, ScriptConfigurationWithSdk>()

    override suspend fun create(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptConfigurationWithSdk? = null

    override fun get(virtualFile: VirtualFile): ScriptConfigurationWithSdk? {
        val url = virtualFile.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
        val result = data.computeIfAbsent(url) { url ->
            val entity = project.workspaceModel.currentSnapshot.getVirtualFileUrlIndex().findEntitiesByUrl(url)
                .firstOrNull {
                    it.entitySource is KotlinGradleScriptEntitySource && it is KotlinScriptEntity
                } as? KotlinScriptEntity

            entity?.toConfigurationWithSdk() ?: ScriptConfigurationWithSdk.EMPTY
        }

        return if (result == ScriptConfigurationWithSdk.EMPTY) null else result
    }

    fun processScripts(
        scriptData: GradleScriptData,
        storageToUpdate: MutableEntityStorage
    ) {
        val updatedStorage = MutableEntityStorage.create()

        val definitions = loadGradleDefinitions(scriptData.definitionsParams).map { it.withIdeKeys(project) }
        updatedStorage.enrichWithDefinitions(definitions)
        updatedStorage.enrichWithConfigurations(scriptData, definitions)

        storageToUpdate.applyChangesFrom(updatedStorage)
    }

    private fun MutableEntityStorage.enrichWithConfigurations(
        scriptsData: GradleScriptData,
        definitions: List<GradleScriptDefinition>
    ) {
        val sdk = scriptsData.definitionsParams.javaHome.resolveSdk()
        val javaHomePath = sdk?.homePath?.let { File(it) }

        val configurations = scriptsData.models.associate { gradleScript: GradleScriptModel ->
            val sourceCode = VirtualFileScriptSource(gradleScript.virtualFile)
            val definition = definitions.firstOrNull { it.isScript(sourceCode) } ?: findScriptDefinition(project, sourceCode)

            val configuration = definition.compilationConfiguration.with {
                if (javaHomePath != null) {
                    jvm.jdkHome(javaHomePath)
                }
                defaultImports(gradleScript.imports)
                dependencies(JvmDependency(gradleScript.classPath.map { File(it) }))
                ide.dependenciesSources(JvmDependency(gradleScript.sourcePath.map { File(it) }))
            }.adjustByDefinition(definition)

            val updatedConfiguration = refineScriptCompilationConfiguration(sourceCode, definition, project, configuration)

            gradleScript.virtualFile to ScriptConfigurationWithSdk(updatedConfiguration, sdk?.let { SdkId(it.name, it.sdkType.name) })
        }

        enrichStorage(configurations)
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

            this addEntity KotlinScriptEntity(
                scriptUrl,
                dependencies,
                KotlinGradleScriptEntitySource
            ) {
                this.configuration = configuration.configuration?.asEntity()
                this.reports = configurationWithSdk.scriptConfiguration.reports.map { it.toData() }.toMutableList()
                this.sdkId = configurationWithSdk.sdkId
            }
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

    private fun MutableEntityStorage.enrichWithDefinitions(definitions: List<GradleScriptDefinition>) {
        definitions.forEach {
            this addEntity GradleScriptDefinitionEntity(
                it.definitionId,
                it.compilationConfiguration.asEntity(),
                it.hostConfiguration.asEntity(),
                KotlinGradleScriptEntitySource
            ) {
                evaluationConfiguration = it.evaluationConfiguration?.asEntity()
            }
        }
    }

    @Suppress("unused")
    private class GradleWorkspaceModelListener(val project: Project, val scope: CoroutineScope) : WorkspaceModelChangeListener {
        override fun changed(event: VersionedStorageChange) = scope.launchTracked {

            if (event.getChanges(GradleScriptDefinitionEntity::class.java).any()) {
                ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
            }

            val updatedData = mutableMapOf<VirtualFileUrl, ScriptConfigurationWithSdk>()
            for (entityChange in event.getChanges(KotlinScriptEntity::class.java)) {
                if (entityChange is EntityChange.Removed) continue
                val entity = entityChange.newEntity ?: continue
                if (entity.entitySource !is KotlinGradleScriptEntitySource) continue

                val result = entity.toConfigurationWithSdk()
                if (result != null) {
                    updatedData[entity.virtualFileUrl] = result
                }
            }

            if (updatedData.any()) {
                val ktFiles = buildSet {
                    for (url in updatedData.keys) {
                        val virtualFile = url.virtualFile ?: continue
                        val ktFile = readAction { PsiManager.getInstance(project).findFile(virtualFile) } as? KtFile
                        addIfNotNull(ktFile)
                    }
                }

                getInstance(project).data.clear()
                if (ktFiles.any()) {
                    DefaultScriptResolutionStrategy.getInstance(project).restartHighlighting(ktFiles)
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): GradleKotlinScriptService = project.service()
    }
}


fun KotlinScriptEntity.toConfigurationWithSdk(): ScriptConfigurationWithSdk? {
    val virtualFile = virtualFileUrl.virtualFile ?: return null

    val result = if (configuration == null) {
        ResultWithDiagnostics.Failure(listOf())
    } else {
        ResultWithDiagnostics.Success<ScriptCompilationConfigurationWrapper>(
            ScriptCompilationConfigurationWrapper.FromCompilationConfiguration(
                VirtualFileScriptSource(virtualFile),
                configuration?.deserialize()
            ), reports.map { report -> report.toScriptDiagnostic() }
        )
    }

    return ScriptConfigurationWithSdk(result, sdkId)
}
