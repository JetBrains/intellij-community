// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.KotlinScriptLibraryEntityId
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDependency
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.util.PropertiesCollection

fun generateScriptLibraryEntities(
    configuration: ScriptCompilationConfiguration,
    definition: ScriptDefinition,
    project: Project
): Sequence<KotlinScriptLibraryEntityId> = sequence {
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    val classes = configuration.getOrEmpty(ScriptCompilationConfiguration.dependencies)
        .toVirtualFileUrls(virtualFileUrlManager)
        .toList()

    if (configuration.isUberDependencyAllowed()) {
        val sources = configuration.getOrEmpty(ScriptCompilationConfiguration.ide.dependenciesSources)
            .toVirtualFileUrls(virtualFileUrlManager)
            .toList()

        yield(KotlinScriptLibraryEntityId(classes, sources))
    } else {
        val definitionLibraryId = createDefinitionLibraryId(definition, project)
        yield(definitionLibraryId)

        val definitionLibraryRoots = definitionLibraryId.classes.toSet()

        for (url in classes) {
            if (definitionLibraryRoots.contains(url)) continue

            yield(KotlinScriptLibraryEntityId(url))
        }
    }
}

fun String.toVirtualFileUrl(fileUrlManager: VirtualFileUrlManager): VirtualFileUrl {
    val url = when {
        this.endsWith(".jar") -> URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR + this + URLUtil.JAR_SEPARATOR
        else -> URLUtil.FILE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + FileUtil.toSystemIndependentName(this)
    }

    return fileUrlManager.getOrCreateFromUrl(url)
}

private fun ScriptCompilationConfiguration.isUberDependencyAllowed(): Boolean {
    return getOrEmpty(ScriptCompilationConfiguration.dependencies).size + getOrEmpty(ScriptCompilationConfiguration.ide.dependenciesSources).size < 20
}

@ApiStatus.Internal
private fun createDefinitionLibraryId(definition: ScriptDefinition, project: Project): KotlinScriptLibraryEntityId {
    val urlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

    val classes = definition.compilationConfiguration[ScriptCompilationConfiguration.dependencies].orEmpty()
        .toVirtualFileUrls(urlManager)
        .toList()

    val sources = definition.compilationConfiguration[ScriptCompilationConfiguration.ide.dependenciesSources].orEmpty()
        .toVirtualFileUrls(urlManager)
        .toList()

    return KotlinScriptLibraryEntityId(classes, sources)
}

private fun Iterable<ScriptDependency>.toVirtualFileUrls(urlManager: VirtualFileUrlManager): Sequence<VirtualFileUrl> = asSequence()
    .filterIsInstance<JvmDependency>()
    .flatMap { it.classpath }
    .map { it.path }
    .sorted()
    .map { it.toVirtualFileUrl(urlManager) }

private fun <T> PropertiesCollection.getOrEmpty(key: PropertiesCollection.Key<List<T>>): List<T> = get(key).orEmpty()