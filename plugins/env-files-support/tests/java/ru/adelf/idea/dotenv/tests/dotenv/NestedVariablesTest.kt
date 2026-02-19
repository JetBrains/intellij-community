package ru.adelf.idea.dotenv.tests.dotenv

import ru.adelf.idea.dotenv.psi.DotEnvNestedVariableKey
import ru.adelf.idea.dotenv.tests.DotEnvFileBasedTestCase

class NestedVariablesTest: DotEnvFileBasedTestCase() {

    fun testNestedVariablesKeyAccessibility() {
        val elementAtCaret = getKeyAtCaret()
        assertNotNull(elementAtCaret)
        assertInstanceOf(elementAtCaret, DotEnvNestedVariableKey::class.java)
    }

    fun testNestedVariablesKeyNameReadOps() {
        val key = getKeyAtCaret()
        assertInstanceOf(key, DotEnvNestedVariableKey::class.java)
        assertEquals("SOME_KEY", key.text)
    }

    private fun getKeyAtCaret(): DotEnvNestedVariableKey = myFixture.file.findElementAt(myFixture.caretOffset)?.parent as DotEnvNestedVariableKey

}