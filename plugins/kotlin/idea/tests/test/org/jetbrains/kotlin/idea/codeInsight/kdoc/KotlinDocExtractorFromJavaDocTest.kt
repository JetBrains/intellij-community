// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.kdoc

import com.intellij.rt.execution.junit.FileComparisonFailure
import org.jetbrains.kotlin.idea.kdoc.KotlinDocExtractorFromJavaDoc
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File


@RunWith(JUnit4::class)
class KotlinDocExtractorFromJavaDocTest : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE

    override val testDataDirectory: File
        get() = IDEA_TEST_DATA_DIR.resolve("codeInsight/kdoc")

    private fun doTest(docUrl: String, javaDocPagePath: String, expectedResultPath: String) {
        val javaDocPage = testDataFile(javaDocPagePath).readText()
        val expectedResultFile = testDataFile(expectedResultPath)
        val expectedResult = expectedResultFile.bufferedReader().use { it.readText() }

        val extractor = KotlinDocExtractorFromJavaDoc(project)
        val extracted = extractor.getExternalDocInfoForElement(docUrl, javaDocPage)

        if (expectedResult != extracted) {
            throw FileComparisonFailure(
                "Extracted information differs from expected",
                expectedResult,
                extracted,
                expectedResultFile.canonicalPath
            )
        }
    }

    @Test
    fun `class docs extraction`() {
        doTest("some/pack/Margherita.html", "Margherita.html", "class-docs-extracted.html")
    }

    @Test
    fun `constructor docs extraction`() {
        doTest("some/pack/Margherita.html#Margherita(", "Margherita.html", "constructor-docs-extracted.html")
    }

    @Test
    fun `method with no overloads docs extraction`() {
        doTest("some/pack/Margherita.html#contains(", "Margherita.html", "method-docs-extracted.html")
    }

    @Test
    fun `method with overloads docs extraction`() {
        doTest(
            "some/pack/Margherita.html#someFunWithArgs(",
            "Margherita.html",
            "method-with-overloads-docs-extracted.html"
        )
    }

    @Test
    fun `enum entry docs extraction`() {
        doTest("some/pack/MyBooleanEnum.html#FALSE", "MyBooleanEnum.html", "enum-entry-docs-extracted.html")
    }
}