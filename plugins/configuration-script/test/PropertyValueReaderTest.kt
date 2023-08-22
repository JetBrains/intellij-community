package com.intellij.configurationScript

import com.intellij.configurationScript.schemaGenerators.PluginJsonSchemaGenerator
import com.intellij.openapi.updateSettings.impl.PluginsConfiguration
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.ClassRule
import org.junit.Test
import org.snakeyaml.engine.v2.nodes.MappingNode

class PropertyValueReaderTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

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

  @Test
  fun `list of objects`() {
    val result = JdkAutoHints()
    readIntoObject(result, readYaml("""
      sdks:
        - sdkName: foo
          sdkPath: /home/foo
    """).value)

    assertThat(result.sdks).hasSize(1)
    val sdks = result.sdks
    assertThat(sdks).hasSize(1)
  }
}

private fun readYaml(@Language("YAML") data: String): MappingNode {
  return doRead(data.trimIndent().reader())!!
}

private fun doReadPluginsConfiguration(@Suppress("SameParameterValue") @Language("YAML") data: String): PluginsConfiguration {
  return readIntoObject(PluginsConfiguration(), findValueNodeByPath(PluginJsonSchemaGenerator.plugins, readYaml(data).value)!!)
}