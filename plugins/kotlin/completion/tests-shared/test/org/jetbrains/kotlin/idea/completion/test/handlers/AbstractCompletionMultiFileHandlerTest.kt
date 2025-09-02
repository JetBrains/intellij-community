// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.test.handlers

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.completion.test.COMPLETION_TEST_DATA_BASE
import org.jetbrains.kotlin.idea.completion.test.KotlinFixtureCompletionBaseTestCase
import org.jetbrains.kotlin.idea.completion.test.testWithAutoCompleteSetting
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.test.TestMetadata
import java.io.File

@TestRoot("idea/tests")
@TestDataPath("/")
@TestMetadata("testData/handlers/multifile")
abstract class AbstractCompletionMultiFileHandlerTest : KotlinFixtureCompletionBaseTestCase() {
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
    fun testPropertyFunctionConflict() = doTest { tailText == "(i: Int) (a.b)" }
    fun testPropertyFunctionConflict2() = doTest { tailText == " { Int, Int -> ... } (i: (Int, Int) -> Unit) (a.b)" }
    fun testExclCharInsertImport() = doTest('!')
    fun testPropertyKeysWithPrefixEnter() = doTest('\n', "TestBundle.properties")
    fun testPropertyKeysWithPrefixTab() = doTest('\t', "TestBundle.properties")
    fun testFileRefInStringLiteralEnter() = doTest('\n', "foo.txt", "bar.txt")
    fun testFileRefInStringLiteralTab() = doTest('\t', "foo.txt", "bar.txt")
    fun testNotImportedExtension() = doTest()
    fun testNotImportedTypeAlias() = doTest()
    fun testKT12077() = doTest()
    fun testClassInRootPackage() = doTest()
    fun testInImportEscaped() = doTest { tailText == " (`foo bar`)" }
    fun testPackageDirective() = doTest()
    fun testPackageInImportDirective() = doTest()
    fun testJavaEnumCompletionEntry() = doTest(extraFileNames = arrayOf("JavaEnum.java"))
    fun testKTIJ_32792() = doTest { tailText == " -> " && typeText == "(Int, String)" }
    fun testExplicitReceiverCast() = doTest()
    fun testCovariantExtensionFunction() = doTest()

    protected fun getTestFileName(): String = "${getTestName(false)}-1.kt"

    protected fun getDependencyFileName(): String = "${getTestName(false)}-2.kt"

    open fun doTest(
        completionChar: Char = '\n',
        vararg extraFileNames: String,
        predicate: LookupElementPresentation.() -> Boolean = { true },
    ) {
        val defaultFiles = listOf(getTestFileName(), getDependencyFileName())
        val filteredFiles = defaultFiles.filter { File(testDataDirectory, it).exists() }

        require(filteredFiles.isNotEmpty()) { "At least one of $defaultFiles should exist!" }

        myFixture.configureByFiles(*extraFileNames)
        myFixture.configureByFiles(*filteredFiles.toTypedArray())

        testWithAutoCompleteSetting(File(testDataDirectory, getTestFileName()).readText()) {
            val items = complete(CompletionType.BASIC, 2)
            if (items != null) {
                val item = try {
                    items.single { element ->
                        val presentation = LookupElementPresentation.renderElement(element)
                        predicate(presentation)
                    }
                } catch (_: NoSuchElementException) {
                    error("Multiple items in completion")
                }

                CompletionHandlerTestBase.selectItem(myFixture, item, completionChar)
            }

            myFixture.checkResultByFile("${getTestName(false)}.kt.after")
        }
    }

    override val testDataDirectory: File
        get() = COMPLETION_TEST_DATA_BASE.resolve("handlers/multifile")

    override fun defaultCompletionType(): CompletionType = CompletionType.BASIC
    override fun getPlatform(): TargetPlatform = JvmPlatforms.unspecifiedJvmPlatform
    override fun getProjectDescriptor() = LightJavaCodeInsightFixtureTestCase.JAVA_11
}
