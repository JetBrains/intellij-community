// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalBuildToolsApi::class)

package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.jetbrains.kotlin.idea.base.util.isGradleModule
import org.jetbrains.kotlin.idea.base.util.isMavenModule
import java.nio.file.Path
import kotlin.io.path.div

internal fun Module.getCriPath(): Path? {
    val modulePath = Path.of(getPath() ?: return null)
    return when {
        isGradleModule -> getGradleCriPath(modulePath)
        isMavenModule -> getMavenCriPath(modulePath)
        else -> null
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

private fun getGradleCriPath(modulePath: Path) = modulePath / "build" / "kotlin" / "compileKotlin" / "cacheable" / CriToolchain.DATA_PATH

private fun getMavenCriPath(modulePath: Path) = modulePath / "target" / "kotlin-ic" / "compile" / CriToolchain.DATA_PATH
