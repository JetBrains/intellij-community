// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TargetPlatformDetectorUtils")
package org.jetbrains.kotlin.idea.base.facet.platform

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.base.platforms.forcedTargetPlatform
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.project.ModulePlatformCache
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.analysisContext

val KtElement.explicitPlatform: TargetPlatform?
    get() {
        return calculateTargetPlatform(containingKtFile)
    }

val KtElement.platform: TargetPlatform
    get() = explicitPlatform ?: JvmPlatforms.defaultJvmPlatform

// FIXME(dsavvinov): this logic is clearly wrong in MPP environment; review and fix
val Project.platform: TargetPlatform?
    get() {
        val jvmTarget = Kotlin2JvmCompilerArgumentsHolder.getInstance(this).settings.jvmTarget ?: return null
        val version = JvmTarget.fromString(jvmTarget) ?: return null
        return JvmPlatforms.jvmPlatformByTargetVersion(version)
    }

val Module.platform: TargetPlatform
    get() = runReadAction { ModulePlatformCache.getInstance(project)[this] }

interface TargetPlatformDetector {
    companion object {
        val EP_NAME: ExtensionPointName<TargetPlatformDetector> =
            ExtensionPointName.create("org.jetbrains.kotlin.idea.base.platforms.targetPlatformDetector")
    }

    fun detectPlatform(file: KtFile): TargetPlatform?
}

private fun calculateTargetPlatform(file: KtFile): TargetPlatform? {
    val forcedPlatform = file.forcedTargetPlatform
    if (forcedPlatform != null) {
        return forcedPlatform
    }

    val context = file.analysisContext
    if (context != null) {
        return when (val contextFile = context.containingFile) {
            is KtFile -> return calculateTargetPlatform(contextFile)
            else -> JvmPlatforms.unspecifiedJvmPlatform
        }
    }

    val extensions = file.project.extensionArea.getExtensionPoint(TargetPlatformDetector.EP_NAME).extensions
    for (extension in extensions) {
        val platform = extension.detectPlatform(file)
        if (platform != null) {
            return platform
        }
    }

    val virtualFile = file.originalFile.virtualFile
    if (virtualFile != null) {
        val moduleForFile = ProjectFileIndex.getInstance(file.project).getModuleForFile(virtualFile)
        if (moduleForFile != null) {
            return moduleForFile.platform
        }
    }

    return JvmPlatforms.defaultJvmPlatform
}