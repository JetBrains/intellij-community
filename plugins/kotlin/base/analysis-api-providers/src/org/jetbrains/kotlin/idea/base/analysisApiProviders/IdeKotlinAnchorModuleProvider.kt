// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiProviders

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.project.structure.KtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.providers.KotlinAnchorModuleProvider
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.ResolutionAnchorCacheService
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.ResolutionAnchorCacheService.Companion.getInstance
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule
import org.jetbrains.kotlin.idea.base.util.Frontend10ApiUsage

class IdeKotlinAnchorModuleProvider(val project: Project) : KotlinAnchorModuleProvider {
    override fun getAnchorModule(libraryModule: KtLibraryModule): KtSourceModule? {
        @OptIn(Frontend10ApiUsage::class)
        return getInstance(project).resolutionAnchorsForLibraries[libraryModule.moduleInfo as LibraryInfo]?.toKtModule() as? KtSourceModule
    }
}