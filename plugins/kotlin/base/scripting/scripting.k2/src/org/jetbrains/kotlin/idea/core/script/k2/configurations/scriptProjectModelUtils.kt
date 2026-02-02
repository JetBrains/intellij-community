// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.io.URLUtil
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDependency
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.jvm.JvmDependency

/**
 * Generates a sequence of script library entities based on the provided script compilation configuration,
 * script definition, and project. The method determines dependencies and resolves them into a sequence of
 * library entities and associated source files URLs.
 *
 * @param configuration the script compilation configuration containing dependency information.
 * @param definition the script definition providing additional compilation configuration details.
 * @param project the current project within which the script library entities are generated.
 * @return a sequence of pairs, where each pair contains a KotlinScriptLibraryEntityId and a list of associated
 *         source files URLs for the library entity.
 */
fun generateScriptLibraryEntities(
    project: Project,
    configuration: ScriptCompilationConfiguration,
    definition: ScriptDefinition
): Sequence<Pair<KotlinScriptLibraryEntityId, List<VirtualFileUrl>>> = sequence {
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    val (classes, sources) = configuration.getDependencyUrls(virtualFileUrlManager)

    val isUberDependencyAllowed = classes.size + sources.size <= 20
    if (isUberDependencyAllowed) {
        yield(KotlinScriptLibraryEntityId(classes) to sources)
    } else {
        val (definitionClasses, definitionSources) = definition.compilationConfiguration.getDependencyUrls(virtualFileUrlManager)
        yield(KotlinScriptLibraryEntityId(definitionClasses) to definitionSources)

        val definitionLibraryRoots = definitionClasses.toSet()

        for (url in classes) {
            if (definitionLibraryRoots.contains(url)) continue

            yield(KotlinScriptLibraryEntityId(url) to listOf())
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

private fun Iterable<ScriptDependency>.toVirtualFileUrls(urlManager: VirtualFileUrlManager): List<VirtualFileUrl> =
    asSequence().filterIsInstance<JvmDependency>().flatMap { it.classpath }.map { it.path }.sorted().map { it.toVirtualFileUrl(urlManager) }
        .toList()

fun ScriptCompilationConfiguration.getDependencyUrls(manager: VirtualFileUrlManager): Pair<List<VirtualFileUrl>, List<VirtualFileUrl>> =
    get(ScriptCompilationConfiguration.dependencies).orEmpty()
        .toVirtualFileUrls(manager) to get(ScriptCompilationConfiguration.ide.dependenciesSources).orEmpty().toVirtualFileUrls(manager)

internal fun EntityStorage.containsScriptEntity(url: VirtualFileUrl) = getVirtualFileUrlIndex()
    .findEntitiesByUrl(url)
    .filterIsInstance<KotlinScriptEntity>().any()