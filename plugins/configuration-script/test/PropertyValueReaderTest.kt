package com.intellij.configurationScript

import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.application.JvmMainMethodRunConfigurationOptions
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test

class PropertyValueReaderTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Test
  fun `enum`() {
    val result = parse("""
    runConfigurations:
      jvmMainMethod:
        shortenClasspath: MANIFEST
    """)
    val options = JvmMainMethodRunConfigurationOptions()
    options.shortenClasspath = ShortenCommandLine.MANIFEST
    assertThat(result).containsExactly(options)
  }

  @Test
  fun map() {
    val result = parse("""
    runConfigurations:
      jvmMainMethod:
        env:
          foo: bar
          answer: 42
    """)
    val options = JvmMainMethodRunConfigurationOptions()
    options.env = linkedMapOf("foo" to "bar", "answer" to "42")
    assertThat(result).containsExactly(options)
  }
}