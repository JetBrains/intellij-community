package ru.adelf.idea.dotenv.tests.dotenv


import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.runInEdtAndWait
import ru.adelf.idea.dotenv.psi.DotEnvKey
import ru.adelf.idea.dotenv.tests.DotEnvFileBasedTestCase


class NestedVariablesNavigationTest: DotEnvFileBasedTestCase() {

    fun testNestedVariablesGotoDeclarationAction() {
        runInEdtAndWait {
            myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
        }
        assertEquals("PROPERTY_A", (myFixture.file.findElementAt(myFixture.caretOffset)?.parent as DotEnvKey).text)
    }

    fun testNestedVariablesGotoUsageAction() {
        doUsageTest()
    }

}