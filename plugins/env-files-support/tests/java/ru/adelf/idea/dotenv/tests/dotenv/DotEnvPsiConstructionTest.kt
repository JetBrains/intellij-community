package ru.adelf.idea.dotenv.tests.dotenv

import ru.adelf.idea.dotenv.tests.DotEnvFileBasedTestCase

class DotEnvPsiConstructionTest : DotEnvFileBasedTestCase() {

    fun testUnquotedValuesDoNotSupportNestedVariables() = doPsiDumpTest()

    fun testSingleQuotedValuesDoNotSupportNestedVariables() = doPsiDumpTest()

    fun testBasicNestedVariablesSupport() = doPsiDumpTest()

    fun testNestedVariableAnchorsMayBeEscaped() = doPsiDumpTest()

}