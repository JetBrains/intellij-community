package ru.adelf.idea.dotenv.tests.dotenv


import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.runInEdtAndWait
import ru.adelf.idea.dotenv.psi.DotEnvProperty
import ru.adelf.idea.dotenv.tests.DotEnvFileBasedTestCase


class NestedVariablesNavigationTest: DotEnvFileBasedTestCase() {

    fun testNestedVariablesGotoDeclarationAction() {
        runInEdtAndWait {
            myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
        }
        assertEquals("PROPERTY_A", (myFixture.elementAtCaret as DotEnvProperty).key.text)
    }

    fun testNestedVariablesGotoUsageAction() {
        doUsageTest()
    }

}