@file:Suppress("UsePropertyAccessSyntax")
package com.intellij.configurationScript

import com.fasterxml.jackson.core.JsonFactory
import com.intellij.configurationScript.providers.readRunConfigurations
import com.intellij.configurationScript.schemaGenerators.rcTypeIdToPropertyName
//import com.intellij.execution.application.JvmMainMethodRunConfigurationOptions
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.SmartList
import com.intellij.util.text.CharSequenceReader
import org.intellij.lang.annotations.Language
import org.junit.ClassRule
import org.junit.Test
import javax.swing.Icon

class ConfigurationFileTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Test
  fun `file name`() {
    assertThat(isConfigurationFile("foo")).isFalse()
    assertThat(isConfigurationFile("foo.yaml")).isFalse()
    assertThat(isConfigurationFile("foo.yml")).isFalse()
    assertThat(isConfigurationFile("intellij.yml")).isTrue()
    assertThat(isConfigurationFile("intellij.yaml")).isTrue()
    assertThat(isConfigurationFile("intellij.json")).isTrue()
  }

  @Test
  fun schema() {
    // check that parseable
    val schema = doGenerateConfigurationSchema(emptyList())
    val jsonFactory = JsonFactory()
    jsonFactory.createParser(CharSequenceReader(schema)).nextValue()
  }

  @Test
  fun rcId() {
    fun convert(string: String): String {
      return rcTypeIdToPropertyName(TestConfigurationType(string)).toString()
    }

    assertThat(convert("foo")).isEqualTo("foo")
    assertThat(convert("Foo")).isEqualTo("foo")
    assertThat(convert("foo-bar")).isEqualTo("fooBar")
    assertThat(convert("foo.bar")).isEqualTo("fooBar")
    assertThat(convert("foo_bar")).isEqualTo("fooBar")
    assertThat(convert("FOO")).isEqualTo("foo")
    assertThat(convert("_FOO")).isEqualTo("foo")
    // better will be barFoo but for now we don't support this strange case
    @Suppress("SpellCheckingInspection")
    assertThat(convert("BAR_FOO")).isEqualTo("barfoo")
  }

  @Test
  fun empty() {
    val result = collectRunConfigurations("""
    runConfigurations:
    """)
    assertThat(result).isEmpty()
  }

  @Test
  fun `empty rc type group`() {
    val result = collectRunConfigurations("""
    runConfigurations:
      java:
    """)
    assertThat(result).isEmpty()
  }

  @Test
  fun `empty rc`() {
    val result = collectRunConfigurations("""
    runConfigurations:
      java:
        -
    """)
    assertThat(result).isEmpty()
  }

  @Test
  fun `templates as invalid node type`() {
    val result = collectRunConfigurations("""
    runConfigurations:
      templates: foo
    """, isTemplatesOnly = true)
    assertThat(result).isEmpty()
  }
}

fun collectRunConfigurations(@Language("YAML") data: String, isTemplatesOnly: Boolean = false): List<Any> {
  val list = SmartList<Any>()
  readRunConfigurations(doRead(data.trimIndent().reader())!!, isTemplatesOnly) { _, state ->
    list.add(state)
  }
  return list
}

private class TestConfigurationType(id: String) : ConfigurationTypeBase(id, id, "", null as Icon?)