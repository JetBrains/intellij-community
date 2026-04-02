// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.diagnostic.PluginException
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetSpecBuilder
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.module
import com.intellij.testFramework.assertErrorLogged
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class ContentModuleVisibilityCheckTest {
  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  @Test
  fun `module cannot depend on private module from other plugin`() {
    val exception = assertErrorLogged<PluginException> {
      buildPluginSet {
        plugin("foo") {
          content {
            module("foo.module") {}
          }
        }
        plugin("bar") {
          content {
            module("bar.module") {
              dependencies {
                module("foo.module")
              }
            }
          }
        }
      }
    }
    assertThat(exception.message).contains("it depends on module 'foo.module' which has private visibility in 'foo' plugin")
  }

  @Test
  fun `module cannot depend on internal module from other namespace`() {
    val exception = assertErrorLogged<PluginException> {
      buildPluginSet {
        plugin("foo") {
          content(namespace = "foo.namespace") {
            module("foo.module") {
              moduleVisibility = ModuleVisibilityValue.INTERNAL
            }
          }
        }
        plugin("bar") {
          content {
            module("bar.module") {
              dependencies {
                module("foo.module")
              }
            }
          }
        }
      }
    }
    assertThat(exception.message).contains("depends on module 'foo.module' which is registered in 'foo' plugin with internal visibility in namespace 'foo.namespace'")
  }

  private fun buildPluginSet(builder: PluginSetSpecBuilder.() -> Unit): PluginSet {
    val pluginsDirPath = inMemoryFs.fs.getPath("/").resolve("plugins")
    return com.intellij.platform.pluginSystem.testFramework.buildPluginSet(pluginsDirPath, builder)
  }
}
