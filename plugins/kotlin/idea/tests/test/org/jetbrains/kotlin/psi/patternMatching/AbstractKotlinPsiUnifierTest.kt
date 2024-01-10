// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.psi.patternMatching

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import java.io.File

abstract class AbstractKotlinPsiUnifierTest : KotlinLightCodeInsightFixtureTestCase() {
    protected abstract fun KtElement.getMatches(file: KtFile): List<String>

    fun doTest(unused: String) {
        fun findPattern(file: KtFile): KtElement {
            val selectionModel = myFixture.editor.selectionModel
            val start = selectionModel.selectionStart
            val end = selectionModel.selectionEnd
            val selectionRange = TextRange(start, end)
            return file.findElementAt(start)?.parentsWithSelf?.last {
                (it is KtExpression || it is KtTypeReference || it is KtWhenCondition)
                        && selectionRange.contains(it.textRange ?: TextRange.EMPTY_RANGE)
            } as KtElement
        }

        val file = myFixture.configureByFile(fileName()) as KtFile
        val disableTestDirective = if (isFirPlugin) IgnoreTests.DIRECTIVES.IGNORE_K2 else IgnoreTests.DIRECTIVES.IGNORE_K1

        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), disableTestDirective) {
            val actualText = findPattern(file).getMatches(file).joinToString("\n\n")
            KotlinTestUtils.assertEqualsToFile(File(testDataDirectory, "${fileName()}.match"), actualText)
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = getProjectDescriptorFromTestName()
}
