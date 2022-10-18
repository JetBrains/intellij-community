package org.jetbrains.completion.full.line.python.tests

import com.intellij.testFramework.LightProjectDescriptor
import com.jetbrains.python.psi.LanguageLevel

class PyLightProjectDescriptor(
    private val dataPath: String,
    private val myLevel: LanguageLevel = LanguageLevel.getLatest(),
    private val myName: String? = null,
) : LightProjectDescriptor() {
    override fun getSdk() = PythonMockSdk.create(myLevel, myName, dataPath)
}
