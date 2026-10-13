// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.elements.xmlValue
import com.intellij.platform.testFramework.loadDescriptorInTest
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestApplication
internal class PluginUiModelAdapterTest {
  @TempDir
  lateinit var pluginsDir: Path

  @TestFactory
  fun `module loading rule survives a DTO round trip`(): List<DynamicTest> {
    return ModuleLoadingRuleValue.entries.map { loadingRule ->
      dynamicTest(loadingRule.name) {
        val descriptor = loadDescriptorInTest(
          plugin("test.plugin") {
            content {
              module("test.module", loadingRule = loadingRule) {}
            }
          },
          pluginsDir.resolve(loadingRule.name),
        )

        val contentModule = descriptor.toPluginContentModules().single()
        val restoredLoadingRule = ModuleLoadingRuleValue.entries.find { it.xmlValue == contentModule.loadingRule }

        assertThat(restoredLoadingRule).isEqualTo(loadingRule)
      }
    }
  }
}
