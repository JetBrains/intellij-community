// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.parser.impl

import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ParseContentAndXIncludesTest {
  @Test
  fun `parse content`() {
    val input = """
      <idea-plugin>
         <id>myId</id>
         <module value="myAlias"/>
         <content namespace="custom">
            <module name="module" loading="on-demand" required-if-available="backend"/>
         </content>
      </idea-plugin>
    """.trimIndent()
    val parseResult = parseContentAndXIncludes(input.toByteArray(), null)
    assertThat(parseResult.pluginId).isEqualTo("myId")
    assertThat(parseResult.pluginAliases).containsExactly("myAlias")
    assertThat(parseResult.contentModules).hasSize(1)
    assertThat(parseResult.contentModules[0].name).isEqualTo("module")
    assertThat(parseResult.contentModules[0].requiredIfAvailable).isEqualTo("backend")
    assertThat(parseResult.contentModules[0].loadingRule).isEqualTo(ModuleLoadingRuleValue.ON_DEMAND)
  }

  @Test
  fun `parse empty`() {
    val input = "<idea-plugin/>"
    val parseResult = parseContentAndXIncludes(input.toByteArray(), null)
    assertThat(parseResult.pluginId).isNull()
    assertThat(parseResult.contentModules).isEmpty()
  }
}
