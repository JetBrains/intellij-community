// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.PluginSpecBuilder
import com.intellij.platform.testFramework.plugins.buildDir
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.module
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import com.intellij.platform.testFramework.plugins.plugin as buildPlugin

internal class ContentModuleDependencyResolutionTest {
  init {
    PluginManagerCore.isUnitTestMode = true // FIXME git rid of this IJPL-220869
  }

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
    assertThat(dependency.name).isEqualTo("bar")
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
    assertThat(dependency.name).isEqualTo("platform")
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
    // namespaces are not active yet: (com.intellij.ide.plugins.PluginModuleId.useNamespaceInId)
    if (System.getProperty("intellij.platform.plugin.modules.use.namespace.in.id") == "true") {
      val foo = pluginSet.getEnabledModule("foo")
      val dependency = foo.moduleDependencies.modules.single()
      assertThat(dependency.name).isEqualTo("platform")
      assertThat(dependency.namespace).isNotEqualTo(PluginModuleId.JETBRAINS_NAMESPACE)
      val fooPlatform = pluginSet.getEnabledPlugin("foo").contentModules.first { it.moduleId.name == "platform" }
      assertThat(dependency.namespace).isEqualTo(fooPlatform.moduleId.namespace)
    } else {
      // FIXME 'foo' and 'core' conflict on 'platform' module while namespaces are not active, they should be both excluded,
      //  but it does not happen with old plugin init, 'foo' overwrites mapping for the module
      assertThat(pluginSet).doesNotHaveEnabledPlugins()
    }
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
    // namespaces are not active yet: (com.intellij.ide.plugins.PluginModuleId.useNamespaceInId)
    if (System.getProperty("intellij.platform.plugin.modules.use.namespace.in.id") == "true") {
      val bar = pluginSet.getEnabledModule("bar")
      val dependency = bar.moduleDependencies.modules.single()
      assertThat(dependency.name).isEqualTo("foo")
      assertThat(dependency.namespace).isEqualTo("bar_ns")
    } else {
      // FIXME 'bar' and 'core' conflict on 'bar' module while namespaces are not active, they should be both excluded,
      //  but it does not happen with old plugin init, 'bar' overwrites mapping for the module
      assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
      val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
      assertThat(errors.joinToString { it.reason?.logMessage ?: "" }).contains(
        "declares id 'foo' which conflicts with the same id from plugin 'bar'",
        "declares id 'foo' which conflicts with the same id from plugin 'core'"
      )
    }
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
