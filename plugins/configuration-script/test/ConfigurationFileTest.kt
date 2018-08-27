package com.intellij.configurationScript

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.testFramework.assertions.Assertions.assertThat
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
    // better will be barfoo but for now we don't support this strange case
    @Suppress("SpellCheckingInspection")
    assertThat(convert("BAR_FOO")).isEqualTo("barfoo")
  }
}

private class TestConfigurationType(id: String) : ConfigurationTypeBase(id, id, "", null as Icon?)