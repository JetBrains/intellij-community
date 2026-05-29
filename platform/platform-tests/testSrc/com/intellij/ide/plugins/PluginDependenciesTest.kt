// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.applicationServiceImpl
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.platform.testFramework.plugins.pluginAlias
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.FileVisitResult

internal class PluginDependenciesTest {
  init {
    Logger.setUnitTestMode() // due to warnInProduction use in IdeaPluginDescriptorImpl
    PluginManagerCore.isUnitTestMode = true // FIXME git rid of this IJPL-220869
  }

  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  private val rootPath get() = inMemoryFs.fs.getPath("/")
  private val pluginDirPath get() = rootPath.resolve("plugin")
  private var loadingErrors: List<PluginLoadingError> = emptyList()

  @Test
  fun `plugin is loaded when depends dependency is resolved`() {
    bar()
    `foo depends bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo).hasExactDirectParentClassloaders(bar)
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
    assertThat(foo).hasExactDirectParentClassloaders(bar)
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
  fun `optional config for unresolved dependency is not marked for loading`() {
    foo()
    plugin("bar") {
      depends("foo", configFile = "foo.xml") { actions = "" }
      depends("baz", configFile = "baz.xml") { actions = "" }
    }.installAt(pluginDirPath)

    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar", "foo")

    val bar = pluginSet.findEnabledPlugin(PluginId.getId("bar")) as PluginMainDescriptor
    val fooDescriptor = bar.dependencies.first { it.pluginId == PluginId.getId("foo") }.subDescriptor!!
    val bazDescriptor = bar.dependencies.first { it.pluginId == PluginId.getId("baz") }.subDescriptor!!
    assertThat(fooDescriptor.isMarkedForLoading).isTrue
    assertThat(bazDescriptor.isMarkedForLoading).isFalse
  }

  @Test
  fun `plugin is loaded when plugin dependency is resolved, only main module is a classloader parent`() {
    `bar with optional module`()
    `foo plugin-dependency bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo)
      .hasExactDirectParentClassloaders(bar)
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
    assertThat(foo).hasExactDirectParentClassloaders(bar, pluginSet.getEnabledModule("bar.module"))
  }

  @Test
  fun `v2 plugin dependency brings only the implicit main module in classloader parents`() {
    `foo plugin-dependency bar`()
    plugin("bar") {
      content {
        module("bar.optional", ModuleLoadingRuleValue.OPTIONAL) { packagePrefix = "bar.optional" }
        module("bar.required", ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "bar.required" }
        module("bar.embedded", ModuleLoadingRuleValue.EMBEDDED) { packagePrefix = "bar.embedded" }
      }
    }.installAt(pluginDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    val (opt, req, _) = pluginSet.getEnabledModules("bar.optional", "bar.required", "bar.embedded")
    assertThat(foo)
      .hasExactDirectParentClassloaders(bar)
      .doesNotHaveTransitiveParentClassloaders(opt, req)
  }

  @Test
  fun `plugin is not loaded if it has a depends dependency on v2 module`() {
    `bar with optional module`()
    plugin("foo") { depends("bar.module") }.installAt(pluginDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is not loaded if it has a plugin dependency on v2 module`() {
    `bar with optional module`()
    plugin("foo") { dependencies { plugin("bar.module") } }.installAt(pluginDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is loaded if it has a module dependency on v2 module`() {
    `bar with optional module`()
    plugin("foo") { dependencies { module("bar.module") } }.installAt(pluginDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
  }

  @Test
  fun `plugin is not loaded if required module is not available`() {
    plugin("sample.plugin") {
      content {
        module("required.module", ModuleLoadingRuleValue.REQUIRED) {
          packagePrefix = "required"
          dependencies {
            module("unknown")
          }
        }
      }
    }.installAt(pluginDirPath)
    val result = buildPluginSet()
    assertThat(result).doesNotHaveEnabledPlugins()
    assertFirstErrorContains("sample.plugin", "requires plugin", "unknown")
  }
  
  @Test
  fun `plugin is not loaded if required module depends on disabled plugin`() {
    bar()
    plugin("sample.plugin") {
      content {
        module("required.module", ModuleLoadingRuleValue.REQUIRED) {
          packagePrefix = "required"
          dependencies {
            plugin("bar")
          }
        }
      }
    }.installAt(pluginDirPath)
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
        module("required.module", ModuleLoadingRuleValue.REQUIRED) {
          packagePrefix = "required"
          dependencies {
            module("bar")
          }
        }
      }
    }.installAt(pluginDirPath)
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
    assertThat(loadingErrors).isNotEmpty
    assertThat(loadingErrors.first().htmlMessage.toString()).contains(*messagePart)
  }

  @Test
  fun `embedded content module uses same classloader as the main module`() {
    val samplePluginDir = plugin("sample.plugin") {
      content {
        module("embedded.module", ModuleLoadingRuleValue.EMBEDDED) {
          packagePrefix = "embedded"
          isSeparateJar = true
        }
        module("required.module", ModuleLoadingRuleValue.REQUIRED) {
          packagePrefix = "required"
          isSeparateJar = true
        }
        module("optional.module", ModuleLoadingRuleValue.OPTIONAL) {
          packagePrefix = "optional"
        }
      }
    }.installAt(pluginDirPath)
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
    plugin(PluginManagerCore.CORE_PLUGIN_ID) {
      content {
        module("embedded.module", ModuleLoadingRuleValue.EMBEDDED) { packagePrefix = "embedded" }
        module("required.module", ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "required" }
      }
    }.installAt(pluginDirPath)
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
    plugin(PluginManagerCore.CORE_PLUGIN_ID) {
      content {
        module("required.module", ModuleLoadingRuleValue.REQUIRED) {
          packagePrefix = "required"
          dependencies { module("unresolved") }
        }
      }
    }.installAt(pluginDirPath)
    buildPluginSet()
    assertFirstErrorContains("requires plugin", "unresolved")
  }

  @Test
  fun `embedded content module without package prefix`() {
    plugin("sample.plugin") {
      content {
        module("embedded.module", ModuleLoadingRuleValue.EMBEDDED) {}
      }
    }.installAt(pluginDirPath)
    val result = buildPluginSet()
    assertThat(result).hasExactlyEnabledPlugins("sample.plugin")
    val mainClassLoader = result.getEnabledPlugin("sample.plugin").pluginClassLoader
    val embeddedModuleClassLoader = result.getEnabledModule("embedded.module").pluginClassLoader
    assertThat(embeddedModuleClassLoader).isSameAs(mainClassLoader)
  }

  @Test
  fun `dependencies of embedded content module are added to the main class loader`() {
    plugin("dep") {}.installAt(pluginDirPath)
    plugin("sample.plugin") {
      content {
        module("embedded.module", ModuleLoadingRuleValue.EMBEDDED) {
          packagePrefix = "embedded"
          dependencies { plugin("dep") }
        }
      }
    }.installAt(pluginDirPath)
    val result = buildPluginSet()
    assertThat(result).hasExactlyEnabledPlugins("sample.plugin", "dep")
    val (sample, dep) = result.getEnabledPlugins("sample.plugin", "dep")
    assertThat(sample).hasExactDirectParentClassloaders(dep)
  }

  @Test
  fun `dependencies between plugin modules`() {
    plugin("sample.plugin") {
      content {
        module("embedded.module", ModuleLoadingRuleValue.EMBEDDED) {
          packagePrefix = "embedded"
        }
        module("required.module", ModuleLoadingRuleValue.REQUIRED) {
          packagePrefix = "required"
          dependencies {
            module("embedded.module")
          }
        }
        module("required2.module", ModuleLoadingRuleValue.REQUIRED) {
          packagePrefix = "required2"
          dependencies {
            module("required.module")
          }
        }
      }
    }.installAt(pluginDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledModulesWithoutMainDescriptors("embedded.module", "required.module", "required2.module")
    val (req, req2, embed) = pluginSet.getEnabledModules("required.module", "required2.module", "embedded.module")
    assertThat(req2).hasExactDirectParentClassloaders(req)
    assertThat(req).hasExactDirectParentClassloaders(embed)
  }

  @Test
  fun `content module in separate JAR`() {
    val pluginDir = plugin("sample.plugin") {
      content {
        module("dep") { isSeparateJar = true }
      }
    }.installAt(pluginDirPath)
    val result = buildPluginSet()
    assertThat(result).hasExactlyEnabledPlugins("sample.plugin")
    assertThat(result).hasExactlyEnabledModulesWithoutMainDescriptors("dep")
    val depModuleDescriptor = result.getEnabledModule("dep")
    assertThat(depModuleDescriptor.ownClassPath).containsExactly(pluginDir.resolve("lib/modules/dep.jar"))
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
    plugin("com.intellij.gradle") {}.installAt(pluginDirPath)
    plugin("org.jetbrains.plugins.gradle") {
      implementationDetail = true
      depends("com.intellij.gradle")
    }.installAt(pluginDirPath)
    plugin("org.jetbrains.plugins.gradle.maven") {
      implementationDetail = true
      depends("org.jetbrains.plugins.gradle")
    }.installAt(pluginDirPath)
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
    assertFirstErrorContains("foo", "depends", "bar-plugin", "failed to load")
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
      .hasExactDirectParentClassloaders(bazModule)
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
      .hasExactDirectParentClassloaders(bazModule)
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
      .hasExactDirectParentClassloaders(bazModule)
      .doesNotHaveTransitiveParentClassloaders(baz)
  }
  
  @Test
  fun `plugin is loaded if it has a depends dependency on plugin alias that is placed in required v2 module and other modules affects sorting`() {
    plugin("baz") {
      content {
        module("baz.module", ModuleLoadingRuleValue.REQUIRED) {
          packagePrefix = "baz.module"
          pluginAlias("bar")
        }
      }
      depends("additional")
    }.installAt(pluginDirPath)
    `foo depends bar`()
    /* an additional module is used to ensure that in the sorted modules list the main module of 'baz' plugin is moved to the end of the 
       list if no explicit edge from 'foo' plugin to it is added */
    plugin("additional") {}.installAt(pluginDirPath)
    
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
      .hasExactDirectParentClassloaders(bazModule)
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
    }.installAt(pluginDirPath)
    `foo module-dependency bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("baz")
  }

  @Test
  fun `plugin is not loaded if it has a depends dependency on v2 module with package prefix`() {
    `bar with optional module`()
    plugin("foo") { depends("bar.module") }.installAt(pluginDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is not loaded if it has a plugin dependency on v2 module with package prefix`() {
    `bar with optional module`()
    plugin("foo") { dependencies { plugin("bar.module") } }.installAt(pluginDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is not loaded if it is incompatible with another plugin and they both contain the same module`() {
    plugin("com.intellij.java") {
      content {
        module("com.intellij.java.debugger.frontend", ModuleLoadingRuleValue.EMBEDDED) {
          packagePrefix = "com.intellij.java.debugger.frontend"
        }
      }
    }.installAt(pluginDirPath)

    plugin("com.intellij.java.frontend") {
      content {
        module("com.intellij.java.debugger.frontend") {
          packagePrefix = "com.intellij.java.debugger.frontend"
        }
      }
      incompatibleWith = listOf("com.intellij.java")
    }.installAt(pluginDirPath)

    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("com.intellij.java")
  }

  @Test
  fun `plugin is loaded if it has a module dependency on v2 module with slash in its name`() {
    plugin("bar") {
      content(namespace = "jetbrains") {
        module("bar/module", ModuleLoadingRuleValue.REQUIRED) {
          packagePrefix = "bar.module"
          moduleVisibility = ModuleVisibilityValue.PUBLIC
        }
      }
    }.installAt(pluginDirPath)
    plugin("foo") { dependencies { module("bar/module") } }.installAt(pluginDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    assertThat(pluginSet).hasExactlyEnabledModulesWithoutMainDescriptors("bar/module")
  }

  @Test
  fun `plugin is not loaded if it has a module dependency on v2 module with slash in its name but dependency has a dot instead`() {
    plugin("bar") {
      content {
        module("bar/module", ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "bar.module" }
      }
    }.installAt(pluginDirPath)
    plugin("foo") { dependencies { module("bar.module") } }.installAt(pluginDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is loaded when it has no dependency on core plugin, but core is a classloader parent excluding its content modules`() {
    plugin(PluginManagerCore.CORE_PLUGIN_ID) {
      pluginAlias("com.intellij.modules.platform")
      content {
        module("embedded.module", ModuleLoadingRuleValue.EMBEDDED) { packagePrefix = "embedded" }
        module("required.module", ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "required" }
        module("optional.module", ModuleLoadingRuleValue.OPTIONAL) { packagePrefix = "optional" }
      }
    }.installAt(pluginDirPath)
    plugin("foo") {}.installAt(pluginDirPath)
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
        module("embedded.module", ModuleLoadingRuleValue.EMBEDDED) { packagePrefix = "embedded" }
        module("required.module", ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "required" }
        module("optional.module", ModuleLoadingRuleValue.OPTIONAL) { packagePrefix = "optional" }
      }
    }.installAt(pluginDirPath)
    plugin("foo") {
      dependencies { plugin(PluginManagerCore.CORE_PLUGIN_ID) }
    }.installAt(pluginDirPath)
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
      content(namespace = "jetbrains") {
        module("embedded.module", ModuleLoadingRuleValue.EMBEDDED) { packagePrefix = "embedded" }
        module("required.module", ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "required" }
        module("optional.module", ModuleLoadingRuleValue.OPTIONAL) {
          packagePrefix = "optional"
          moduleVisibility = ModuleVisibilityValue.PUBLIC
        }
      }
    }.installAt(pluginDirPath)
    plugin("foo") {
      dependencies { module("optional.module") }
    }.installAt(pluginDirPath)
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins(PluginManagerCore.CORE_PLUGIN_ID, "foo")
    val (core, foo) = pluginSet.getEnabledPlugins(PluginManagerCore.CORE_PLUGIN_ID, "foo")
    val (opt, req, emb) = pluginSet.getEnabledModules("optional.module", "required.module", "embedded.module")
    assertThat(foo)
      .hasExactDirectParentClassloaders(opt)
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
          applicationServiceImpl("service")
        }
      }
    }.installAt(pluginDirPath)
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
        module("content.module", ModuleLoadingRuleValue.REQUIRED) {
          depends("foo")
          isSeparateJar = true
        }
      }
    }.installAt(pluginDirPath)
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
      content(namespace = "jetbrains") {
        module("intellij.platform.tasks.impl") {
          packagePrefix = "com.intellij.tasks.impl"
          moduleVisibility = ModuleVisibilityValue.PUBLIC
        }
      }
    }.installAt(pluginDirPath)
    plugin("with-depends") {
      depends("com.intellij.modules.platform")
    }.installAt(pluginDirPath)
    plugin("with-depends-on-lang") {
      depends("com.intellij.modules.lang")
    }.installAt(pluginDirPath)
    plugin("with-dependencies") {
      dependencies { plugin("com.intellij.modules.platform") }
    }.installAt(pluginDirPath)
    plugin("with-depends-on-vcs") {
      depends("com.intellij.modules.vcs")
    }.installAt(pluginDirPath)
    val pluginSet = buildPluginSet()
    val (withDepends, withDependsOnLang, withDependencies, withDependsOnVcs) = 
      pluginSet.getEnabledPlugins("with-depends", "with-depends-on-lang", "with-dependencies", "with-depends-on-vcs")
    val tasks = pluginSet.getEnabledModule("intellij.platform.tasks.impl")
    assertThat(withDepends).hasExactDirectParentClassloaders(tasks)
    assertThat(withDependsOnLang).hasExactDirectParentClassloaders(tasks)
    assertThat(withDependencies).hasExactDirectParentClassloaders()
    assertThat(withDependsOnVcs).hasExactDirectParentClassloaders()
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
    }.installAt(pluginDirPath)
    val (pluginSet, err) = runAndReturnWithLoggedError { buildPluginSet() }
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar", "foo", "baz")
    val (bar, foo, baz) = pluginSet.getEnabledPlugins("bar", "foo", "baz")
    val bazModule = pluginSet.getEnabledModule("baz.module")
    val barSub = bar.dependencies[0].subDescriptor!!
    assertThat(barSub.pluginClassLoader).isEqualTo(bar.pluginClassLoader)
    assertThat(barSub)
      .hasExactDirectParentClassloaders(foo)
      .doesNotHaveTransitiveParentClassloaders(baz, bazModule)
    assertThat(barSub.moduleDependencies.modules).isEmpty()
    assertThat(err).hasMessageContainingAll("'depends' sub-descriptor", "bar", "<dependencies><module>")
  }

  @Test
  fun `content module is not loaded if it depends on module from plugin which was not included in explicit loaded subset`() {
    plugin("foo") {
      content {
        module("foo.embedded", ModuleLoadingRuleValue.EMBEDDED) {}
      }
    }.installAt(pluginDirPath)
    plugin("bar") {
      content {
        module("bar.optional") {}
        module("bar.foo.optional") {
          dependencies {
            module("foo.embedded")
          }
        }
      }
    }.installAt(pluginDirPath)
    val pluginSet = PluginSetTestBuilder.fromPath(pluginDirPath)
      .withExplicitPluginSubsetToLoad(setOf(PluginId("bar")))
      .build()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
    assertThat(pluginSet).hasExactlyEnabledModulesWithoutMainDescriptors("bar.optional")
  }

  @Test
  fun `optional content modules implicitly depend on main module, while required do not`() {
    plugin("foo") {
      content {
        module("embedded.module", ModuleLoadingRuleValue.EMBEDDED) { packagePrefix = "embedded" }
        module("required.module", ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "required" }
        module("optional.module", ModuleLoadingRuleValue.OPTIONAL) { packagePrefix = "optional" }
      }
    }.installAt(pluginDirPath)
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
      .hasExactDirectParentClassloaders(foo)
      .doesNotHaveTransitiveParentClassloaders(req)
  }

  @Nested
  inner class ImplicitDependencyAdditionTests {
    @Test
    fun `legacy plugin gets implicit java dependency when all modules marker is present`() {
      // marker enables implicit dependencies for legacy plugins
      plugin("com.intellij.modules.all") {}.installAt(pluginDirPath)

      // provide java alias and backend module
      plugin(PluginManagerCore.JAVA_PLUGIN_ID.idString) {
        pluginAlias(PluginManagerCore.JAVA_PLUGIN_ALIAS_ID.idString)
        content(namespace = "jetbrains") {
          module("intellij.java.backend") { packagePrefix = "com.intellij.java.backend"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      // legacy plugin: no explicit depends/module deps, non-bundled, no package prefix
      plugin("legacy.plugin") {}.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      assertThat(pluginSet).hasExactlyEnabledPlugins(
        "legacy.plugin",
        PluginManagerCore.JAVA_PLUGIN_ID.idString,
        PluginManagerCore.ALL_MODULES_MARKER.idString,
      )
      val legacy = pluginSet.getEnabledPlugin("legacy.plugin")
      val java = pluginSet.getEnabledPlugin(PluginManagerCore.JAVA_PLUGIN_ID.idString)
      val javaBackend = pluginSet.getEnabledModule("intellij.java.backend")
      assertThat(legacy).hasExactDirectParentClassloaders(java, javaBackend)
    }

    @Test
    fun `legacy plugin does not get implicit java dependency without all modules marker`() {
      plugin(PluginManagerCore.JAVA_PLUGIN_ID.idString) {
        pluginAlias(PluginManagerCore.JAVA_PLUGIN_ALIAS_ID.idString)
        content {
          module("intellij.java.backend") { packagePrefix = "com.intellij.java.backend" }
        }
      }.installAt(pluginDirPath)

      plugin("legacy.plugin") {}.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val legacy = pluginSet.getEnabledPlugin("legacy.plugin")
      val javaBackend = pluginSet.getEnabledModule("intellij.java.backend")
      assertThat(legacy).doesNotHaveDirectParentClassloaders(javaBackend)
    }

    @Test
    @SystemProperty(propertyKey = "enable.implicit.json.dependency", propertyValue = "true")
    fun `non strict plugin gets implicit json backend and collaboration tools`() {
      // JSON alias + backend
      plugin("json.provider") {
        vendor = "JetBrains"
        pluginAlias("com.intellij.modules.json")
        content(namespace = "jetbrains") {
          module("intellij.json.backend") { packagePrefix = "com.intellij.json.backend"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      // Collaboration tools module
      plugin("collab.provider") {
        vendor = "JetBrains"
        content(namespace = "jetbrains") {
          module("intellij.platform.collaborationTools") { packagePrefix = "com.intellij.platform.collaborationTools"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      // VCS module to ensure only non-strict plugins get it implicitly
      plugin("vcs.provider") {
        vendor = "JetBrains"
        content(namespace = "jetbrains") {
          module("intellij.platform.vcs.impl") { packagePrefix = "com.intellij.vcs.impl"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("consumer.plugin") {}
        .installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val consumer = pluginSet.getEnabledPlugin("consumer.plugin")
      val jsonProvider = pluginSet.getEnabledPlugin("json.provider")
      val jsonBackend = pluginSet.getEnabledModule("intellij.json.backend")
      val collab = pluginSet.getEnabledModule("intellij.platform.collaborationTools")
      val vcsImpl = pluginSet.getEnabledModule("intellij.platform.vcs.impl")
      assertThat(consumer).hasExactDirectParentClassloaders(jsonProvider, jsonBackend, collab, vcsImpl)
    }

    @Test
    @SystemProperty(propertyKey = "enable.implicit.json.dependency", propertyValue = "true")
    fun `strict plugin does not get non strict implicit deps`() {
      plugin("json.provider") {
        vendor = "JetBrains"
        pluginAlias("com.intellij.modules.json")
        content { module("intellij.json.backend") { packagePrefix = "com.intellij.json.backend" } }
      }.installAt(pluginDirPath)

      plugin("collab.provider") {
        vendor = "JetBrains"
        content { module("intellij.platform.collaborationTools") { packagePrefix = "com.intellij.platform.collaborationTools" } }
      }.installAt(pluginDirPath)

      plugin("vcs.provider") {
        vendor = "JetBrains"
        content { module("intellij.platform.vcs.impl") { packagePrefix = "com.intellij.vcs.impl" } }
      }.installAt(pluginDirPath)

      plugin("strict.consumer") { vendor = "JetBrains" }
        .installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val consumer = pluginSet.getEnabledPlugin("strict.consumer")
      val jsonBackend = pluginSet.getEnabledModule("intellij.json.backend")
      val collab = pluginSet.getEnabledModule("intellij.platform.collaborationTools")
      val vcsImpl = pluginSet.getEnabledModule("intellij.platform.vcs.impl")
      assertThat(consumer).doesNotHaveDirectParentClassloaders(jsonBackend, collab, vcsImpl)
    }

    @Test
    fun `strict plugin gets vcs module only with explicit alias and no json backend`() {
      plugin("vcs.provider") {
        vendor = "JetBrains"
        pluginAlias("com.intellij.modules.vcs")
        content(namespace = "jetbrains") {
          module("intellij.platform.vcs.impl") {
            packagePrefix = "com.intellij.vcs.impl"
            moduleVisibility = ModuleVisibilityValue.PUBLIC
          }
        }
      }.installAt(pluginDirPath)

      plugin("json.provider") {
        vendor = "JetBrains"
        pluginAlias("com.intellij.modules.json")
        content { module("intellij.json.backend") { packagePrefix = "com.intellij.json.backend" } }
      }.installAt(pluginDirPath)

      plugin("strict.no.dep") { vendor = "JetBrains" }
        .installAt(pluginDirPath)
      plugin("strict.with.dep") {
        vendor = "JetBrains"
        dependencies {
          plugin("com.intellij.modules.vcs")
          plugin("com.intellij.modules.json")
        }
      }.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val noDep = pluginSet.getEnabledPlugin("strict.no.dep")
      val withDep = pluginSet.getEnabledPlugin("strict.with.dep")
      val vcsImpl = pluginSet.getEnabledModule("intellij.platform.vcs.impl")
      val jsonBackend = pluginSet.getEnabledModule("intellij.json.backend")
      assertThat(noDep).doesNotHaveTransitiveParentClassloaders(vcsImpl)
      assertThat(withDep)
        .hasTransitiveParentClassloaders(vcsImpl)
        .doesNotHaveTransitiveParentClassloaders(jsonBackend)
    }

    @Test
    fun `compatibility layer adds backend modules for alias dependencies`() {
      plugin("java.provider") {
        pluginAlias(PluginManagerCore.JAVA_PLUGIN_ALIAS_ID.idString)
        content(namespace = "jetbrains") {
          module("intellij.java.backend") { packagePrefix = "com.intellij.java.backend"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("rider.provider") {
        pluginAlias("com.intellij.modules.rider")
        content(namespace = "jetbrains") {
          module("intellij.rider") { packagePrefix = "com.intellij.rider"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("full.line.provider") {
        pluginAlias("org.jetbrains.completion.full.line")
        content(namespace = "jetbrains") {
          module("intellij.fullLine.core") { packagePrefix = "com.intellij.fullLine.core"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
          module("intellij.fullLine.local") { packagePrefix = "com.intellij.fullLine.local"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
          module("intellij.fullLine.core.impl") { packagePrefix = "com.intellij.fullLine.core.impl"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("cwm.provider") {
        pluginAlias("com.jetbrains.codeWithMe")
        content(namespace = "jetbrains") {
          module("intellij.cwm") { packagePrefix = "com.intellij.cwm"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("cwm.rider.provider") {
        pluginAlias("intellij.rider.plugins.cwm")
        content(namespace = "jetbrains") {
          module("intellij.rider.plugins.cwm") { packagePrefix = "com.intellij.rider.plugins.cwm"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("json.provider") {
        pluginAlias("com.intellij.modules.json")
        content(namespace = "jetbrains") {
          module("intellij.json.backend") { packagePrefix = "com.intellij.json.backend"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("consumer.plugin") {
        dependencies {
          plugin(PluginManagerCore.JAVA_PLUGIN_ALIAS_ID.idString)
          plugin("com.intellij.modules.rider")
          plugin("org.jetbrains.completion.full.line")
          plugin("com.jetbrains.codeWithMe")
          plugin("intellij.rider.plugins.cwm")
          plugin("com.intellij.modules.json")
        }
      }.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val consumer = pluginSet.getEnabledPlugin("consumer.plugin")
      assertThat(consumer).hasExactDirectParentClassloaders(
        pluginSet.getEnabledPlugin("java.provider"),
        pluginSet.getEnabledPlugin("rider.provider"),
        pluginSet.getEnabledPlugin("full.line.provider"),
        pluginSet.getEnabledPlugin("cwm.provider"),
        pluginSet.getEnabledPlugin("cwm.rider.provider"),
        pluginSet.getEnabledPlugin("json.provider"),
        pluginSet.getEnabledModule("intellij.java.backend"),
        pluginSet.getEnabledModule("intellij.rider"),
        pluginSet.getEnabledModule("intellij.fullLine.core"),
        pluginSet.getEnabledModule("intellij.fullLine.local"),
        pluginSet.getEnabledModule("intellij.fullLine.core.impl"),
        pluginSet.getEnabledModule("intellij.cwm"),
        pluginSet.getEnabledModule("intellij.rider.plugins.cwm"),
        pluginSet.getEnabledModule("intellij.json.backend"),
      )
    }

    @Test
    fun `content module gets backend modules for alias dependencies`() {
      plugin("java.provider") {
        vendor = "JetBrains"
        pluginAlias(PluginManagerCore.JAVA_PLUGIN_ALIAS_ID.idString)
        content(namespace = "jetbrains") {
          module("intellij.java.backend") { packagePrefix = "com.intellij.java.backend"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("json.provider") {
        vendor = "JetBrains"
        pluginAlias("com.intellij.modules.json")
        content(namespace = "jetbrains") {
          module("intellij.json.backend") { packagePrefix = "com.intellij.json.backend"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("consumer") {
        content {
          module("consumer.module", ModuleLoadingRuleValue.REQUIRED) {
            packagePrefix = "consumer.module"
            dependencies {
              plugin(PluginManagerCore.JAVA_PLUGIN_ALIAS_ID.idString)
              plugin("com.intellij.modules.json")
            }
          }
        }
      }.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val consumerModule = pluginSet.getEnabledModule("consumer.module")
      assertThat(consumerModule).hasExactDirectParentClassloaders(
        pluginSet.getEnabledPlugin("java.provider"),
        pluginSet.getEnabledPlugin("json.provider"),
        pluginSet.getEnabledModule("intellij.java.backend"),
        pluginSet.getEnabledModule("intellij.json.backend"),
      )
    }

    @Test
    fun `external non bundled descriptors get implicit compatibility modules`() {
      val compatibilityModuleIds = listOf(
        "intellij.libraries.groovy",
        "intellij.platform.structureView",
        "intellij.platform.todo",
      )
      plugin("compatibility.modules.provider") {
        vendor = "JetBrains"
        content(namespace = "jetbrains") {
          for (moduleId in compatibilityModuleIds) {
            module(moduleId) { packagePrefix = moduleId; moduleVisibility = ModuleVisibilityValue.PUBLIC }
          }
        }
      }.installAt(pluginDirPath)

      plugin("optional.target") { vendor = "JetBrains" }.installAt(pluginDirPath)

      plugin("external.consumer") {
        depends("optional.target", configFile = "optional.xml") { applicationServiceImpl("optional.service") }
        content {
          module("external.consumer.module", ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "external.consumer.module" }
        }
      }.installAt(pluginDirPath)

      plugin("jetbrains.consumer") {
        vendor = "JetBrains"
        depends("optional.target", configFile = "optional.xml") { applicationServiceImpl("optional.service") }
        content {
          module("jetbrains.consumer.module", ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "jetbrains.consumer.module" }
        }
      }.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val compatibilityModules = compatibilityModuleIds.map { pluginSet.getEnabledModule(it) }.toTypedArray()
      val optionalTarget = pluginSet.getEnabledPlugin("optional.target")
      val (externalConsumer, jetbrainsConsumer) = pluginSet.getEnabledPlugins("external.consumer", "jetbrains.consumer")
      val externalOptionalDescriptor = externalConsumer.dependencies.single { it.pluginId == PluginId.getId("optional.target") }.subDescriptor!!
      val jetbrainsOptionalDescriptor = jetbrainsConsumer.dependencies.single { it.pluginId == PluginId.getId("optional.target") }.subDescriptor!!

      assertThat(externalConsumer).hasExactDirectParentClassloaders(optionalTarget, *compatibilityModules)
      assertThat(externalOptionalDescriptor).hasExactDirectParentClassloaders(optionalTarget, *compatibilityModules)
      assertThat(pluginSet.getEnabledModule("external.consumer.module")).hasExactDirectParentClassloaders(*compatibilityModules)
      assertThat(jetbrainsConsumer).hasExactDirectParentClassloaders(optionalTarget)
      assertThat(jetbrainsOptionalDescriptor).hasExactDirectParentClassloaders(optionalTarget)
      assertThat(pluginSet.getEnabledModule("jetbrains.consumer.module")).doesNotHaveDirectParentClassloaders(*compatibilityModules)
    }

    @Test
    @SystemProperty(propertyKey = "enable.implicit.json.dependency", propertyValue = "true")
    fun `non strict content module gets implicit json backend and collaboration tools`() {
      plugin("json.provider") {
        vendor = "JetBrains"
        pluginAlias("com.intellij.modules.json")
        content(namespace = "jetbrains") {
          module("intellij.json.backend") { packagePrefix = "com.intellij.json.backend"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("collab.provider") {
        vendor = "JetBrains"
        content(namespace = "jetbrains") {
          module("intellij.platform.collaborationTools") { packagePrefix = "com.intellij.platform.collaborationTools"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("vcs.provider") {
        vendor = "JetBrains"
        content(namespace = "jetbrains") {
          module("intellij.platform.vcs.impl") { packagePrefix = "com.intellij.vcs.impl"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("consumer") {
        content {
          module("consumer.module", ModuleLoadingRuleValue.REQUIRED) { packagePrefix = "consumer.module" }
        }
      }.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val consumerModule = pluginSet.getEnabledModule("consumer.module")
      assertThat(consumerModule).hasExactDirectParentClassloaders(
        pluginSet.getEnabledPlugin("json.provider"),
        pluginSet.getEnabledModule("intellij.json.backend"),
        pluginSet.getEnabledModule("intellij.platform.collaborationTools"),
        pluginSet.getEnabledModule("intellij.platform.vcs.impl"),
      )
    }

    @Test
    fun `depends on java alias adds java backend module`() {
      plugin("java.alias.provider") {
        pluginAlias(PluginManagerCore.JAVA_PLUGIN_ALIAS_ID.idString)
      }.installAt(pluginDirPath)

      plugin("java.backend.provider") {
        content(namespace = "jetbrains") {
          module("intellij.java.backend") { packagePrefix = "com.intellij.java.backend"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("consumer") {
        vendor = "JetBrains"
        depends(PluginManagerCore.JAVA_PLUGIN_ALIAS_ID.idString)
      }.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val consumer = pluginSet.getEnabledPlugin("consumer")
      assertThat(consumer).hasExactDirectParentClassloaders(
        pluginSet.getEnabledPlugin("java.alias.provider"),
        pluginSet.getEnabledModule("intellij.java.backend"),
      )
    }

    @Test
    fun `depends on full line alias adds full line modules`() {
      plugin("full.line.alias.provider") {
        pluginAlias("org.jetbrains.completion.full.line")
      }.installAt(pluginDirPath)

      plugin("full.line.modules") {
        content(namespace = "jetbrains") {
          module("intellij.fullLine.core") { packagePrefix = "com.intellij.fullLine.core"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
          module("intellij.fullLine.local") { packagePrefix = "com.intellij.fullLine.local"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
          module("intellij.fullLine.core.impl") { packagePrefix = "com.intellij.fullLine.core.impl"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("consumer") {
        vendor = "JetBrains"
        depends("org.jetbrains.completion.full.line")
      }.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val consumer = pluginSet.getEnabledPlugin("consumer")
      assertThat(consumer).hasExactDirectParentClassloaders(
        pluginSet.getEnabledPlugin("full.line.alias.provider"),
        pluginSet.getEnabledModule("intellij.fullLine.core"),
        pluginSet.getEnabledModule("intellij.fullLine.local"),
        pluginSet.getEnabledModule("intellij.fullLine.core.impl"),
      )
    }

    @Test
    fun `depends on code with me alias adds remote development module`() {
      plugin("cwm.alias.provider") {
        pluginAlias("com.jetbrains.codeWithMe")
      }.installAt(pluginDirPath)

      plugin("cwm.module.provider") {
        content(namespace = "jetbrains") {
          module("intellij.cwm") { packagePrefix = "com.intellij.cwm"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("consumer") {
        depends("com.jetbrains.codeWithMe")
      }.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val consumer = pluginSet.getEnabledPlugin("consumer")
      assertThat(consumer).hasExactDirectParentClassloaders(
        pluginSet.getEnabledPlugin("cwm.alias.provider"),
        pluginSet.getEnabledModule("intellij.cwm"),
      )
    }

    @Test
    fun `depends on json alias adds json backend without property`() {
      plugin("json.alias.provider") {
        pluginAlias("com.intellij.modules.json")
      }.installAt(pluginDirPath)

      plugin("json.backend.provider") {
        content(namespace = "jetbrains") {
          module("intellij.json.backend") { packagePrefix = "com.intellij.json.backend"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("consumer") {
        depends("com.intellij.modules.json")
      }.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val consumer = pluginSet.getEnabledPlugin("consumer")
      assertThat(consumer).hasExactDirectParentClassloaders(
        pluginSet.getEnabledPlugin("json.alias.provider"),
        pluginSet.getEnabledModule("intellij.json.backend"),
      )
    }

    @Test
    fun `depends on rider alias adds rider module`() {
      plugin("rider.alias.provider") {
        pluginAlias("com.intellij.modules.rider")
      }.installAt(pluginDirPath)

      plugin("rider.module.provider") {
        content(namespace = "jetbrains") {
          module("intellij.rider") {
            packagePrefix = "com.intellij.rider"
            moduleVisibility = ModuleVisibilityValue.PUBLIC
          }
        }
      }.installAt(pluginDirPath)

      plugin("consumer") {
        vendor = "JetBrains"
        depends("com.intellij.modules.rider")
      }.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val consumer = pluginSet.getEnabledPlugin("consumer")
      assertThat(consumer).hasExactDirectParentClassloaders(
        pluginSet.getEnabledPlugin("rider.alias.provider"),
        pluginSet.getEnabledModule("intellij.rider"),
      )
    }

    @Test
    fun `depends on code with me rider alias adds remote development rider module`() {
      plugin("cwm.rider.alias.provider") {
        pluginAlias("intellij.rider.plugins.cwm")
      }.installAt(pluginDirPath)

      plugin("cwm.rider.module.provider") {
        content(namespace = "jetbrains") {
          module("intellij.rider.plugins.cwm") { packagePrefix = "com.intellij.rider.plugins.cwm"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginDirPath)

      plugin("consumer") {
        depends("intellij.rider.plugins.cwm")
      }.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val consumer = pluginSet.getEnabledPlugin("consumer")
      assertThat(consumer).hasExactDirectParentClassloaders(
        pluginSet.getEnabledPlugin("cwm.rider.alias.provider"),
        pluginSet.getEnabledModule("intellij.rider.plugins.cwm"),
      )
    }

    @Test
    fun `depends on vcs alias adds vcs module`() {
      plugin("vcs.alias.provider") {
        pluginAlias("com.intellij.modules.vcs")
      }.installAt(pluginDirPath)

      plugin("vcs.module.provider") {
        vendor = "JetBrains"
        content(namespace = "jetbrains") {
          module("intellij.platform.vcs.impl") {
            packagePrefix = "com.intellij.vcs.impl"
            moduleVisibility = ModuleVisibilityValue.PUBLIC
          }
        }
      }.installAt(pluginDirPath)

      plugin("consumer") {
        vendor = "JetBrains"
        depends("com.intellij.modules.vcs")
      }.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val consumer = pluginSet.getEnabledPlugin("consumer")
      assertThat(consumer).hasExactDirectParentClassloaders(
        pluginSet.getEnabledPlugin("vcs.alias.provider"),
        pluginSet.getEnabledModule("intellij.platform.vcs.impl"),
      )
    }

    @Test
    fun `depends on platform or lang alias adds extracted core content modules`() {
      val extractedModules = listOf(
        "intellij.platform.collaborationTools.auth",
        "intellij.platform.collaborationTools.auth.base",
        "intellij.platform.tasks",
        "intellij.platform.tasks.impl",
        "intellij.platform.scriptDebugger.ui",
        "intellij.platform.scriptDebugger.backend",
        "intellij.platform.scriptDebugger.protocolReaderRuntime",
        "intellij.spellchecker.xml",
        "intellij.relaxng",
        "intellij.spellchecker",
        "intellij.platform.structuralSearch",
      )
      plugin(PluginManagerCore.CORE_PLUGIN_ID) {
        vendor = "JetBrains"
        pluginAlias("com.intellij.modules.platform")
        pluginAlias("com.intellij.modules.lang")
        content(namespace = "jetbrains") {
          for (moduleId in extractedModules) {
            module(moduleId) { packagePrefix = "p.${moduleId.replace('/', '.')}"; moduleVisibility = ModuleVisibilityValue.PUBLIC }
          }
        }
      }.installAt(pluginDirPath)

      plugin("with-platform") {
        vendor = "JetBrains"
        depends("com.intellij.modules.platform")
      }.installAt(pluginDirPath)

      plugin("with-lang") {
        vendor = "JetBrains"
        depends("com.intellij.modules.lang")
      }.installAt(pluginDirPath)

      plugin("with-dependencies") {
        vendor = "JetBrains"
        dependencies { plugin("com.intellij.modules.platform") }
      }.installAt(pluginDirPath)

      val pluginSet = buildPluginSet()
      val moduleDescriptors = extractedModules.map { pluginSet.getEnabledModule(it) }
      val (withPlatform, withLang, withDependencies) =
        pluginSet.getEnabledPlugins("with-platform", "with-lang", "with-dependencies")
      assertThat(withPlatform).hasExactDirectParentClassloaders(*moduleDescriptors.toTypedArray())
      assertThat(withLang).hasExactDirectParentClassloaders(*moduleDescriptors.toTypedArray())
      assertThat(withDependencies).hasExactDirectParentClassloaders()
    }
  }

  private fun foo() = plugin("foo") {}.installAt(pluginDirPath)
  private fun `foo depends bar`() = plugin("foo") { depends("bar") }.installAt(pluginDirPath)
  private fun `foo depends-optional bar`() = plugin("foo") {
    depends("bar", configFile = "bar.xml") { actions = "" }
  }.installAt(pluginDirPath)
  private fun `foo plugin-dependency bar`() = plugin("foo") {
    dependencies { plugin("bar") }
  }.installAt(pluginDirPath)
  private fun `foo module-dependency bar`() = plugin("foo") {
    dependencies { module("bar") }
  }.installAt(pluginDirPath)
  private fun `bar-plugin with module bar`() = plugin("bar-plugin") {
    content(namespace = "jetbrains") {
      module("bar") { moduleVisibility = ModuleVisibilityValue.PUBLIC }
    }
  }.installAt(pluginDirPath)


  private fun bar() = plugin("bar") {}.installAt(pluginDirPath)
  private fun `bar with optional module`() = plugin("bar") {
    content(namespace = "jetbrains") {
      module("bar.module") {
        packagePrefix = "bar.module"
        moduleVisibility = ModuleVisibilityValue.PUBLIC
      }
    }
  }.installAt(pluginDirPath)

  private fun baz() = plugin("baz") {}.installAt(pluginDirPath)
  private fun `baz with alias bar`() = plugin("baz") {
    pluginAlias("bar")
  }.installAt(pluginDirPath)
  private fun `baz with an optional module which has a package prefix`() = plugin("baz") {
    content {
      module("baz.module") { packagePrefix = "baz.module" }
    }
  }.installAt(pluginDirPath)
  private fun `baz with an optional module which has an alias bar and package prefix`() = plugin("baz") {
    content {
      module("baz.module") {
        packagePrefix = "baz.module"
        pluginAlias("bar")
      }
    }
  }.installAt(pluginDirPath)
  private fun `baz with a required module which has an alias bar and package prefix`() = plugin("baz") {
    content {
      module("baz.module", ModuleLoadingRuleValue.REQUIRED) {
        packagePrefix = "baz.module"
        pluginAlias("bar")
      }
    }
  }.installAt(pluginDirPath)

  private fun buildPluginSet(expiredPluginIds: Array<String> = emptyArray(), disabledPluginIds: Array<String> = emptyArray()): PluginSet {
    val state = PluginSetTestBuilder.fromPath(pluginDirPath)
      .withExpiredPlugins(*expiredPluginIds)
      .withDisabledPlugins(*disabledPluginIds)
      .buildState()
    loadingErrors = state.loadingErrors
    return state.pluginSet
  }
}
