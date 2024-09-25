// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinAnchorModuleProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.analysisApiPlatform.IdeKotlinAnchorModuleProvider
import org.jetbrains.kotlin.idea.base.analysisApiPlatform.IdeKotlinModuleDependentsProvider

internal class K2IdeKotlinModuleDependentsProvider(project: Project) : IdeKotlinModuleDependentsProvider(project) {
    override fun addAnchorModuleDependents(
        module: KaSourceModule,
        to: MutableSet<KaModule>
    ) {
        val kotlinAnchorModuleProvider = KotlinAnchorModuleProvider.getInstance(project) as IdeKotlinAnchorModuleProvider
        val anchorLibraries = kotlinAnchorModuleProvider.getAnchorLibraries(module).ifEmpty { return }
        to.addAll(anchorLibraries)
    }
}
