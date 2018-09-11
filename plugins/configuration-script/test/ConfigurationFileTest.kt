package com.intellij.configurationScript

import com.google.gson.Gson
import com.intellij.execution.application.JvmMainMethodRunConfigurationOptions
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
  fun schema() {
    // check that parseable
    val schema = generateConfigurationSchema()
    val jsonReader = Gson().fromJson(CharSequenceReader(schema), Any::class.java)
    assertThat(jsonReader).isNotNull
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
    val result = readRunConfigurations("""
    runConfigurations:
    """)
    assertThat(result).isEmpty()
  }

  @Test
  fun `empty rc type group`() {
    val result = readRunConfigurations("""
    runConfigurations:
      jvmMainMethod:
    """)
    assertThat(result).isEmpty()
  }

  @Test
  fun `empty rc`() {
    val result = readRunConfigurations("""
    runConfigurations:
      jvmMainMethod:
        -
    """)
    assertThat(result).isEmpty()
  }

  @Test
  fun `one jvmMainMethod`() {
    val result = readRunConfigurations("""
    runConfigurations:
      jvmMainMethod:
        isAlternativeJrePathEnabled: true
    """)
    val options = JvmMainMethodRunConfigurationOptions()
    options.isAlternativeJrePathEnabled = true
    assertThat(result).containsExactly(options)
  }

  @Test
  fun `one jvmMainMethod as list`() {
    val result = readRunConfigurations("""
    runConfigurations:
      jvmMainMethod:
        - isAlternativeJrePathEnabled: true
    """)
    val options = JvmMainMethodRunConfigurationOptions()
    options.isAlternativeJrePathEnabled = true
    assertThat(result).containsExactly(options)
  }

  @Test
  fun `one jvmMainMethod as list - template`() {
    val result = readRunConfigurations("""
    runConfigurations:
      templates:
        jvmMainMethod:
          - isAlternativeJrePathEnabled: true
    """, isTemplatesOnly = true)
    val options = JvmMainMethodRunConfigurationOptions()
    options.isAlternativeJrePathEnabled = true
    assertThat(result).containsExactly(options)
  }

  @Test
  fun `templates as invalid node type`() {
    val result = readRunConfigurations("""
    runConfigurations:
      templates: foo
    """, isTemplatesOnly = true)
    assertThat(result).isEmpty()
  }
}

internal fun readRunConfigurations(@Language("YAML") data: String, isTemplatesOnly: Boolean = false): List<Any> {
  val list = SmartList<Any>()
  com.intellij.configurationScript.providers.readRunConfigurations(doRead(data.trimIndent().reader())!!, isTemplatesOnly) { _, state ->
    list.add(state)
  }
  return list
}

private class TestConfigurationType(id: String) : ConfigurationTypeBase(id, id, "", null as Icon?)