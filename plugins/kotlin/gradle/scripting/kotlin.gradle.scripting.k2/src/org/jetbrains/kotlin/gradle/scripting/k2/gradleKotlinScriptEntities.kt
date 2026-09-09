// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.PathPrefixTree
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.psi.PsiManager
import com.intellij.util.containers.prefixTree.map.MutablePrefixTreeMap
import com.intellij.workspaceModel.ide.toPath
import org.jetbrains.kotlin.gradle.scripting.k2.importing.GradleScriptModel
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleKotlinScriptEntitySource
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity
import org.jetbrains.kotlin.gradle.scripting.shared.definition.GradleScriptDefinition
import org.jetbrains.kotlin.idea.core.script.k2.asEntity
import org.jetbrains.kotlin.idea.core.script.k2.configurations.sdkId
import org.jetbrains.kotlin.idea.core.script.k2.configurations.toVirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.k2.getOrCreateScriptConfigurationId
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId
import org.jetbrains.kotlin.idea.core.script.k2.modules.map
import org.jetbrains.kotlin.idea.core.script.k2.modules.modifyKotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.v1.indexSourceRootsEagerly
import org.jetbrains.kotlin.idea.core.script.v1.scriptingInfoLog
import org.jetbrains.kotlin.idea.core.script.v1.scriptingWarnLog
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.adjustByDefinition
import org.jetbrains.kotlin.scripting.resolve.getScriptCollectedData
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlin.utils.addIfNotNull
import java.io.File
import java.nio.file.Path
import java.nio.file.Path.of
import kotlin.script.experimental.api.ScriptCompilationConfiguration
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

private const val JAR = ".jar!/"
private const val SOURCES_JAR = "-sources.jar!/"
private val GROUPED_LIBRARY_MARKERS = listOf("kotlin-stdlib", "accessors", "kotlin-gradle-plugin")

internal fun addDefinitions(
    storage: MutableEntityStorage,
    entitySource: GradleKotlinScriptEntitySource,
    definitions: Collection<GradleScriptDefinition>,
) {
    for (definition in definitions) {
        storage addEntity GradleScriptDefinitionEntity(
            definition.definitionId,
            definition.compilationConfiguration.asEntity(),
            definition.hostConfiguration.asEntity(),
            entitySource
        ) {
            evaluationConfiguration = definition.evaluationConfiguration?.asEntity()
        }
    }
}

/** Adds one [KotlinScriptEntity] with its libraries per model that matches a definition and is not in the storage yet. */
internal suspend fun addScripts(
    project: Project,
    storage: MutableEntityStorage,
    entitySource: GradleKotlinScriptEntitySource,
    models: Collection<GradleScriptModel>,
    definitions: Collection<GradleScriptDefinition>,
    javaHome: String,
) {
    val urlManager = project.workspaceModel.getVirtualFileUrlManager()
    val contentRootIndex = PathPrefixTree.createMap<ModuleId>()
    for (contentRoot in storage.entities(ContentRootEntity::class.java)) {
        contentRootIndex.put(contentRoot.url.toPath(), contentRoot.module.symbolicId)
    }
    val sourceRootIndex = PathPrefixTree.createMap<ModuleId>()
    for (sourceRoot in storage.entities(SourceRootEntity::class.java)) {
        sourceRootIndex.put(sourceRoot.url.toPath(), sourceRoot.contentRoot.module.symbolicId)
    }
    val attachSources = indexSourceRootsEagerly() || AdvancedSettings.getBoolean("gradle.attach.scripts.dependencies.sources")
    val libraries = ScriptLibraries(storage, entitySource, urlManager, attachSources)

    for (model in models) {
        val sourceCode = VirtualFileScriptSource(model.virtualFile)
        val definition = definitions.firstOrNull { it.isScript(sourceCode) } ?: continue
        val scriptUrl = model.virtualFile.toVirtualFileUrl(urlManager)
        if (storage.getVirtualFileUrlIndex().findEntitiesByUrl(scriptUrl).any { it is KotlinScriptEntity }) continue

        val result = resolveConfiguration(project, model, sourceCode, definition, javaHome) ?: continue
        val configurationWrapper = result.valueOrNull() ?: continue

        val dependencies = libraries.register(scriptUrl, configurationWrapper.dependenciesClassPath, configurationWrapper.dependenciesSources)
        storage addEntity KotlinScriptEntity(scriptUrl, dependencies, entitySource) {
            this.configurationId = configurationWrapper.configuration?.getOrCreateScriptConfigurationId(storage, entitySource)
            this.reports = result.reports.map(ScriptDiagnostic::map).toMutableList()
            this.sdkId = configurationWrapper.configuration?.sdkId
            this.relatedModuleIds = getRelatedModules(model, sourceRootIndex, contentRootIndex).toMutableList()
        }
    }
}

