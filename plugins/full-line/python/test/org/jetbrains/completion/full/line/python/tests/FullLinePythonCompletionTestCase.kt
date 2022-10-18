package org.jetbrains.completion.full.line.python.tests

import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase

abstract class FullLinePythonCompletionTestCase : FullLineCompletionTestCase() {
  override fun getProjectDescriptor() = PyLightProjectDescriptor(basePath, LanguageLevel.PYTHON36)
}
