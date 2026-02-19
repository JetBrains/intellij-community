// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight.tooling

import org.jetbrains.kotlin.idea.base.codeInsight.PsiOnlyKotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractGenericTestIconProvider
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractNativeIdePlatformKindTooling
import org.jetbrains.kotlin.idea.highlighter.KotlinTestRunLineMarkerContributor
import org.jetbrains.kotlin.idea.testIntegration.genericKotlinTestUrls
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import javax.swing.Icon

internal class FirNativeIdePlatformKindTooling : AbstractNativeIdePlatformKindTooling() {

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
        if (!testIconProvider.isKotlinTestDeclaration(testContainerElement)) {
            return null
        }

        return KotlinTestRunLineMarkerContributor.getTestStateIcon(declaration.genericKotlinTestUrls(), declaration)
    }
}