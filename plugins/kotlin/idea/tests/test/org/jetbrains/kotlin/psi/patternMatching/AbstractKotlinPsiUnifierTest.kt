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
    protected abstract fun KtElement.getMatches(file: KtFile): List<TextRange>

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

        val disableTestDirective = IgnoreTests.DIRECTIVES.of(pluginMode)

        val fileTextWithoutDirectives = dataFile().getTextWithoutDirectives() // contains markers
        val file = myFixture.configureByText(fileName(), fileTextWithoutDirectives) as? KtFile ?: error("Failed to configure file")
        val fileText = file.text // doesn't contain ignore-directives and markers

        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), disableTestDirective) {
            val actualText = findPattern(file)
                .getMatches(file)
                .joinToString(separator = "\n\n") { "$it\n${it.substring(fileText)}" }
            val testName = getTestName(/* lowercaseFirstLetter = */ true)
            val matchFile = File(testDataDirectory, "$testName.fir.kt.match").takeIf { isFirPlugin && it.exists() }
                ?: File(testDataDirectory, "$testName.kt.match")
            KotlinTestUtils.assertEqualsToFile(matchFile, actualText)
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = getProjectDescriptorFromTestName()

    private fun File.getTextWithoutDirectives(): String {
        val directives = setOf(IgnoreTests.DIRECTIVES.IGNORE_K1, IgnoreTests.DIRECTIVES.IGNORE_K2)

        return readLines().filterNot { it.trim() in directives }.joinToString("\n")
    }
}
