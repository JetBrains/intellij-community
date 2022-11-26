package org.jetbrains.completion.full.line.local

import junit.framework.TestCase
import org.intellij.lang.annotations.Language

abstract class XmlSerializationTest : TestCase() {
  /**
   * This function is only needed to enable highlighting for xml in a parameter.
   */
  protected fun xml(@Language("XML") data: String) = data

  protected fun assertEqualsWithoutIndent(expected: Any, actual: Any) = assertEquals(
    expected.toString().removeIndent(),
    actual.toString().removeIndent(),
  )

  private fun String.removeIndent() = lineSequence().joinToString("\n") {
    it.trimStart()
  }
}
