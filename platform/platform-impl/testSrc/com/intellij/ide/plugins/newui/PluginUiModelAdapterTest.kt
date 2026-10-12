// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.testFramework.loadDescriptorInTest
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestApplication
internal class PluginUiModelAdapterTest {
  @TempDir
  lateinit var pluginsDir: Path

  @Test
  fun `content modules use XML loading rules`() {
    val descriptor = loadDescriptorInTest(
      plugin("test.plugin") {
        content {
          module("required.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
          module("embedded.module", loadingRule = ModuleLoadingRuleValue.EMBEDDED) {}
          module("optional.module", loadingRule = ModuleLoadingRuleValue.OPTIONAL) {}
          module("on.demand.module", loadingRule = ModuleLoadingRuleValue.ON_DEMAND) {}
        }
      },
      pluginsDir,
    )

    assertThat(descriptor.toPluginContentModules().map { it.moduleName to it.loadingRule }).containsExactly(
      "required.module" to "required",
      "embedded.module" to "embedded",
      "optional.module" to "optional",
      "on.demand.module" to "on-demand",
    )
  }
}
