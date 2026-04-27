// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalBuildToolsApi::class)

package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.toNioPathOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.jetbrains.kotlin.idea.base.util.isGradleModule
import org.jetbrains.kotlin.idea.base.util.isMavenModule
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

internal fun Project.getCriPaths(): Collection<Path> = ModuleManager.getInstance(this).modules
    .flatMapTo(mutableSetOf(), Module::getCriPaths)

internal fun Module.getCriPaths(): Collection<Path> {
    val modulePath = getPath()?.toNioPathOrNull() ?: return emptyList()
    return when {
        isGradleModule -> getGradleCriPaths(modulePath)
        isMavenModule -> listOfNotNull(getMavenCriPath(modulePath))
        else -> emptyList()
    }
}

/**
 * Resolves the file system path for a module.
 * For Gradle modules, uses [ExternalSystemApiUtil.getExternalProjectPath].
 * For Maven modules, falls back to the first content root.
 */
private fun Module.getPath(): String? = runReadActionBlocking {
    ExternalSystemApiUtil.getExternalProjectPath(this)
        ?: ModuleRootManager.getInstance(this).contentRoots.firstOrNull()?.path
}

@ApiStatus.Internal
fun getGradleCriPaths(modulePath: Path): Collection<Path> {
    val kotlinBuildDirectory = modulePath / "build" / "kotlin"
    if (!kotlinBuildDirectory.exists() || !kotlinBuildDirectory.isDirectory()) return emptyList()

    // Gradle writes CRI into a separate build/kotlin/<compile task>/cacheable/cri directory per compilation task
    val entries = try {
        kotlinBuildDirectory.listDirectoryEntries()
    } catch (_: IOException) {
        return emptyList()
    }
    return entries
        .asSequence()
        .map { it / "cacheable" / CriToolchain.DATA_PATH }
        .filter { it.exists() }
        .toSet()
}

@ApiStatus.Internal
fun getMavenCriPath(modulePath: Path): Path? {
    val criPath = modulePath / "target" / "kotlin-ic" / "compile" / CriToolchain.DATA_PATH
    return criPath.takeIf { it.exists() }
}
