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
          content(namespace = "jetbrains") {
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
          content(namespace = "foo_namespace") {
            module("foo.module") {
              moduleVisibility = ModuleVisibilityValue.INTERNAL
            }
          }
        }
        plugin("bar") {
          content {
            module("bar.module") {
              dependencies {
                module("foo.module", namespace = "foo_namespace")
              }
            }
          }
        }
      }
    }
    assertThat(exception.message).contains("depends on module 'foo.module' which is registered in 'foo' plugin with internal visibility in namespace 'foo_namespace'")
  }

  @Test
  fun `plugin descriptor without namespace cannot depend on internal module`() {
    val exception = assertErrorLogged<PluginException> {
      buildPluginSet {
        plugin("foo") {
          content(namespace = "foo_namespace") {
            module("foo.module") {
              moduleVisibility = ModuleVisibilityValue.INTERNAL
            }
          }
        }
        plugin("bar") {
          dependencies {
            module("foo.module", namespace = "foo_namespace")
          }
        }
      }
    }
    assertThat(exception.message).contains("depends on module 'foo.module' which is registered in 'foo' plugin with internal visibility in namespace 'foo_namespace'")
  }

  @Test
  fun `plugin descriptor can depend on internal module if it has content module from the same namespace`() {
    val pluginSet = buildPluginSet {
      plugin("foo") {
        content(namespace = "foo_namespace") {
          module("foo.module") {
            moduleVisibility = ModuleVisibilityValue.INTERNAL
          }
        }
      }
      plugin("bar") {
        dependencies {
          module("foo.module", namespace = "foo_namespace")
        }
        content(namespace = "foo_namespace") {
          module("bar.module") {}
        }
      }
    }
    assertThat(pluginSet).hasEnabledPlugins("foo", "bar")
  }

  @Test
  fun `dependency on internal module from plugin descriptor with a dummy content tag to specify namespace`() {
    val pluginSet = buildPluginSet {
      plugin("foo") {
        content(namespace = "foo_namespace") {
          module("foo.module") {
            moduleVisibility = ModuleVisibilityValue.INTERNAL
          }
        }
      }
      plugin("bar") {
        dependencies {
          module("foo.module", namespace = "foo_namespace")
        }
        body = "<content namespace=\"foo_namespace\"/>"
      }
    }
    assertThat(pluginSet).hasEnabledPlugins("foo", "bar")
  }

  private fun buildPluginSet(builder: PluginSetSpecBuilder.() -> Unit): PluginSet {
    val pluginsDirPath = inMemoryFs.fs.getPath("/").resolve("plugins")
    return com.intellij.platform.pluginSystem.testFramework.buildPluginSet(pluginsDirPath, builder = builder)
  }
}
