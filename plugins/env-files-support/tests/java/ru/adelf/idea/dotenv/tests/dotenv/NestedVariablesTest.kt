package ru.adelf.idea.dotenv.tests.dotenv

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.util.PsiTreeUtil
import ru.adelf.idea.dotenv.psi.DotEnvNestedVariableKey
import ru.adelf.idea.dotenv.tests.DotEnvFileBasedTestCase

class NestedVariablesTest: DotEnvFileBasedTestCase() {

    fun testNestedVariablesKeyAccessibility() {
        val elementAtCaret = myFixture.elementAtCaret
        assertNotNull(elementAtCaret)
        assertInstanceOf(elementAtCaret, DotEnvNestedVariableKey::class.java)
    }

    fun testNestedVariablesKeyNameReadOps() {
        val key = getKeyAtCaret()
        assertInstanceOf(key, DotEnvNestedVariableKey::class.java)
        assertSame(key, key.nameIdentifier)
        assertEquals("SOME_KEY", key.nameIdentifier?.text)
        assertEquals("SOME_KEY", key.name)
    }

    fun testNestedVariablesKeyRenaming() {
        val key = getKeyAtCaret()
        val parent = key.parent
        WriteCommandAction.runWriteCommandAction(project) {
            key.setName("NEW_KEY")
            assertEquals("NEW_KEY", PsiTreeUtil.getChildOfType(parent, DotEnvNestedVariableKey::class.java)?.text)
        }
    }

    private fun getKeyAtCaret(): DotEnvNestedVariableKey = myFixture.elementAtCaret as DotEnvNestedVariableKey

}