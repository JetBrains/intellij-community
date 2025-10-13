// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.platform.plugins.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.*
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import com.intellij.platform.testFramework.plugins.plugin as buildPlugin

internal class ContentModuleDependencyResolutionTest {
  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  private val pluginDirPath get() = inMemoryFs.fs.getPath("/").resolve("plugin")

  @Test
  fun `reference to modules from the same plugin without namespace`() {
    val pluginSet = buildPluginSet {
      plugin("foo") {
        content {
          module("foo") {
            dependencies {
              module("bar")
            }
          }
          module("bar") {}
        }
      }
    }

    val foo = pluginSet.getEnabledModule("foo")
    val bar = pluginSet.getEnabledModule("bar")
    val dependency = foo.moduleDependencies.modules.single()
    assertThat(dependency.id).isEqualTo("bar")
    assertThat(dependency.namespace).isEqualTo(bar.moduleId.namespace)
  }

  @Test
  fun `fallback to jetbrains namespace by default`() {
    val pluginSet = buildPluginSet {
      plugin("core") {
        content(namespace = "jetbrains") {
          module("platform") {}
        }
      }
      plugin("foo") {
        content {
          module("foo") {
            dependencies {
              module("platform")
            }
          }
        }
      }
    }

    val dependency = pluginSet.getEnabledModule("foo").moduleDependencies.modules.single()
    assertThat(dependency.id).isEqualTo("platform")
    assertThat(dependency.namespace).isEqualTo(PluginModuleId.JETBRAINS_NAMESPACE)
  }

  @Test
  fun `prefer module from the same plugin in case of ambiguity`() {
    val pluginSet = buildPluginSet {
      plugin("core") {
        content(namespace = "jetbrains") {
          module("platform") {}
        }
      }
      plugin("foo") {
        content {
          module("foo") {
            dependencies {
              module("platform")
            }
          }
          module("platform") {}
        }
      }
    }
    val foo = pluginSet.getEnabledModule("foo")
    val dependency = foo.moduleDependencies.modules.single()
    assertThat(dependency.id).isEqualTo("platform")
    assertThat(dependency.namespace).isNotEqualTo(PluginModuleId.JETBRAINS_NAMESPACE)
    val fooPlatform = pluginSet.getEnabledPlugin("foo").contentModules.first { it.moduleId.id == "platform" }
    assertThat(dependency.namespace).isEqualTo(fooPlatform.moduleId.namespace)
  }

  @Test
  fun `honor explicit namespace`() {
    val pluginSet = buildPluginSet {
      plugin("core") {
        content(namespace = "jetbrains") {
          module("foo") {}
        }
      }
      plugin("foo") {
        content(namespace = "foo_ns") {
          module("foo") {}
        }
      }
      plugin("bar") {
        content(namespace = "bar_ns") {
          module("foo") {}
          module("bar") {
            dependencies {
              module("foo", namespace = "bar_ns")
            }
          }
        }
      }
    }
    val bar = pluginSet.getEnabledModule("bar")
    val dependency = bar.moduleDependencies.modules.single()
    assertThat(dependency.id).isEqualTo("foo")
    assertThat(dependency.namespace).isEqualTo("bar_ns")
  }

  private fun buildPluginSet(builder: PluginSetSpecBuilder.() -> Unit): PluginSet {
    builder(PluginSetSpecBuilder(pluginDirPath))
    return PluginSetTestBuilder.fromPath(pluginDirPath).build()
  }
}

private class PluginSetSpecBuilder(private val pluginsDirPath: Path) {
  fun plugin(id: String? = null, body: PluginSpecBuilder.() -> Unit) {
    val pluginSpec = if (id != null) {
      buildPlugin(id, body)
    }
    else {
      buildPlugin(body = body)
    }
    pluginSpec.buildDir(pluginsDirPath.resolve(pluginSpec.id!!))
  }
}
