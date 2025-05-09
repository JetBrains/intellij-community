// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.platform.testFramework.PluginBuilder
import com.intellij.platform.plugins.testFramework.PluginSetTestBuilder
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class PluginDependenciesTest {
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
  }

  @Test
  fun `plugin is loaded when depends-optional dependency is resolved`() {
    `foo depends-optional bar`()
    bar()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    val (foo, bar) = pluginSet.getEnabledPlugins("foo", "bar")
    assertThat(foo).hasDirectParentClassloaders(bar)
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
    PluginBuilder.empty().id("bar")
      .module("bar.optional", PluginBuilder.empty().packagePrefix("bar.optional"), loadingRule = ModuleLoadingRule.OPTIONAL)
      .module("bar.required", PluginBuilder.empty().packagePrefix("bar.required"), loadingRule = ModuleLoadingRule.REQUIRED)
      .module("bar.embedded", PluginBuilder.empty().packagePrefix("bar.embedded"), loadingRule = ModuleLoadingRule.EMBEDDED)
      .build(pluginDirPath.resolve("bar"))
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
    PluginBuilder.empty().id("foo").depends("bar.module").build(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is not loaded if it has a plugin dependency on v2 module`() {
    `bar with optional module`()
    PluginBuilder.empty().id("foo").pluginDependency("bar.module").build(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is loaded if it has a module dependency on v2 module`() {
    `bar with optional module`()
    PluginBuilder.empty().id("foo").dependency("bar.module").build(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
  }

  @Test
  fun `plugin is not loaded if required module is not available`() {
    PluginManagerCore.getAndClearPluginLoadingErrors() //clear errors which may be registered by other tests
    PluginBuilder.empty()
      .id("sample.plugin")
      .module("required.module", PluginBuilder.empty().packagePrefix("required").dependency("unknown"), loadingRule = ModuleLoadingRule.REQUIRED)
      .build(pluginDirPath.resolve("sample-plugin"))
    val result = buildPluginSet()
    assertThat(result).doesNotHaveEnabledPlugins()
    val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
    assertThat(errors).isNotEmpty
    assertThat(errors.first().get().toString()).contains("sample.plugin", "requires plugin", "unknown")
  }

  @Test
  fun `embedded content module uses same classloader as the main module`() {
    val samplePluginDir = pluginDirPath.resolve("sample-plugin")
    PluginBuilder.empty()
      .id("sample.plugin")
      .module("embedded.module", PluginBuilder.empty().packagePrefix("embedded").separateJar(true), loadingRule = ModuleLoadingRule.EMBEDDED)
      .module("required.module", PluginBuilder.empty().packagePrefix("required").separateJar(true), loadingRule = ModuleLoadingRule.REQUIRED)
      .module("optional.module", PluginBuilder.empty().packagePrefix("optional"))
      .build(samplePluginDir)
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
    PluginBuilder.empty()
      .id(PluginManagerCore.CORE_PLUGIN_ID)
      .module("embedded.module", PluginBuilder.empty().packagePrefix("embedded"), loadingRule = ModuleLoadingRule.EMBEDDED)
      .module("required.module", PluginBuilder.empty().packagePrefix("required"), loadingRule = ModuleLoadingRule.REQUIRED)
      .build(corePluginDir)
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
    PluginManagerCore.getAndClearPluginLoadingErrors() //clear errors which may be registered by other tests
    val corePluginDir = pluginDirPath.resolve("core")
    PluginBuilder.empty()
      .id(PluginManagerCore.CORE_PLUGIN_ID)
      .module("required.module", PluginBuilder.empty().packagePrefix("required").dependency("unresolved"), loadingRule = ModuleLoadingRule.REQUIRED)
      .build(corePluginDir)
    buildPluginSet()
    val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
    assertThat(errors).isNotEmpty
    assertThat(errors.first().get().toString()).contains("requires plugin", "unresolved")
  }

  @Test
  fun `embedded content module without package prefix`() {
    val samplePluginDir = pluginDirPath.resolve("sample-plugin")
    PluginBuilder.empty()
      .id("sample.plugin")
      .module("embedded.module", PluginBuilder.empty(), loadingRule = ModuleLoadingRule.EMBEDDED)
      .build(samplePluginDir)
    val result = buildPluginSet()
    assertThat(result).hasExactlyEnabledPlugins("sample.plugin")
    val mainClassLoader = result.getEnabledPlugin("sample.plugin").pluginClassLoader
    val embeddedModuleClassLoader = result.getEnabledModule("embedded.module").pluginClassLoader
    assertThat(embeddedModuleClassLoader).isSameAs(mainClassLoader)
  }

  @Test
  fun `dependencies of embedded content module are added to the main class loader`() {
    PluginBuilder.empty()
      .id("dep")
      .build(pluginDirPath.resolve("dep"))
    PluginBuilder.empty()
      .id("sample.plugin")
      .module("embedded.module", PluginBuilder.empty().packagePrefix("embedded").pluginDependency("dep"), loadingRule = ModuleLoadingRule.EMBEDDED)
      .build(pluginDirPath.resolve("sample-plugin"))
    val result = buildPluginSet()
    assertThat(result).hasExactlyEnabledPlugins("sample.plugin", "dep")
    val (sample, dep) = result.getEnabledPlugins("sample.plugin", "dep")
    assertThat(sample).hasDirectParentClassloaders(dep)
  }

  @Test
  fun `dependencies between plugin modules`() {
    PluginBuilder.empty()
      .id("sample.plugin")
      .module("embedded.module", PluginBuilder.empty().packagePrefix("embedded"), loadingRule = ModuleLoadingRule.EMBEDDED)
      .module("required.module", PluginBuilder.empty().packagePrefix("required").dependency("embedded.module"), loadingRule = ModuleLoadingRule.REQUIRED)
      .module("required2.module", PluginBuilder.empty().packagePrefix("required2").dependency("required.module"), loadingRule = ModuleLoadingRule.REQUIRED)
      .build(pluginDirPath.resolve("sample-plugin"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledModulesWithoutMainDescriptors("embedded.module", "required.module", "required2.module")
    val (req, req2, embed) = pluginSet.getEnabledModules("required.module", "required2.module", "embedded.module")
    assertThat(req2).hasDirectParentClassloaders(req)
    assertThat(req).hasDirectParentClassloaders(embed)
  }

  @Test
  fun `content module in separate JAR`() {
    val pluginDir = pluginDirPath.resolve("sample-plugin")
    PluginBuilder.empty()
      .id("sample.plugin")
      .module("dep", PluginBuilder.empty().separateJar(true))
      .build(pluginDir)
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
    PluginBuilder.empty()
      .id("com.intellij.gradle")
      .build(pluginDirPath.resolve("intellij.gradle"))
    PluginBuilder.empty()
      .id("org.jetbrains.plugins.gradle")
      .depends("com.intellij.gradle")
      .implementationDetail()
      .build(pluginDirPath.resolve("intellij.gradle.java"))
    PluginBuilder.empty()
      .id("org.jetbrains.plugins.gradle.maven")
      .implementationDetail()
      .depends("org.jetbrains.plugins.gradle")
      .build(pluginDirPath.resolve("intellij.gradle.java.maven"))
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
    PluginBuilder.empty().id("bar").packagePrefix("idk").build(pluginDirPath.resolve("bar"))
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
    PluginBuilder.empty().id("baz").pluginAlias("bar").packagePrefix("idk").build(pluginDirPath.resolve("baz"))
    `foo module-dependency bar`()
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("baz")
  }

  @Test
  fun `plugin is not loaded if it has a depends dependency on v2 module with package prefix`() {
    `bar with optional module`()
    PluginBuilder.empty().id("foo").depends("bar.module").build(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is not loaded if it has a plugin dependency on v2 module with package prefix`() {
    `bar with optional module`()
    PluginBuilder.empty().id("foo").pluginDependency("bar.module").build(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is not loaded if it is incompatible with another plugin and they both contain the same module`() {
    val requiredModule = PluginBuilder.empty().packagePrefix("com.intellij.java.debugger.frontend")

    PluginBuilder.empty()
      .id("com.intellij.java")
      .module("com.intellij.java.debugger.frontend", requiredModule, loadingRule = ModuleLoadingRule.EMBEDDED)
      .build(pluginDirPath.resolve("intellij.java"))

    PluginBuilder.empty()
      .id("com.intellij.java.frontend")
      .module("com.intellij.java.debugger.frontend", requiredModule)
      .incompatibleWith("com.intellij.java")
      .build(pluginDirPath.resolve("intellij.java.frontend"))

    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("com.intellij.java")
  }

  @Test
  fun `plugin is loaded if it has a module dependency on v2 module with slash in its name`() {
    PluginBuilder.empty().id("bar")
      .module(moduleName = "bar/module",
              PluginBuilder.empty().packagePrefix("bar.module"),
              loadingRule = ModuleLoadingRule.REQUIRED,
              moduleFile = "bar.module.xml")
      .build(pluginDirPath.resolve("bar"))
    PluginBuilder.empty().id("foo").dependency("bar/module").build(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    assertThat(pluginSet).hasExactlyEnabledModulesWithoutMainDescriptors("bar/module")
  }

  @Test
  fun `plugin is not loaded if it has a module dependency on v2 module with slash in its name but dependency has a dot instead`() {
    PluginBuilder.empty().id("bar")
      .module(moduleName = "bar/module",
              PluginBuilder.empty().packagePrefix("bar.module"),
              loadingRule = ModuleLoadingRule.REQUIRED,
              moduleFile = "bar.module.xml")
      .build(pluginDirPath.resolve("bar"))
    PluginBuilder.empty().id("foo").dependency("bar.module").build(pluginDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("bar")
  }

  @Test
  fun `plugin is loaded when it has no dependency on core plugin, but core is a classloader parent excluding its content modules`() {
    PluginBuilder.empty()
      .id(PluginManagerCore.CORE_PLUGIN_ID)
      .pluginAlias("com.intellij.modules.platform")
      .module("embedded.module", PluginBuilder.empty().packagePrefix("embedded"), loadingRule = ModuleLoadingRule.EMBEDDED)
      .module("required.module", PluginBuilder.empty().packagePrefix("required"), loadingRule = ModuleLoadingRule.REQUIRED)
      .module("optional.module", PluginBuilder.empty().packagePrefix("optional"), loadingRule = ModuleLoadingRule.OPTIONAL)
      .build(pluginDirPath.resolve("core"))
    PluginBuilder.empty()
      .id("foo")
      .build(pluginDirPath.resolve("foo"))
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
    PluginBuilder.empty()
      .id(PluginManagerCore.CORE_PLUGIN_ID)
      .module("embedded.module", PluginBuilder.empty().packagePrefix("embedded"), loadingRule = ModuleLoadingRule.EMBEDDED)
      .module("required.module", PluginBuilder.empty().packagePrefix("required"), loadingRule = ModuleLoadingRule.REQUIRED)
      .module("optional.module", PluginBuilder.empty().packagePrefix("optional"), loadingRule = ModuleLoadingRule.OPTIONAL)
      .build(pluginDirPath.resolve("core"))
    PluginBuilder.empty()
      .id("foo")
      .pluginDependency(PluginManagerCore.CORE_PLUGIN_ID)
      .build(pluginDirPath.resolve("foo"))
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
    PluginBuilder.empty()
      .id(PluginManagerCore.CORE_PLUGIN_ID)
      .module("embedded.module", PluginBuilder.empty().packagePrefix("embedded"), loadingRule = ModuleLoadingRule.EMBEDDED)
      .module("required.module", PluginBuilder.empty().packagePrefix("required"), loadingRule = ModuleLoadingRule.REQUIRED)
      .module("optional.module", PluginBuilder.empty().packagePrefix("optional"), loadingRule = ModuleLoadingRule.OPTIONAL)
      .build(pluginDirPath.resolve("core"))
    PluginBuilder.empty()
      .id("foo")
      .dependency("optional.module")
      .build(pluginDirPath.resolve("foo"))
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
    PluginBuilder.empty().id("bar")
      .depends(
        "foo",
        PluginBuilder.empty()
          .depends(
            "baz",
            PluginBuilder.empty().extensions("""
              <applicationService serviceImplementation="service"/>
            """.trimIndent())
          )
      )
      .build(pluginDirPath.resolve("bar"))
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
    PluginBuilder.empty()
      .id("bar")
      .module("content.module",
              PluginBuilder.empty().depends("foo").separateJar(true),
              loadingRule = ModuleLoadingRule.REQUIRED)
      .build(pluginDirPath.resolve("bar"))
    val msg = LoggedErrorProcessor.executeAndReturnLoggedError {
      assertThatThrownBy {
        buildPluginSet()
      }.hasMessageContainingAll("content.module", "shouldn't have plugin dependencies", "foo")
    }
    assertThat(msg).hasMessageContainingAll("Unexpected `depends` dependencies in a content module")
  }

  @Test
  fun `optional depends descriptor may have module dependency, but it's disregarded`() {
    foo()
    `baz with an optional module which has a package prefix`()
    PluginBuilder.empty()
      .id("bar")
      .depends("foo", PluginBuilder.empty().dependency("baz.module").packagePrefix("foo.baz"))
      .build(pluginDirPath.resolve("bar"))
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
    assertThat(err).hasMessageContainingAll("Unexpected `module` dependencies in a `depends` sub-descriptor")
  }

  private fun foo() = PluginBuilder.empty().id("foo").build(pluginDirPath.resolve("foo"))
  private fun `foo depends bar`() = PluginBuilder.empty().id("foo").depends("bar").build(pluginDirPath.resolve("foo"))
  private fun `foo depends-optional bar`() = PluginBuilder.empty().id("foo")
    .depends("bar", PluginBuilder.empty().actions(""))
    .build(pluginDirPath.resolve("foo"))
  private fun `foo plugin-dependency bar`() = PluginBuilder.empty().id("foo").pluginDependency("bar").build(pluginDirPath.resolve("foo"))
  private fun `foo module-dependency bar`() = PluginBuilder.empty().id("foo").dependency("bar").build(pluginDirPath.resolve("foo"))

  private fun bar() = PluginBuilder.empty().id("bar").build(pluginDirPath.resolve("bar"))
  private fun `bar with optional module`() = PluginBuilder.empty().id("bar")
    .module("bar.module", PluginBuilder.empty().packagePrefix("bar.module"))
    .build(pluginDirPath.resolve("bar"))

  private fun baz() = PluginBuilder.empty().id("baz").build(pluginDirPath.resolve("baz"))
  private fun `baz with alias bar`() = PluginBuilder.empty().id("baz").pluginAlias("bar").build(pluginDirPath.resolve("baz"))
  private fun `baz with an optional module which has a package prefix`() = PluginBuilder.empty().id("baz")
    .module("baz.module", PluginBuilder.empty().packagePrefix("baz.module"))
    .build(pluginDirPath.resolve("baz"))
  private fun `baz with an optional module which has an alias bar and package prefix`() = PluginBuilder.empty().id("baz")
    .module("baz.module", PluginBuilder.empty().packagePrefix("baz.module").pluginAlias("bar"))
    .build(pluginDirPath.resolve("baz"))
  private fun `baz with a required module which has an alias bar and package prefix`() = PluginBuilder.empty().id("baz")
    .module("baz.module", PluginBuilder.empty().packagePrefix("baz.module").pluginAlias("bar"), loadingRule = ModuleLoadingRule.REQUIRED)
    .build(pluginDirPath.resolve("baz"))

  private fun buildPluginSet(expiredPluginIds: Array<String> = emptyArray(), disabledPluginIds: Array<String> = emptyArray()) =
    PluginSetTestBuilder.fromPath(pluginDirPath)
      .withExpiredPlugins(*expiredPluginIds)
      .withDisabledPlugins(*disabledPluginIds)
      .build()
}