private fun getRelatedModules(
    model: GradleScriptModel,
    sourceRootIndex: MutablePrefixTreeMap<Path, ModuleId>,
    contentRootIndex: MutablePrefixTreeMap<Path, ModuleId>
): Iterable<ModuleId> {
    return buildSet {
        addIfNotNull(sourceRootIndex.getAncestorValues(model.virtualFile.toNioPath()).lastOrNull())
        
        if (model.classpathModel != null) {
            addAll(
                model.classpathModel.classpath.asSequence()
                    .flatMap { it.sources }
                    .mapNotNullTo(mutableSetOf()) { contentRootIndex.getAncestorValues(of(it)).lastOrNull() })
        }
    }
}

private suspend fun resolveConfiguration(
    project: Project,
    model: GradleScriptModel,
    sourceCode: VirtualFileScriptSource,
    definition: GradleScriptDefinition,
    javaHome: String,
): ScriptCompilationConfigurationResult? {
    val configuration = definition.compilationConfiguration.with {
        withResolvedJdk(project, javaHome)
        defaultImports(model.imports)
        dependencies(JvmDependency(model.classPath.map(::File)))
        ide.dependenciesSources(JvmDependency(model.sourcePath.map(::File)))
    }.adjustByDefinition(definition)

    val collectedData = readAction {
        val ktFile = PsiManager.getInstance(project).findFile(sourceCode.virtualFile) as? KtFile
        if (ktFile == null) {
            scriptingWarnLog("Unable to load PSI from ${sourceCode.virtualFile.path}")
        }

        ktFile?.let { getScriptCollectedData(it, configuration, definition.contextClassLoader) }
    } ?: return null

    return refineScriptCompilationConfiguration(
        compilationConfiguration = configuration,
        sourceCode = sourceCode,
        collectedData = collectedData,
        knownVirtualFileSources = null,
        definition = definition
    )
}

/** Registers the JDK at [javaHome] in the SDK table, where [sdkId] resolves it later, and sets it as the script JDK. */
private fun ScriptCompilationConfiguration.Builder.withResolvedJdk(project: Project, javaHome: String) {
    ExternalSystemJdkUtil.lookupJdkByPath(project, javaHome)
    jvm.jdkHome(File(javaHome))
    scriptingInfoLog("resolved gradle javaHome=$javaHome")
}

/** Registers the library entities of one script and returns their ids. Equal class roots of one project share one entity. */
internal class ScriptLibraries(
    private val storage: MutableEntityStorage,
    private val entitySource: GradleKotlinScriptEntitySource,
    private val urlManager: VirtualFileUrlManager,
    private val attachSources: Boolean,
) {
    private val scope = "Gradle (${entitySource.projectPath})"

    fun register(scriptUrl: VirtualFileUrl, classPath: List<File>, sourcePath: List<File>): List<KotlinScriptLibraryEntityId> {
        val classes = classPath.sorted().mapTo(mutableSetOf()) { it.path.toVirtualFileUrl(urlManager) }
        val sources = sourcePath.sorted().mapTo(mutableSetOf()) { it.path.toVirtualFileUrl(urlManager) }

        return buildList {
            for (marker in GROUPED_LIBRARY_MARKERS) {
                val groupedClasses = classes.extract { marker in it.url }
                if (groupedClasses.isEmpty()) continue
                add(addLibrary(scriptUrl, groupedClasses, sources.extract { marker in it.url }))
            }

            if (attachSources) {
                val sourceJarsByName = sources.filter { it.url.endsWith(SOURCES_JAR) }
                    .associateBy { it.url.removeSuffix(SOURCES_JAR).substringAfterLast('/') }
                for (classUrl in classes.filter { it.url.endsWith(JAR) }) {
                    val sourceUrl = sourceJarsByName[classUrl.url.removeSuffix(JAR).substringAfterLast('/')] ?: continue
                    if (sourceUrl == classUrl) continue
                    classes.remove(classUrl)
                    sources.remove(sourceUrl)
                    add(addLibrary(scriptUrl, listOf(classUrl), listOf(sourceUrl)))
                }
            }

            val sourceRoots = if (attachSources) sources.mapTo(mutableSetOf()) { it.parent ?: it } else emptySet()
            for (classUrl in classes) {
                add(addLibrary(scriptUrl, listOf(classUrl), sourceRoots))
            }
        }
    }

    private fun addLibrary(scriptUrl: VirtualFileUrl, classes: List<VirtualFileUrl>, sources: Collection<VirtualFileUrl>): KotlinScriptLibraryEntityId {
        val id = KotlinScriptLibraryEntityId(scope, classes)
        val existing = storage.resolve(id)
        if (existing == null) {
            storage addEntity KotlinScriptLibraryEntity(scope = scope, classes = classes, usedInScripts = setOf(scriptUrl), entitySource = entitySource) {
                this.sources += sources
            }
        } else {
            storage.modifyKotlinScriptLibraryEntity(existing) {
                this.sources += sources
                this.usedInScripts += scriptUrl
            }
        }
        return id
    }

    private fun <T> MutableCollection<T>.extract(predicate: (T) -> Boolean): List<T> = filter(predicate).also { removeAll(it) }
}
