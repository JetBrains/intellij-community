// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils

abstract class AbstractExpressionTypeTest : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE

    protected fun doTest(path: String) {
        myFixture.configureByFile(fileName())
        val expressionTypeProvider = KotlinExpressionTypeProviderDescriptorsImpl()
        val elementAtCaret = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!!
        val expressions = expressionTypeProvider.getExpressionsAt(elementAtCaret)
        val types = expressions.map { "${it.text.replace('\n', ' ')} -> ${expressionTypeProvider.getInformationHint(it)}" }
        val expectedTypes = InTextDirectivesUtils.findLinesWithPrefixesRemoved(myFixture.file.text, "// TYPE: ")
        UsefulTestCase.assertOrderedEquals(types, expectedTypes)
    }
}
