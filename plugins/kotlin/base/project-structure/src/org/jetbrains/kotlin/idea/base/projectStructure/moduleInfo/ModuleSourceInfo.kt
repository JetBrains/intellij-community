// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.serviceContainer.AlreadyDisposedException
import org.jetbrains.kotlin.analyzer.ModuleSourceInfoBase
import org.jetbrains.kotlin.analyzer.TrackableModuleInfo
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinModificationTrackerProvider
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.compat.toOldPlatform
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.idea.caches.project.ModuleSourceInfo as OldModuleSourceInfo

interface ModuleSourceInfo : OldModuleSourceInfo, IdeaModuleInfo, TrackableModuleInfo, ModuleSourceInfoBase {
    override val module: Module

    override val expectedBy: List<ModuleSourceInfo>

    override val displayedName get() = module.name

    override val moduleOrigin: ModuleOrigin
        get() = ModuleOrigin.MODULE

    override val project: Project
        get() = module.project

    override val platform: TargetPlatform
        get() = module.platform

    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        message = "This accessor is deprecated and will be removed soon, use API from 'org.jetbrains.kotlin.platform.*' packages instead",
        replaceWith = ReplaceWith("platform"),
        level = DeprecationLevel.ERROR
    )
    fun getPlatform(): org.jetbrains.kotlin.resolve.TargetPlatform = platform.toOldPlatform()

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = platform.findAnalyzerServices(module.project)

    override fun createModificationTracker(): ModificationTracker {
        return KotlinModificationTrackerProvider.getInstance(module.project).createModuleModificationTracker(module)
    }

    override fun checkValidity() {
        module.checkValidity()
    }
}

fun Module.checkValidity() {
    if (isDisposed) {
        throw AlreadyDisposedException("Module '${name}' is already disposed")
    }
}