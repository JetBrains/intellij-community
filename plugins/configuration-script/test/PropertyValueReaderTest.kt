package com.intellij.configurationScript

//import com.intellij.execution.application.JvmMainMethodRunConfigurationOptions
import com.intellij.configurationScript.providers.PluginsConfiguration
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.ClassRule
import org.junit.Test

class PropertyValueReaderTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  //@Test
  //fun `enum`() {
  //  val result = readRunConfigurations("""
  //  runConfigurations:
  //    java:
  //      shortenClasspath: MANIFEST
  //  """)
  //  val options = JvmMainMethodRunConfigurationOptions()
  //  options.shortenClasspath = ShortenCommandLine.MANIFEST
  //  assertThat(result).containsExactly(options)
  //}

  //@Test
  //fun map() {
  //  val result = readRunConfigurations("""
  //  runConfigurations:
  //    java:
  //      env:
  //        foo: bar
  //        answer: 42
  //  """)
  //  val options = JvmMainMethodRunConfigurationOptions()
  //  options.env = linkedMapOf("foo" to "bar", "answer" to "42")
  //  assertThat(result).containsExactly(options)
  //}

  @Test
  fun collection() {
    val result = doReadPluginsConfiguration("""
      plugins:
        repositories:
          - foo
          - bar
          - http://example.com
      """)
    val options = PluginsConfiguration()
    options.repositories.addAll(listOf("foo", "bar", "http://example.com"))
    assertThat(result).isEqualTo(options)
  }
}

private fun doReadPluginsConfiguration(@Language("YAML") data: String): PluginsConfiguration? {
  return readIntoObject(PluginsConfiguration(), findValueNodeByPath(Keys.plugins, doRead(data.trimIndent().reader())!!.value)!!)
}