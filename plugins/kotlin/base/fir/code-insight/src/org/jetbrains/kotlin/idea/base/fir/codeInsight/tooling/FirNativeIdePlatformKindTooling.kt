// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight.tooling

import org.jetbrains.kotlin.idea.base.codeInsight.PsiOnlyKotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractGenericTestIconProvider
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractNativeIdePlatformKindTooling
import org.jetbrains.kotlin.idea.base.platforms.StableModuleNameProvider
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import javax.swing.Icon

class FirNativeIdePlatformKindTooling : AbstractNativeIdePlatformKindTooling() {
    override val testIconProvider: AbstractGenericTestIconProvider
        get() = SymbolBasedGenericTestIconProvider

    override fun acceptsAsEntryPoint(function: KtFunction): Boolean {
        return function is KtNamedFunction
                && PsiOnlyKotlinMainFunctionDetector.isMain(function)
                && super.acceptsAsEntryPoint(function)
    }

    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        if (!allowSlowOperations) {
            return null
        }

        val testContainerElement = testIconProvider.getTestContainerElement(declaration) ?: return null
        if (testIconProvider.isKotlinTestDeclaration(testContainerElement)) {
            return null
        }

        val module = declaration.module ?: return null
        val moduleName = StableModuleNameProvider.getInstance(module.project).getStableModuleName(module)
        return getTestIcon(declaration, moduleName)
    }
}