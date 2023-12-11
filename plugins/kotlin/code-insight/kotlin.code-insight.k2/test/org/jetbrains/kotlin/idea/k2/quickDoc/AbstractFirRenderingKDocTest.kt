// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickDoc

import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinInlineDocumentationProvider
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

abstract class AbstractFirRenderingKDocTest: KotlinLightCodeInsightFixtureTestCase() {

    override fun isFirPlugin(): Boolean {
        return true
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    protected fun doTest(path: String) {
        myFixture.configureByFile(fileName())
        val file = myFixture.file

        val kDocProvider = KotlinInlineDocumentationProvider()

        val comments = mutableListOf<String>()
        kDocProvider.inlineDocumentationItems(file).forEach {
            val rendered = it.renderText()
            if (rendered != null) {
                comments.add(rendered.replace("\n", ""))
            }
        }

        val expectedRenders = InTextDirectivesUtils.findLinesWithPrefixesRemoved(file.text, "// RENDER: ")
        UsefulTestCase.assertOrderedEquals(comments, expectedRenders)
    }
}