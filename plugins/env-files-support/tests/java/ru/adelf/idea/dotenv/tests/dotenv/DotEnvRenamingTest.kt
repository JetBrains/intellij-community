package ru.adelf.idea.dotenv.tests.dotenv

import ru.adelf.idea.dotenv.tests.DotEnvFileBasedTestCase

class DotEnvRenamingTest : DotEnvFileBasedTestCase() {

    fun testRenameDeclarationWithoutUsage() {
        doRenameTest("PROP_1_RENAMED")
    }

    fun testRenameDeclarationWithNestedVariableUsage() {
        doRenameTest("PROP_1_RENAMED")
    }

    fun testRenameDeclaredNestedVariable() {
        doRenameTest("PROP_1_RENAMED")
    }

    fun testRenamingPropagatesToNestedVariables() {
        doRenameTest("RENAMED_PROPERTY", "env", "usage.env")
    }

    fun testRenamingPropagatesFromNestedVariables() {
        doRenameTest("RENAMED_PROPERTY", "env", "declaration.env")
    }

}