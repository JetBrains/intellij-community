// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.intentions.tests

import com.intellij.openapi.Disposable
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.analysis.KotlinIdeInjectedFilesAnalysisPromoter


class InjectedFilesAnalysisPromoter : KotlinIdeInjectedFilesAnalysisPromoter {
    override fun shouldRunOnlyEssentialHighlightingForInjectedFile(psiFile: PsiFile): Boolean {
        return false
    }

    override fun shouldRunAnalysisForInjectedFile(viewProvider: FileViewProvider): Boolean {
        return true
    }
}

/**
 * Base class for generating tests related to Kotlin injected files.
 * For analysis to be performed, [InjectedFilesAnalysisPromoter] should be registered.
 */
abstract class AbstractK2IntentionInInjectionTest : AbstractK2IntentionTest() {
    private fun <T : KotlinIdeInjectedFilesAnalysisPromoter> registerExtensionPoint(
        provider: T,
        testRootDisposable: Disposable
    ) {
        EXTENSION_POINT_NAME.point.registerExtension(provider, testRootDisposable)
    }

    override fun doTest(unused: String) {
        registerExtensionPoint(
            InjectedFilesAnalysisPromoter(),
            testRootDisposable
        )
        super.doTest(unused)
    }

    companion object {
        private val EXTENSION_POINT_NAME = KotlinIdeInjectedFilesAnalysisPromoter.Companion.EP_NAME
    }
}