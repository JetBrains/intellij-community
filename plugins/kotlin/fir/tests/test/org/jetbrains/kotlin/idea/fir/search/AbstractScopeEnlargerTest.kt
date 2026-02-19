// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.search

import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinResolveScopeEnlarger
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractScopeEnlargerTest : KotlinLightCodeInsightFixtureTestCase() {
    @OptIn(KaAllowAnalysisOnEdt::class)
    fun doTest(testFilePath: String) {
        myFixture.configureByFile(testFilePath) as KtFile
        val fileOutOfProjectScope = LightVirtualFile("dummy.txt", "No content")

        val ktElementAtCaret = myFixture.elementAtCaret.parentOfType<KtElement>(withSelf = true) ?: error("No declaration found at caret")

        KotlinResolveScopeEnlarger.EP_NAME.point.registerExtension(
            KtResolveScopeEnlargerForTests(fileOutOfProjectScope), testRootDisposable
        )

        allowAnalysisOnEdt {
            analyze(ktElementAtCaret) {
                assert(analysisScope.contains(fileOutOfProjectScope))
            }
        }
    }
}