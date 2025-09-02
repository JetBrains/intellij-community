// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.plugins.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.*
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.FileVisitResult

internal class PluginDependenciesTest {
  init {
    Logger.setUnitTestMode() // due to warnInProduction use in IdeaPluginDescriptorImpl
  }

  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  private val rootPath get() = inMemoryFs.fs.getPath("/")
  private val pluginDirPath get() = rootPath.resolve("plugin")

  @Test
  fun `plugin is loaded when depends dependency is resolved`() {
    bar()
    `foo depends bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo).hasDirectParentClassloaders(bar)
  }

  @Test
  fun `plugin is not loaded when depends dependency is not resolved`() {
    `foo depends bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
    assertNonOptionalDependenciesIds(pluginSet, "foo", "bar")
  }

  @Test
  fun `plugin is loaded when depends-optional dependency is resolved`() {
    `foo depends-optional bar`()
    bar()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo).hasDirectParentClassloaders(bar)
    assertNonOptionalDependenciesIds(pluginSet, "foo")
  }

  @Test
  fun `plugin is loaded when depends-optional dependency is not resolved`() {
    `foo depends-optional bar`()
    baz()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "baz")
    val (foo, baz) = pluginSet.getEnabledPlugins("foo", "baz")
    assertThat(foo).doesNotHaveDirectParentClassloaders(baz)
    assertThat(baz).doesNotHaveDirectParentClassloaders(foo)
  }

  @Test
  fun `plugin is loaded when plugin dependency is resolved, only main module is a classloader parent`() {
    `bar with optional module`()
    `foo plugin-dependency bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo)
      .hasDirectParentClassloaders(bar)
      .doesNotHaveTransitiveParentClassloaders(pluginSet.getEnabledModule("bar.module"))
  }

  @Test
  fun `plugin is not loaded when plugin dependency is not resolved`() {
    `foo plugin-dependency bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
  }

  @Test
  fun `v1 plugin gets v2 content module in classloader parents even without direct dependency if depends dependency is used`() {
    `foo depends bar`()
    `bar with optional module`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo).hasDirectParentClassloaders(bar, pluginSet.getEnabledModule("bar.module"))
  }

  @Test
  fun `v2 plugin dependency brings only the implicit main module in classloader parents`() {
    `foo plugin-dependency bar`()
    plugin("bar") {
      content {
        module("bar.optional", ModuleLoadingRule.OPTIONAL) { packagePrefix = "bar.optional" }
        module("bar.required", ModuleLoadingRule.REQUIRED) { packagePrefix = "bar.required" }
        module("bar.embedded", ModuleLoadingRule.EMBEDDED) { packagePrefix = "bar.embedded" }
      }
    }.buildDir(pluginDirPath.resolve("bar"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    val (opt, req, _) = pluginSet.getEnabledModules("bar.optional", "bar.required", "bar.embedded")
    assertThat(foo)
      .hasDirectParentClassloaders(bar)
      .doesNotHaveTransitiveParentClassloaders(opt, req)
  }

  @Test
  fun `plugin is not loaded if it has a depends dependency on v2 module`() {
    `bar with optional module`()
    plugin("foo") { depends("bar.module") }.buildDir(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is not loaded if it has a plugin dependency on v2 module`() {
    `bar with optional module`()
    plugin("foo") { dependencies { plugin("bar.module") } }.buildDir(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is loaded if it has a module dependency on v2 module`() {
    `bar with optional module`()
    plugin("foo") { dependencies { module("bar.module") } }.buildDir(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
  }

  @Test
  fun `plugin is not loaded if required module is not available`() {
    plugin("sample.plugin") {
      content {
        module("required.module", ModuleLoadingRule.REQUIRED) {
          packagePrefix = "required"
          dependencies {
            module("unknown")
          }
        }
      }
    }.buildDir(pluginDirPath.resolve("sample-plugin"))
    val result = buildPluginSet()
    assertThat(result).doesNotHaveEnabledPlugins()
    assertFirstErrorContains("sample.plugin", "requires plugin", "unknown")
  }
  
  @Test
  fun `plugin is not loaded if required module depends on disabled plugin`() {
    bar()
    plugin("sample.plugin") {
      content {
        module("required.module", ModuleLoadingRule.REQUIRED) {
          packagePrefix = "required"
          dependencies {
            plugin("bar")
          }
        }
      }
    }.buildDir(pluginDirPath.resolve("sample-plugin"))
    val result = buildPluginSet(disabledPluginIds = arrayOf("bar"))
    assertThat(result).doesNotHaveEnabledPlugins()
    assertFirstErrorContains("sample.plugin", "requires plugin", "bar")
    assertNonOptionalDependenciesIds(result, "sample.plugin", "bar")
  }
  
  @Test
  fun `plugin is not loaded if required module depends on a module from disabled plugin`() {
    `bar-plugin with module bar`()
    plugin("sample.plugin") {
      content {
        module("required.module", ModuleLoadingRule.REQUIRED) {
          packagePrefix = "required"
          dependencies {
            module("bar")
          }
        }
      }
    }.buildDir(pluginDirPath.resolve("sample-plugin"))
    val result = buildPluginSet(disabledPluginIds = arrayOf("bar-plugin"))
    assertThat(result).doesNotHaveEnabledPlugins()
    assertFirstErrorContains("sample.plugin", "requires plugin", "bar-plugin"/*, "to be enabled"*/) //todo fix not loading reason
    assertNonOptionalDependenciesIds(result, "sample.plugin", "bar-plugin")
  }

  private fun assertNonOptionalDependenciesIds(result: PluginSet, pluginId: String, vararg dependencyPluginId: String) {
    val actualDependencies = HashSet<String>()
    val pluginIdMap = result.buildPluginIdMap()
    val contentModuleIdMap = result.buildContentModuleIdMap()
    PluginManagerCore.processAllNonOptionalDependencyIds(result.getPlugin(pluginId), pluginIdMap, contentModuleIdMap) {
      actualDependencies.add(it.idString)
      FileVisitResult.CONTINUE
    }
    assertThat(actualDependencies).containsExactlyInAnyOrder(*dependencyPluginId)
  }

  private fun assertFirstErrorContains(vararg messagePart: String) {
    val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
    assertThat(errors).isNotEmpty
    assertThat(errors.first().get().toString()).contains(*messagePart)
  }

  @Test
  fun `embedded content module uses same classloader as the main module`() {
    val samplePluginDir = pluginDirPath.resolve("sample-plugin")
    plugin("sample.plugin") {
      content {
        module("embedded.module", ModuleLoadingRule.EMBEDDED) {
          packagePrefix = "embedded"
          isSeparateJar = true
        }
        module("required.module", ModuleLoadingRule.REQUIRED) {
          packagePrefix = "required"
          isSeparateJar = true
        }
        module("optional.module", ModuleLoadingRule.OPTIONAL) {
          packagePrefix = "optional"
        }
      }
    }.buildDir(samplePluginDir)
    val result = buildPluginSet()
    assertThat(result).hasExactlyEnabledPlugins("sample.plugin")
    val mainClassLoader = result.getEnabledPlugin("sample.plugin").pluginClassLoader
    val embeddedModuleClassLoader = result.getEnabledModule("embedded.module").pluginClassLoader
    assertThat(embeddedModuleClassLoader).isSameAs(mainClassLoader)
    assertThat((mainClassLoader as PluginClassLoader).files).containsExactly(
      samplePluginDir.resolve("lib/sample.plugin.jar"),
      samplePluginDir.resolve("lib/modules/embedded.module.jar"),
    )
    val requiredModuleClassLoader = result.getEnabledModule("required.module").pluginClassLoader
    assertThat(requiredModuleClassLoader).isNotSameAs(mainClassLoader)
    val optionalModuleClassLoader = result.getEnabledModule("optional.module").pluginClassLoader
    assertThat(optionalModuleClassLoader).isNotSameAs(mainClassLoader)
  }
  
  @Test
  fun `embedded and required content modules in the core plugin`() {
    val corePluginDir = pluginDirPath.resolve("core")
    plugin(PluginManagerCore.CORE_PLUGIN_ID) {
      content {
        module("embedded.module", ModuleLoadingRule.EMBEDDED) { packagePrefix = "embedded" }
        module("required.module", ModuleLoadingRule.REQUIRED) { packagePrefix = "required" }
      }
    }.buildDir(corePluginDir)
    val result = buildPluginSet()
    assertThat(result).hasExactlyEnabledPlugins(PluginManagerCore.CORE_PLUGIN_ID)
    val mainClassLoader = result.getEnabledPlugin(PluginManagerCore.CORE_PLUGIN_ID).pluginClassLoader
    val embeddedModuleClassLoader = result.getEnabledModule("embedded.module").pluginClassLoader
    assertThat(embeddedModuleClassLoader).isSameAs(mainClassLoader)
    val requiredModuleClassLoader = result.getEnabledModule("required.module").pluginClassLoader
    assertThat(requiredModuleClassLoader).isNotSameAs(mainClassLoader)
  }
  
  @Test
  fun `required content module with unresolved dependency in the core plugin`() {
    val corePluginDir = pluginDirPath.resolve("core")
    plugin(PluginManagerCore.CORE_PLUGIN_ID) {
      content {
        module("required.module", ModuleLoadingRule.REQUIRED) {
          packagePrefix = "required"
          dependencies { module("unresolved") }
        }
      }
    }.buildDir(corePluginDir)
    buildPluginSet()
    assertFirstErrorContains("requires plugin", "unresolved")
  }

  @Test
  fun `embedded content module without package prefix`() {
    val samplePluginDir = pluginDirPath.resolve("sample-plugin")
    plugin("sample.plugin") {
      content {
        module("embedded.module", ModuleLoadingRule.EMBEDDED) {}
      }
    }.buildDir(samplePluginDir)
    val result = buildPluginSet()
    assertThat(result).hasExactlyEnabledPlugins("sample.plugin")
    val mainClassLoader = result.getEnabledPlugin("sample.plugin").pluginClassLoader
    val embeddedModuleClassLoader = result.getEnabledModule("embedded.module").pluginClassLoader
    assertThat(embeddedModuleClassLoader).isSameAs(mainClassLoader)
  }

  @Test
  fun `dependencies of embedded content module are added to the main class loader`() {
    plugin("dep") {}.buildDir(pluginDirPath.resolve("dep"))
    plugin("sample.plugin") {
      content {
        module("embedded.module", ModuleLoadingRule.EMBEDDED) {
          packagePrefix = "embedded"
          dependencies { plugin("dep") }
        }
      }
    }.buildDir(pluginDirPath.resolve("sample-plugin"))
    val result = buildPluginSet()
    assertThat(result).hasExactlyEnabledPlugins("sample.plugin", "dep")
    val (sample, dep) = result.getEnabledPlugins("sample.plugin", "dep")
    assertThat(sample).hasDirectParentClassloaders(dep)
  }

  @Test
  fun `dependencies between plugin modules`() {
    plugin("sample.plugin") {
      content {
        module("embedded.module", ModuleLoadingRule.EMBEDDED) {
          packagePrefix = "embedded"
        }
        module("required.module", ModuleLoadingRule.REQUIRED) {
          packagePrefix = "required"
          dependencies {
            module("embedded.module")
          }
        }
        module("required2.module", ModuleLoadingRule.REQUIRED) {
          packagePrefix = "required2"
          dependencies {
            module("required.module")
          }
        }
      }
    }.buildDir(pluginDirPath.resolve("sample-plugin"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledModulesWithoutMainDescriptors("embedded.module", "required.module", "required2.module")
    val (req, req2, embed) = pluginSet.getEnabledModules("required.module", "required2.module", "embedded.module")
    assertThat(req2).hasDirectParentClassloaders(req)
    assertThat(req).hasDirectParentClassloaders(embed)
  }

  @Test
  fun `content module in separate JAR`() {
    val pluginDir = pluginDirPath.resolve("sample-plugin")
    plugin("sample.plugin") {
      content {
        module("dep") { isSeparateJar = true }
      }
    }.buildDir(pluginDir)
    val result = buildPluginSet()
    assertThat(result).hasExactlyEnabledPlugins("sample.plugin")
    assertThat(result).hasExactlyEnabledModulesWithoutMainDescriptors("dep")
    val depModuleDescriptor = result.getEnabledModule("dep")
    assertThat(depModuleDescriptor.jarFiles).containsExactly(pluginDir.resolve("lib/modules/dep.jar"))
  }

  @Test
  fun `plugin is not loaded if it is expired`() {
    foo()
    bar()
    val pluginSet = buildPluginSet(expiredPluginIds = arrayOf("foo"))
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is not loaded when it has a disabled depends dependency`() {
    `foo depends bar`()
    bar()
    val pluginSet = buildPluginSet(disabledPluginIds = arrayOf("bar"))
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
  }

  @Test
  fun `plugin is not loaded when it has a transitive disabled depends dependency`() {
    plugin("com.intellij.gradle") {}.buildDir(pluginDirPath.resolve("intellij.gradle"))
    plugin("org.jetbrains.plugins.gradle") {
      implementationDetail = true
      depends("com.intellij.gradle")
    }.buildDir(pluginDirPath.resolve("intellij.gradle.java"))
    plugin("org.jetbrains.plugins.gradle.maven") {
      implementationDetail = true
      depends("org.jetbrains.plugins.gradle")
    }.buildDir(pluginDirPath.resolve("intellij.gradle.java.maven"))
    val pluginSet = buildPluginSet(disabledPluginIds = arrayOf("com.intellij.gradle"))
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
  }

  @Test
  fun `plugin is loaded when it has a disabled depends-optional dependency`() {
    `foo depends-optional bar`()
    bar()
    val pluginSet = buildPluginSet(disabledPluginIds = arrayOf("bar"))
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
  }

  @Test
  fun `plugin is not loaded when it depends on module from disabled plugin`() {
    `bar-plugin with module bar`()
    `foo module-dependency bar`()
    val pluginSet = buildPluginSet(disabledPluginIds = arrayOf("bar-plugin"))
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
    assertFirstErrorContains("foo", "requires plugin", "bar-plugin", "to be enabled")
  }
  
  @Test
  fun `plugin is not loaded when it depends on module from expired plugin`() {
    `bar-plugin with module bar`()
    `foo module-dependency bar`()
    val pluginSet = buildPluginSet(expiredPluginIds = arrayOf("bar-plugin"))
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
    assertFirstErrorContains("foo", "requires plugin", "bar-plugin", "to be installed")
  }
  
  @Test
  fun `plugin is not loaded when it depends on unknown module`() {
    `foo module-dependency bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
    assertFirstErrorContains("foo", "requires plugin", "bar", "to be installed")
  }

  @Test
  fun `plugin is loaded when it has a depends dependency on plugin alias`() {
    `foo depends bar`()
    `baz with alias bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "baz")
  }

  @Test
  fun `plugin is loaded when it has a plugin dependency on plugin alias`() {
    `foo plugin-dependency bar`()
    `baz with alias bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "baz")
  }

  @Test
  fun `plugin is not loaded when it has a module dependency on plugin alias`() {
    `foo module-dependency bar`()
    `baz with alias bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("baz")
  }

  @Test
  fun `plugin is loaded if it has a depends dependency on plugin alias that is placed in optional v2 module, only the v2 module is a classloader parent`() {
    `baz with an optional module which has an alias bar and package prefix`()
    `foo depends bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "baz")
    val (foo, baz) = pluginSet.getEnabledPlugins("foo", "baz")
    val bazModule = pluginSet.getEnabledModule("baz.module")
    assertThat(foo)
      .hasDirectParentClassloaders(bazModule)
      .doesNotHaveDirectParentClassloaders(baz)
      .hasTransitiveParentClassloaders(baz) // only because the module is optional
  }

  @Test
  fun `plugin is loaded if it has a plugin dependency on plugin alias that is placed in optional v2 module, only the v2 module is a classloader parent`() {
    `baz with an optional module which has an alias bar and package prefix`()
    `foo plugin-dependency bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "baz")
    val (foo, baz) = pluginSet.getEnabledPlugins("foo", "baz")
    val bazModule = pluginSet.getEnabledModule("baz.module")
    assertThat(foo)
      .hasDirectParentClassloaders(bazModule)
      .doesNotHaveDirectParentClassloaders(baz)
      .hasTransitiveParentClassloaders(baz) // only because the module is optional
  }

  @Test
  fun `plugin is not loaded if it has a module dependency on plugin alias that is placed in v2 module`() {
    `baz with an optional module which has an alias bar and package prefix`()
    `foo module-dependency bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("baz")
  }

  @Test
  fun `plugin is loaded if it has a depends dependency on plugin alias that is placed in required v2 module, only the v2 module is a classloader parent`() {
    `baz with a required module which has an alias bar and package prefix`()
    `foo depends bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "baz")
    val (foo, baz) = pluginSet.getEnabledPlugins("foo", "baz")
    val bazModule = pluginSet.getEnabledModule("baz.module")
    assertThat(bazModule).doesNotHaveTransitiveParentClassloaders(baz)
    assertThat(foo)
      .hasDirectParentClassloaders(bazModule)
      .doesNotHaveTransitiveParentClassloaders(baz)
  }
  
  @Test
  fun `plugin is loaded if it has a depends dependency on plugin alias that is placed in required v2 module and other modules affects sorting`() {
    plugin("baz") {
      content {
        module("baz.module", ModuleLoadingRule.REQUIRED) {
          packagePrefix = "baz.module"
          pluginAlias("bar")
        }
      }
      depends("additional")
    }.buildDir(pluginDirPath.resolve("baz"))
    `foo depends bar`()
    /* an additional module is used to ensure that in the sorted modules list the main module of 'baz' plugin is moved to the end of the 
       list if no explicit edge from 'foo' plugin to it is added */
    plugin("additional") {}.buildDir(pluginDirPath.resolve("additional"))
    
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "baz", "additional")
  }

  @Test
  fun `plugin is loaded if it has a plugin dependency on plugin alias that is placed in required v2 module, only the v2 module is a classloader parent`() {
    `baz with a required module which has an alias bar and package prefix`()
    `foo plugin-dependency bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "baz")
    val (foo, baz) = pluginSet.getEnabledPlugins("foo", "baz")
    val bazModule = pluginSet.getEnabledModule("baz.module")
    assertThat(foo)
      .hasDirectParentClassloaders(bazModule)
      .doesNotHaveTransitiveParentClassloaders(baz)
  }

  @Test
  fun `plugin is not loaded if it has a module dependency on a plugin without package prefix`() {
    bar()
    `foo module-dependency bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is loaded if it has a module dependency on a plugin with package prefix`() {
    plugin("bar") { packagePrefix = "idk" }.buildDir(pluginDirPath.resolve("bar"))
    `foo module-dependency bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
  }

  @Test
  fun `plugin is not loaded if it has a module dependency on plugin alias of a plugin without package prefix`() {
    `baz with alias bar`()
    `foo module-dependency bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("baz")
  }

  @Test
  fun `plugin is not loaded if it has a module dependency on plugin alias of a plugin with package prefix`() {
    plugin("baz") {
      packagePrefix = "idk"
      pluginAlias("bar")
    }.buildDir(pluginDirPath.resolve("baz"))
    `foo module-dependency bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("baz")
  }

  @Test
  fun `plugin is not loaded if it has a depends dependency on v2 module with package prefix`() {
    `bar with optional module`()
    plugin("foo") { depends("bar.module") }.buildDir(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is not loaded if it has a plugin dependency on v2 module with package prefix`() {
    `bar with optional module`()
    plugin("foo") { dependencies { plugin("bar.module") } }.buildDir(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is not loaded if it is incompatible with another plugin and they both contain the same module`() {
    plugin("com.intellij.java") {
      content {
        module("com.intellij.java.debugger.frontend", ModuleLoadingRule.EMBEDDED) {
          packagePrefix = "com.intellij.java.debugger.frontend"
        }
      }
    }.buildDir(pluginDirPath.resolve("intellij.java"))

    plugin("com.intellij.java.frontend") {
      content {
        module("com.intellij.java.debugger.frontend") {
          packagePrefix = "com.intellij.java.debugger.frontend"
        }
      }
      incompatibleWith = listOf("com.intellij.java")
    }.buildDir(pluginDirPath.resolve("intellij.java.frontend"))

    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("com.intellij.java")
  }

  @Test
  fun `plugin is loaded if it has a module dependency on v2 module with slash in its name`() {
    plugin("bar") {
      content {
        module("bar/module", ModuleLoadingRule.REQUIRED) { packagePrefix = "bar.module" }
      }
    }.buildDir(pluginDirPath.resolve("bar"))
    plugin("foo") { dependencies { module("bar/module") } }.buildDir(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    assertThat(pluginSet).hasExactlyEnabledModulesWithoutMainDescriptors("bar/module")
  }

  @Test
  fun `plugin is not loaded if it has a module dependency on v2 module with slash in its name but dependency has a dot instead`() {
    plugin("bar") {
      content {
        module("bar/module", ModuleLoadingRule.REQUIRED) { packagePrefix = "bar.module" }
      }
    }.buildDir(pluginDirPath.resolve("bar"))
    plugin("foo") { dependencies { module("bar.module") } }.buildDir(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is loaded when it has no dependency on core plugin, but core is a classloader parent excluding its content modules`() {
    plugin(PluginManagerCore.CORE_PLUGIN_ID) {
      pluginAlias("com.intellij.modules.platform")
      content {
        module("embedded.module", ModuleLoadingRule.EMBEDDED) { packagePrefix = "embedded" }
        module("required.module", ModuleLoadingRule.REQUIRED) { packagePrefix = "required" }
        module("optional.module", ModuleLoadingRule.OPTIONAL) { packagePrefix = "optional" }
      }
    }.buildDir(pluginDirPath.resolve("core"))
    plugin("foo") {}.buildDir(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins(PluginManagerCore.CORE_PLUGIN_ID, "foo")
    val (core, foo) = pluginSet.getEnabledPlugins(PluginManagerCore.CORE_PLUGIN_ID, "foo")
    val (opt, req, emb) = pluginSet.getEnabledModules("optional.module", "required.module", "embedded.module")
    assertThat(foo).doesNotHaveDirectParentClassloaders(core, opt, req, emb)
      .hasTransitiveParentClassloaders(core, emb)
      .doesNotHaveTransitiveParentClassloaders(opt, req)
  }

  @Test
  fun `plugin is loaded when it has a plugin dependency on core plugin, its content modules are not in classloader parents`() {
    plugin(PluginManagerCore.CORE_PLUGIN_ID) {
      content {
        module("embedded.module", ModuleLoadingRule.EMBEDDED) { packagePrefix = "embedded" }
        module("required.module", ModuleLoadingRule.REQUIRED) { packagePrefix = "required" }
        module("optional.module", ModuleLoadingRule.OPTIONAL) { packagePrefix = "optional" }
      }
    }.buildDir(pluginDirPath.resolve("core"))
    plugin("foo") {
      dependencies { plugin(PluginManagerCore.CORE_PLUGIN_ID) }
    }.buildDir(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins(PluginManagerCore.CORE_PLUGIN_ID, "foo")
    val (core, foo) = pluginSet.getEnabledPlugins(PluginManagerCore.CORE_PLUGIN_ID, "foo")
    val (opt, req, emb) = pluginSet.getEnabledModules("optional.module", "required.module", "embedded.module")
    assertThat(foo).doesNotHaveDirectParentClassloaders(core, opt, req, emb)
      .hasTransitiveParentClassloaders(core, emb)
      .doesNotHaveTransitiveParentClassloaders(opt, req)
  }

  @Test
  fun `plugin is loaded when it has a module dependency on content module of core plugin`() {
    plugin(PluginManagerCore.CORE_PLUGIN_ID) {
      content {
        module("embedded.module", ModuleLoadingRule.EMBEDDED) { packagePrefix = "embedded" }
        module("required.module", ModuleLoadingRule.REQUIRED) { packagePrefix = "required" }
        module("optional.module", ModuleLoadingRule.OPTIONAL) { packagePrefix = "optional" }
      }
    }.buildDir(pluginDirPath.resolve("core"))
    plugin("foo") {
      dependencies { module("optional.module") }
    }.buildDir(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins(PluginManagerCore.CORE_PLUGIN_ID, "foo")
    val (core, foo) = pluginSet.getEnabledPlugins(PluginManagerCore.CORE_PLUGIN_ID, "foo")
    val (opt, req, emb) = pluginSet.getEnabledModules("optional.module", "required.module", "embedded.module")
    assertThat(foo)
      .hasDirectParentClassloaders(opt)
      .doesNotHaveDirectParentClassloaders(core, req, emb)
      .hasTransitiveParentClassloaders(core, emb)
      .doesNotHaveTransitiveParentClassloaders(req)
  }

  @Test
  fun `transitive optional depends is allowed`() {
    foo()
    baz()
    plugin("bar") {
      depends("foo", configFile = "foo.xml") {
        depends("baz", configFile = "baz.xml") {
          appService("service")
        }
      }
    }.buildDir(pluginDirPath.resolve("bar"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar", "foo", "baz")
    val bar = pluginSet.getEnabledPlugin("bar")
    val sub = bar.dependencies[0].subDescriptor!!
    val subsub = sub.dependencies[0].subDescriptor!!
    assertThat(subsub).hasExactlyApplicationServices("service")
  }

  @Test
  fun `content module can't have depends dependencies`() {
    foo()
    plugin("bar") {
      content {
        module("content.module", ModuleLoadingRule.REQUIRED) {
          depends("foo")
          isSeparateJar = true
        }
      }
    }.buildDir(pluginDirPath.resolve("bar"))
    val msg = LoggedErrorProcessor.executeAndReturnLoggedError {
      assertThatThrownBy {
        buildPluginSet()
      }.hasMessageContainingAll("content.module", "shouldn't have plugin dependencies", "foo")
    }
    assertThat(msg).hasMessageContainingAll("content module", "content.module", "bar", "element 'depends'")
  }

  @Test
  fun `dependencies on specific content modules extracted in core plugin are added automatically`() {
    plugin(PluginManagerCore.CORE_PLUGIN_ID) {
      pluginAlias("com.intellij.modules.platform")
      pluginAlias("com.intellij.modules.lang")
      pluginAlias("com.intellij.modules.vcs")
      content {
        module("intellij.platform.tasks.impl") { packagePrefix = "com.intellij.tasks.impl" }
      }
    }.buildDir(pluginDirPath.resolve("core"))
    plugin("with-depends") {
      depends("com.intellij.modules.platform")
    }.buildDir(pluginDirPath.resolve("with-depends"))
    plugin("with-depends-on-lang") {
      depends("com.intellij.modules.lang")
    }.buildDir(pluginDirPath.resolve("with-depends-on-lang"))
    plugin("with-dependencies") {
      dependencies { plugin("com.intellij.modules.platform") }
    }.buildDir(pluginDirPath.resolve("with-dependencies"))
    plugin("with-depends-on-vcs") {
      depends("com.intellij.modules.vcs")
    }.buildDir(pluginDirPath.resolve("with-depends-on-vcs"))
    val pluginSet = buildPluginSet()
    val (withDepends, withDependsOnLang, withDependencies, withDependsOnVcs) = 
      pluginSet.getEnabledPlugins("with-depends", "with-depends-on-lang", "with-dependencies", "with-depends-on-vcs")
    val tasks = pluginSet.getEnabledModule("intellij.platform.tasks.impl")
    assertThat(withDepends).hasDirectParentClassloaders(tasks)
    assertThat(withDependsOnLang).hasDirectParentClassloaders(tasks)
    assertThat(withDependencies).hasDirectParentClassloaders()
    assertThat(withDependsOnVcs).hasDirectParentClassloaders()
  } 
  
  @Test
  fun `optional depends descriptor may have module dependency, but it's disregarded`() {
    foo()
    `baz with an optional module which has a package prefix`()
    plugin("bar") {
      depends("foo", configFile = "foo.xml") {
        packagePrefix = "foo.baz"
        dependencies {
          module("baz.module")
        }
      }
    }.buildDir(pluginDirPath.resolve("bar"))
    val (pluginSet, err) = runAndReturnWithLoggedError { buildPluginSet() }
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar", "foo", "baz")
    val (bar, foo, baz) = pluginSet.getEnabledPlugins("bar", "foo", "baz")
    val bazModule = pluginSet.getEnabledModule("baz.module")
    val barSub = bar.dependencies[0].subDescriptor!!
    assertThat(barSub.pluginClassLoader).isEqualTo(bar.pluginClassLoader)
    assertThat(barSub)
      .hasDirectParentClassloaders(foo)
      .doesNotHaveTransitiveParentClassloaders(baz, bazModule)
    assertThat(barSub.moduleDependencies.modules).hasSize(1)
    assertThat(err).hasMessageContainingAll("'depends' sub-descriptor", "bar", "<dependencies><module>")
  }

  @Test
  fun `optional content modules implicitly depend on main module, while required do not`() {
    plugin("foo") {
      content {
        module("embedded.module", ModuleLoadingRule.EMBEDDED) { packagePrefix = "embedded" }
        module("required.module", ModuleLoadingRule.REQUIRED) { packagePrefix = "required" }
        module("optional.module", ModuleLoadingRule.OPTIONAL) { packagePrefix = "optional" }
      }
    }.buildDir(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
    val foo = pluginSet.getEnabledPlugin("foo")
    val (opt, req, emb) = pluginSet.getEnabledModules("optional.module", "required.module", "embedded.module")
    assertThat(emb.pluginClassLoader).isSameAs(foo.pluginClassLoader)
    assertThat(foo)
      .doesNotHaveTransitiveParentClassloaders(req, opt)
    assertThat(req)
      .doesNotHaveTransitiveParentClassloaders(foo, opt)
    assertThat(opt)
      .hasDirectParentClassloaders(foo)
      .doesNotHaveTransitiveParentClassloaders(req)
  }

  private fun foo() = plugin("foo") {}.buildDir(pluginDirPath.resolve("foo"))
  private fun `foo depends bar`() = plugin("foo") { depends("bar") }.buildDir(pluginDirPath.resolve("foo"))
  private fun `foo depends-optional bar`() = plugin("foo") {
    depends("bar", configFile = "bar.xml") { actions = "" }
  }.buildDir(pluginDirPath.resolve("foo"))
  private fun `foo plugin-dependency bar`() = plugin("foo") {
    dependencies { plugin("bar") }
  }.buildDir(pluginDirPath.resolve("foo"))
  private fun `foo module-dependency bar`() = plugin("foo") {
    dependencies { module("bar") }
  }.buildDir(pluginDirPath.resolve("foo"))
  private fun `bar-plugin with module bar`() = plugin("bar-plugin") {
    content {
      module("bar") {}
    }
  }.buildDir(pluginDirPath.resolve("bar-plugin"))


  private fun bar() = plugin("bar") {}.buildDir(pluginDirPath.resolve("bar"))
  private fun `bar with optional module`() = plugin("bar") {
    content {
      module("bar.module") {
        packagePrefix = "bar.module"
      }
    }
  }.buildDir(pluginDirPath.resolve("bar"))

  private fun baz() = plugin("baz") {}.buildDir(pluginDirPath.resolve("baz"))
  private fun `baz with alias bar`() = plugin("baz") {
    pluginAlias("bar")
  }.buildDir(pluginDirPath.resolve("baz"))
  private fun `baz with an optional module which has a package prefix`() = plugin("baz") {
    content {
      module("baz.module") { packagePrefix = "baz.module" }
    }
  }.buildDir(pluginDirPath.resolve("baz"))
  private fun `baz with an optional module which has an alias bar and package prefix`() = plugin("baz") {
    content {
      module("baz.module") {
        packagePrefix = "baz.module"
        pluginAlias("bar")
      }
    }
  }.buildDir(pluginDirPath.resolve("baz"))
  private fun `baz with a required module which has an alias bar and package prefix`() = plugin("baz") {
    content {
      module("baz.module", ModuleLoadingRule.REQUIRED) {
        packagePrefix = "baz.module"
        pluginAlias("bar")
      }
    }
  }.buildDir(pluginDirPath.resolve("baz"))

  private fun buildPluginSet(expiredPluginIds: Array<String> = emptyArray(), disabledPluginIds: Array<String> = emptyArray()) =
    PluginSetTestBuilder.fromPath(pluginDirPath)
      .withExpiredPlugins(*expiredPluginIds)
      .withDisabledPlugins(*disabledPluginIds)
      .build()
}
