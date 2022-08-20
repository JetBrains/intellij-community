// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test.handlers

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.completion.test.COMPLETION_TEST_DATA_BASE
import org.jetbrains.kotlin.idea.completion.test.KotlinFixtureCompletionBaseTestCase
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
@TestRoot("idea/tests")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("testData/handlers/multifile")
open class CompletionMultiFileHandlerTest22 : KotlinFixtureCompletionBaseTestCase() {
    fun testExtensionFunctionImport() = doTest()
    fun testExtensionPropertyImport() = doTest()
    fun testImportAlreadyImportedObject() = doTest()
    fun testJetClassCompletionImport() = doTest()
    fun testStaticMethodFromGrandParent() = doTest('\n', "StaticMethodFromGrandParent-1.java", "StaticMethodFromGrandParent-2.java")
    fun testTopLevelFunctionImport() = doTest()
    fun testTopLevelFunctionInQualifiedExpr() = doTest()
    fun testTopLevelPropertyImport() = doTest()
    fun testTopLevelValImportInStringTemplate() = doTest()
    fun testNoParenthesisInImports() = doTest()
    fun testKeywordExtensionFunctionName() = doTest()
    fun testInfixExtensionCallImport() = doTest()
    fun testClassWithClassObject() = doTest()
    fun testGlobalFunctionImportInLambda() = doTest()
    fun testObjectInStringTemplate() = doTest()
    fun testPropertyFunctionConflict() = doTest()
    fun testPropertyFunctionConflict2() = doTest(tailText = " { Int, Int -> ... } (i: (Int, Int) -> Unit) (a.b)")
    fun testExclCharInsertImport() = doTest('!')
    fun testPropertyKeysWithPrefixEnter() = doTest('\n', "TestBundle.properties")
    fun testPropertyKeysWithPrefixTab() = doTest('\t', "TestBundle.properties")
    fun testFileRefInStringLiteralEnter() = doTest('\n', "foo.txt", "bar.txt")
    fun testFileRefInStringLiteralTab() = doTest('\t', "foo.txt", "bar.txt")
    fun testNotImportedExtension() = doTest()
    fun testNotImportedTypeAlias() = doTest()
    fun testKT12077() = doTest()

    open fun doTest(completionChar: Char = '\n', vararg extraFileNames: String, tailText: String? = null) {
        val fileNameBase = getTestName(false)
        val defaultFiles = listOf("$fileNameBase-1.kt", "$fileNameBase-2.kt")
        val filteredFiles = defaultFiles.filter { File(testDataDirectory, it).exists() }

        require(filteredFiles.isNotEmpty()) { "At least one of $defaultFiles should exist!" }

        myFixture.configureByFiles(*extraFileNames)
        myFixture.configureByFiles(*filteredFiles.toTypedArray())
        val items = complete(CompletionType.BASIC, 2)

        if (items != null) {
            val item = if (tailText == null)
                items.singleOrNull() ?: error("Multiple items in completion")
            else {
                val presentation = LookupElementPresentation()
                items.first {
                    it.renderElement(presentation)
                    presentation.tailText == tailText
                }
            }

            CompletionHandlerTestBase.selectItem(myFixture, item, completionChar)
        }
        myFixture.checkResultByFile("$fileNameBase.kt.after")
    }

    override val testDataDirectory: File
        get() = COMPLETION_TEST_DATA_BASE.resolve("handlers/multifile")

    override fun defaultCompletionType(): CompletionType = CompletionType.BASIC
    override fun getPlatform(): TargetPlatform = JvmPlatforms.unspecifiedJvmPlatform
    override fun getProjectDescriptor() = LightJavaCodeInsightFixtureTestCase.JAVA_11
}
