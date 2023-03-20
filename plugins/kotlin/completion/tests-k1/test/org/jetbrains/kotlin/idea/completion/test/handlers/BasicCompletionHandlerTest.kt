// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.completion.handlers

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.completion.test.handlers.CompletionHandlerTestBase
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@Deprecated("All tests from here to be moved to the generated test")
@TestRoot("completion/testData")
@TestMetadata("handlers")
@RunWith(JUnit38ClassRunner::class)
class BasicCompletionHandlerTest12 : CompletionHandlerTestBase() {
    private fun checkResult() {
        fixture.checkResultByFile(getTestName(false) + ".kt.after")
    }

    private fun doTest() {
        doTest(2, "*", null, null, '\n')
    }

    private fun doTest(time: Int, lookupString: String?, tailText: String?, completionChar: Char) {
        doTest(time, lookupString, null, tailText, completionChar)
    }

    private fun doTest(time: Int, lookupString: String?, itemText: String?, tailText: String?, completionChar: Char) {
        val fileName = fileName()
        val fileText = FileUtil.loadFile(File(dataFilePath(fileName)), true)

        fixture.configureByFile(fileName)
        doTestWithTextLoaded(
            fileText,
            myFixture,
            CompletionType.BASIC,
            time,
            lookupString,
            itemText,
            tailText,
            completionChar.toString(),
            fileName() + ".after"
        )
    }

    fun testClassCompletionImport() = doTest(2, "SortedSet", null, '\n')

    fun testClassCompletionInMiddle() = doTest(1, "TimeZone", " (java.util)", '\t')

    fun testClassCompletionInImport() = doTest(1, "TimeZone", " (java.util)", '\t')

    fun testClassCompletionInLambda() = doTest(1, "String", " (kotlin)", '\n')

    fun testClassCompletionBeforeName() = doTest(1, "StringBuilder", " (kotlin.text)", '\n')

    fun testDoNotInsertImportForAlreadyImported() = doTest()

    fun testDoNotInsertDefaultJsImports() = doTest()

    fun testDoNotInsertImportIfResolvedIntoJavaConstructor() = doTest()

    fun testNonStandardArray() = doTest(2, "Array", " (java.lang.reflect)", '\n')

    fun testNoParamsFunction() = doTest()

    fun testParamsFunction() = doTest()

    fun testNamedParametersCompletion() = doTest()

    fun testNamedParametersAreNotProperlyOrdered() = doTest()

    fun testAddNameToExistingArgument() = doTest()

    fun testBasicCompletionWorksAfterLastAllowedArgument() = doTest()

    fun testNamedParameterBeforeAnotherNamedParameter() = doTest() // The test checks parsing error

    fun testNamedParameterCompletionWithLeadingSpace() = doTest()

    fun testNamedParameterWithExistingComma() = doTest()

    fun testNamedParameterManualRenameCompletion() = doTest()

    fun testNamedParameterCompletionWithTrailingCommaAndSpace() = doTest()

    fun testNamedParametersCompletionOnEqual() = doTest(0, "paramTest =", "paramTest =", null, '=')

    fun testNamedParameterKeywordName() = doTest(1, "class =", "class =", null, '\n')

    fun testInsertJavaClassImport() = doTest()

    fun testInsertVoidJavaMethod() = doTest()

    fun testPropertiesGetter() = doTest()

    fun testExistingSingleBrackets() = doTest()

    fun testExtFunction() = doTest()

    fun testInsertImportOnTab() = doTest(2, "ArrayList", null, '\t')

    fun testInsertFqnForJavaClass() = doTest(2, "SortedSet", "<E> (java.util)", '\n')

    fun testTabInsertAtTheFileEnd() = doTest(0, "vvvvv", null, '\t')

    fun testTabInsertBeforeBraces() = doTest(0, "vvvvv", null, '\t')

    fun testTabInsertBeforeBrackets() = doTest(0, "vvvvv", null, '\t')

    fun testTabInsertBeforeOperator() = doTest(0, "vvvvv", null, '\t')

    fun testTabInsertBeforeParentheses() = doTest(0, "vvvvv", null, '\t')

    fun testTabInsertInsideBraces() = doTest(1, "vvvvv", null, '\t')

    fun testTabInsertInsideBrackets() = doTest(0, "vvvvv", null, '\t')

    fun testTabInsertInsideEmptyParentheses() = doTest(0, "vvvvv", null, '\t')

    fun testTabInsertInsideParentheses() = doTest(0, "vvvvv", null, '\t')

    fun testTabInsertInSimpleName() = doTest(0, "vvvvv", null, '\t')

    fun testTabReplaceIdentifier() = doTest(1, "sss", null, '\t')
    fun testTabReplaceIdentifier2() = doTest(1, "sss", null, '\t')
    fun testTabReplaceThis() = doTest(1, "sss", null, '\t')
    fun testTabReplaceNull() = doTest(1, "sss", null, '\t')
    fun testTabReplaceTrue() = doTest(1, "sss", null, '\t')
    fun testTabReplaceNumber() = doTest(1, "sss", null, '\t')

    fun testSingleBrackets() {
        fixture.configureByFile(fileName())
        fixture.type('(')
        checkResult()
    }

    fun testInsertFunctionWithBothParentheses() {
        fixture.configureByFile(fileName())
        fixture.type("test()")
        checkResult()
    }

    fun testObject() = doTest()

    fun testEnumMember() = doTest(1, "A", null, '\n')
    fun testEnumMember1() = doTest(1, "A", null, '\n')
    fun testClassFromClassObject() = doTest(1, "Some", null, '\n')
    fun testClassFromClassObjectInPackage() = doTest(1, "Some", null, '\n')

    fun testParameterType() = doTest(1, "StringBuilder", " (kotlin.text)", '\n')

    fun testLocalClassCompletion() = doTest(1, "LocalClass", null, '\n')
    fun testNestedLocalClassCompletion() = doTest(1, "Nested", null, '\n')

    fun testTypeArgOfSuper() = doTest(1, "X", null, '\n')

    fun testKeywordClassName() = doTest(1, "class", null, '\n')
    fun testKeywordFunctionName() = doTest(1, "fun", "fun", "() (test)", '\n')

    fun testInfixCall() = doTest(1, "to", null, null, '\n')
    fun testInfixCallOnSpace() = doTest(1, "to", null, null, ' ')

    fun testImportedEnumMember() {
        doTest(1, "AAA", null, null, '\n')
    }

    fun testInnerClass() {
        doTest(1, "Inner", null, null, '\n')
    }

    fun testNoParentheses() {
        val settings = EditorSettingsExternalizable.getInstance()
        settings.setInsertParenthesesAutomatically(false)
        try {
            fixture.configureByText("test.kt", "fun myFunction() { myFun<caret> }")
            fixture.completeBasic()
            fixture.checkResult("fun myFunction() { myFunction<caret> }")
        }
        finally {
            settings.setInsertParenthesesAutomatically(true)
        }
    }
}
