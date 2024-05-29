// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.providers.KotlinDeclarationProvider
import org.jetbrains.kotlin.analysis.providers.KotlinForwardDeclarationsPackageProviderFactory
import org.jetbrains.kotlin.analysis.providers.KotlinForwardDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.providers.KotlinPackageProvider
import org.jetbrains.kotlin.analysis.providers.createDeclarationProvider
import org.jetbrains.kotlin.analysis.providers.createPackageProvider
import org.jetbrains.kotlin.idea.base.projectStructure.KtNativeKlibLibraryModuleByModuleInfo

/**
 * FIR IDE declaration provider factory implementation for Kotlin/Native forward declarations.
 * Delegates to the regular factory, but uses a narrow [GlobalSearchScope] that contains only generated declarations of the [KtModule].
 *
 * @see [org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.KotlinForwardDeclarationsFileGenerator]
 */
internal class FirIdeForwardDeclarationProviderFactory : KotlinForwardDeclarationProviderFactory() {
    override fun createDeclarationProvider(ktModule: KtModule): KotlinDeclarationProvider? {
        if (ktModule !is KtNativeKlibLibraryModuleByModuleInfo) return null

        return ktModule.project.createDeclarationProvider(ktModule.forwardDeclarationsScope, ktModule)
    }
}

/**
 * FIR IDE package provider factory for Kotlin/Native forward declarations.
 * Delegates to the regular factory, but uses a narrow [GlobalSearchScope] that contains only generated declarations of the [KtModule].
 *
 * @see [org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.KotlinForwardDeclarationsFileGenerator]
 */
internal class FirIdeForwardDeclarationPackageProviderFactory : KotlinForwardDeclarationsPackageProviderFactory() {
    override fun createPackageProvider(ktModule: KtModule): KotlinPackageProvider? {
        if (ktModule !is KtNativeKlibLibraryModuleByModuleInfo) return null

        return ktModule.project.createPackageProvider(ktModule.forwardDeclarationsScope)
    }
}
