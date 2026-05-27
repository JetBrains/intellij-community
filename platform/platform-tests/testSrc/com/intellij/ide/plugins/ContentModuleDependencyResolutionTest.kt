// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetSpecBuilder
import com.intellij.platform.pluginSystem.testFramework.buildPluginSetState
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.module
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class ContentModuleDependencyResolutionTest {
  init {
    PluginManagerCore.isUnitTestMode = true // FIXME git rid of this IJPL-220869
  }

  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()
  private var loadingErrors: List<PluginLoadingError> = emptyList()

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
          module("platform") {
            moduleVisibility = ModuleVisibilityValue.PUBLIC
          }
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
          module("util") {}
        }
      }
      plugin("foo") {
        content {
          module("foo") {
            dependencies {
              module("platform")
              module("util")
            }
          }
          module("platform") {}
        }
        content(namespace = "custom") {
          module("util") {}
        }
      }
    }
    val foo = pluginSet.getEnabledModule("foo")
    assertThat(foo.moduleDependencies.modules).hasSize(2)
    val (dependency1, dependency2) = foo.moduleDependencies.modules
    assertThat(dependency1.name).isEqualTo("platform")
    assertThat(dependency1.namespace).isNotEqualTo(PluginModuleId.JETBRAINS_NAMESPACE)
    val fooPlatform = pluginSet.getEnabledPlugin("foo").contentModules.first { it.moduleId.name == "platform" }
    assertThat(dependency1.namespace).isEqualTo(fooPlatform.moduleId.namespace)
    assertThat(dependency2.name).isEqualTo("util")
    assertThat(dependency2.namespace).isEqualTo("custom")
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
    assertThat(dependency.name).isEqualTo("foo")
    assertThat(dependency.namespace).isEqualTo("bar_ns")
  }

  @Test
  fun `modules with same name in different namespaces`() {
    val pluginSet = buildPluginSet {
      plugin("foo") {
        content(namespace = "foo_ns") {
          module("foo") {
            dependencies {
              module("common")
            }
          }
          module("common") {}
        }
      }
      plugin("bar") {
        content(namespace = "bar_ns") {
          module("bar") {
            dependencies {
              module("common")
            }
          }
          module("common") {}
        }
      }
    }
    val fooModule = pluginSet.getEnabledModule("foo")
    assertThat(fooModule.moduleDependencies.modules.single()).isEqualTo(PluginModuleId("common", "foo_ns"))
    val barModule = pluginSet.getEnabledModule("bar")
    assertThat(barModule.moduleDependencies.modules.single()).isEqualTo(PluginModuleId("common", "bar_ns"))
  }

  @Test
  fun `modules with same name and different namespaces in the same plugin`() {
    val pluginSet = buildPluginSet {
      plugin("foo") {
        content(namespace = "namespace1") {
          module("foo1") {
            dependencies {
              module("foo2")
            }
          }
        }
        content(namespace = "namespace2") {
          module("foo2") {
            dependencies {
              module("foo3")
            }
          }
        }
        content {
          module("foo3") {}
        }
      }
    }
    assertThat(pluginSet).hasExactlyEnabledModulesWithoutMainDescriptors("foo1", "foo2", "foo3")
    val foo1Module = pluginSet.getEnabledModule("foo1")
    val foo2Module = pluginSet.getEnabledModule("foo2")
    val foo3Module = pluginSet.getEnabledModule("foo3")
    assertThat(foo1Module.moduleDependencies.modules.single()).isEqualTo(foo2Module.moduleId)
    assertThat(foo2Module.moduleDependencies.modules.single()).isEqualTo(foo3Module.moduleId)
  }

  private fun buildPluginSet(builder: PluginSetSpecBuilder.() -> Unit): PluginSet {
    val pluginsDirPath = inMemoryFs.fs.getPath("/").resolve("plugins")
    val state = buildPluginSetState(pluginsDirPath, builder = builder)
    loadingErrors = state.loadingErrors
    return state.pluginSet
  }
}
