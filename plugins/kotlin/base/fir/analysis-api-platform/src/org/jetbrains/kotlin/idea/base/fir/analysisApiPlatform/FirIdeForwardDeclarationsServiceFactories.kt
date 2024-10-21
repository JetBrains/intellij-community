// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProvider
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinForwardDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.declarations.createDeclarationProvider
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinForwardDeclarationsPackageProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProvider
import org.jetbrains.kotlin.analysis.api.platform.packages.createPackageProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.projectStructure.KtNativeKlibLibraryModuleByModuleInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi

/**
 * FIR IDE declaration provider factory implementation for Kotlin/Native forward declarations.
 * Delegates to the regular factory, but uses a narrow [GlobalSearchScope] that contains only generated declarations of the [KaModule].
 *
 * @see [org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.KotlinForwardDeclarationsFileGenerator]
 */
internal class FirIdeForwardDeclarationProviderFactory : KotlinForwardDeclarationProviderFactory {
    @OptIn(K1ModeProjectStructureApi::class)
    override fun createDeclarationProvider(module: KaModule): KotlinDeclarationProvider? {
        if (module !is KtNativeKlibLibraryModuleByModuleInfo) return null

        return module.project.createDeclarationProvider(module.forwardDeclarationsScope, module)
    }
}

/**
 * FIR IDE package provider factory for Kotlin/Native forward declarations.
 * Delegates to the regular factory, but uses a narrow [GlobalSearchScope] that contains only generated declarations of the [KaModule].
 *
 * @see [org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.KotlinForwardDeclarationsFileGenerator]
 */
internal class FirIdeForwardDeclarationPackageProviderFactory : KotlinForwardDeclarationsPackageProviderFactory {
    @OptIn(K1ModeProjectStructureApi::class)
    override fun createPackageProvider(module: KaModule): KotlinPackageProvider? {
        if (module !is KtNativeKlibLibraryModuleByModuleInfo) return null

        return module.project.createPackageProvider(module.forwardDeclarationsScope)
    }
}
