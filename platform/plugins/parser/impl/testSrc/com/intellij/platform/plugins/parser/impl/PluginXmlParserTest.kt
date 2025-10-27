// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule
import com.intellij.util.xml.dom.NoOpXmlInterner
import com.intellij.util.xml.dom.XmlInterner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PluginXmlParserTest {

  @Test
  fun `content module required-if-available attribute parsing`() {
    val xml = """
      <idea-plugin>
        <content>
          <module name="test.module.1" loading="optional" required-if-available="some.dependency"/>
          <module name="test.module.2" loading="required"/>
          <module name="test.module.3" required-if-available="another.dependency"/>
          <module name="test.module.4" required-if-available=""/>
        </content>
      </idea-plugin>
    """.trimIndent()

    val descriptor = parse(xml)

    assertThat(descriptor.contentModules).hasSize(4)

    // Test first module with required-if-available attribute
    val module1 = descriptor.contentModules[0]
    assertThat(module1.name).isEqualTo("test.module.1")
    assertThat(module1.loadingRule).isEqualTo(ModuleLoadingRule.OPTIONAL)
    assertThat(module1.requiredIfAvailable).isEqualTo("some.dependency")

    // Test second module without required-if-available attribute
    val module2 = descriptor.contentModules[1]
    assertThat(module2.name).isEqualTo("test.module.2")
    assertThat(module2.loadingRule).isEqualTo(ModuleLoadingRule.REQUIRED)
    assertThat(module2.requiredIfAvailable).isNull()

    // Test third module with required-if-available but default loading
    val module3 = descriptor.contentModules[2]
    assertThat(module3.name).isEqualTo("test.module.3")
    assertThat(module3.loadingRule).isEqualTo(ModuleLoadingRule.OPTIONAL)
    assertThat(module3.requiredIfAvailable).isEqualTo("another.dependency")

    // Test fourth module with empty value in required-if-available
    val module4 = descriptor.contentModules[3]
    assertThat(module4.name).isEqualTo("test.module.4")
    assertThat(module4.loadingRule).isEqualTo(ModuleLoadingRule.OPTIONAL)
    assertThat(module4.requiredIfAvailable).isNull()
  }

  private fun parse(xml: String): RawPluginDescriptor {
    val consumer = createTestConsumer()
    consumer.consume(xml.toByteArray(), "test-plugin.xml")
    return consumer.build()
  }

  private fun createTestConsumer(): PluginDescriptorFromXmlStreamConsumer {
    val context = object : PluginDescriptorReaderContext {
      override val interner: XmlInterner = NoOpXmlInterner
      override val isMissingIncludeIgnored: Boolean = true
    }
    return PluginDescriptorFromXmlStreamConsumer(context, null)
  }
}