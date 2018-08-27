package com.intellij.configurationScript

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.testFramework.assertions.Assertions.assertThat
import gnu.trove.THashMap
import org.assertj.core.data.MapEntry
import org.intellij.lang.annotations.Language
import org.junit.Test
import javax.swing.Icon

class ConfigurationFileTest {
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
    val result = parse("""
    runConfigurations:
    """)
//    assertThat((node as MappingNode).value.map { (it.keyNode as ScalarNode).value }).containsExactly("runConfigurations")
    assertThat(result).isNull()
  }

  @Test
  fun `empty rc`() {
    val result = parse("""
    runConfigurations:
      jvmApp:
    """)
    assertThat(result).isInstanceOf(THashMap::class.java)
    @Suppress("UNCHECKED_CAST")
    assertThat(result as Map<String, Any>).containsExactly(MapEntry.entry("jvmApp", null))
  }

  @Test
  fun `one jvmApp`() {
    val result = parse("""
    runConfigurations:
      jvmApp:
        isAlternativeJrePathEnabled: true
    """)
    assertThat(result).isInstanceOf(THashMap::class.java)
    @Suppress("UNCHECKED_CAST")
    assertThat(result as Map<String, Any>).containsExactly(MapEntry.entry("jvmApp", null))
  }
}

private fun parse(@Language("YAML") data: String): Any? {
  return parseConfigurationFile(data.trimIndent().reader())
}

private class TestConfigurationType(id: String) : ConfigurationTypeBase(id, id, "", null as Icon?)