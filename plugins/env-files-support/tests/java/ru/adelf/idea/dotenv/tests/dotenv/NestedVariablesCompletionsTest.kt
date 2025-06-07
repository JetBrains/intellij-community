package ru.adelf.idea.dotenv.tests.dotenv

import ru.adelf.idea.dotenv.tests.DotEnvFileBasedTestCase

class NestedVariablesCompletionsTest: DotEnvFileBasedTestCase() {

    fun testNestedVariablesCompletionPrefix() {
        doSingleCompletionTest()
    }

    fun testNestedVariablesCompletionInfix() {
        doSingleCompletionTest()
    }

    fun testNestedVariablesCompletionSuffix() {
        doSingleCompletionTest()
    }

    fun testNestedVariablesCompletionSkipsEscaped() {
        doSingleCompletionTest()
    }

    fun testNestedVariablesCompletionSkipsOutsideDoubleQuotes() {
        doSingleCompletionTest()
    }

}