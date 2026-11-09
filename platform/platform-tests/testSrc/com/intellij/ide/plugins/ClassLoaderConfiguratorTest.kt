// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.pluginSystem.testFramework.buildPluginSet
import com.intellij.platform.testFramework.plugins.PluginSpec
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.includeClassFile
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class ClassLoaderConfiguratorTest {
  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  val rootDir: Path get() = inMemoryFs.fs.getPath("/")

  @Test
  fun `plugin must be after child`() {
    val emptyPath = Path.of("")
    val kotlin = PluginMainDescriptor(PluginDescriptorBuilder.builder().apply { id = "org.jetbrains.kotlin" }.build(), emptyPath, isBundled = false)
    val gradle = PluginMainDescriptor(PluginDescriptorBuilder.builder().apply { id = "org.jetbrains.plugins.gradle" }.build(), emptyPath, isBundled = false)
    val emptyBuilder = PluginDescriptorBuilder.builder()
    val kotlinGradleJava = kotlin.createContentModuleInTest(
      subBuilder = emptyBuilder,
      descriptorPath = "",
      module = PluginContentDescriptor.ModuleItem(
        moduleId = PluginModuleId("kotlin.gradle.gradle-java", PluginModuleId.JETBRAINS_NAMESPACE),
        loadingRule = ModuleLoadingRule.OPTIONAL,
        configFile = null,
        descriptorContent = null,
        requiredIfAvailable = null,
      ))
    val kotlinCompilerGradle = kotlin.createContentModuleInTest(
      subBuilder = emptyBuilder,
      descriptorPath = "",
      module = PluginContentDescriptor.ModuleItem(
        moduleId = PluginModuleId("kotlin.compiler-plugins.annotation-based-compiler-support.gradle", PluginModuleId.JETBRAINS_NAMESPACE),
        loadingRule = ModuleLoadingRule.OPTIONAL,
        configFile = null,
        descriptorContent = null,
        requiredIfAvailable = null,
      ))
    val plugins = arrayOf(kotlin, gradle, kotlinGradleJava, kotlinCompilerGradle)
    sortDependenciesInPlace(plugins)
    assertThat(plugins.last().contentModuleName).isNull()
  }

  @Test
  fun `child with common package prefix must be after included sibling`() {
    val plugin = PluginMainDescriptor(
      PluginDescriptorBuilder.builder().apply {
        id = "com.example"
      }.build(),
      Path.of(""),
      false,
    )
    fun createModuleDescriptor(moduleId: String): ContentModuleDescriptor {
      return plugin.createContentModuleInTest(
        subBuilder = PluginDescriptorBuilder.builder().apply { `package` = moduleId },
        descriptorPath = "",
        module = PluginContentDescriptor.ModuleItem(
          moduleId = PluginModuleId(moduleId, PluginModuleId.JETBRAINS_NAMESPACE),
          configFile = null,
          descriptorContent = null,
          loadingRule = ModuleLoadingRule.OPTIONAL,
          requiredIfAvailable = null,
        ),
      )
    }
    val modules = arrayOf(
      createModuleDescriptor("com.foo"),
      createModuleDescriptor("com.foo.bar"),
    )
    sortDependenciesInPlace(modules)
    assertThat(modules.map { it.moduleId.name }).containsExactly("com.foo.bar", "com.foo")
  }

  @Test
  fun regularPluginClassLoaderIsUsedIfPackageSpecified() {
    val pluginSet = buildPluginSet(rootDir, configureClassLoaders = false) {
      plugin("p_dependency") {
        packagePrefix = "com.bar"
        extensionPoints =
          """<extensionPoint qualifiedName="bar.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP" dynamic="true"/>"""
      }
      plugin("p_dependent") {
        packagePrefix = "com.example"
        content(namespace = "jetbrains") {
          module("com.example.sub") {
            packagePrefix = "com.example.extraSupportedFeature"
            extensionPoints =
              """<extensionPoint qualifiedName="bar.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP" dynamic="true"/>"""
          }
        }
      }
    }
    Assertions.assertThat(pluginSet.enabledPlugins).hasSize(2)
    val classLoaderConfigurator = ClassLoaderConfigurator(pluginSet)
    classLoaderConfigurator.configure()
    val plugin = pluginSet.findEnabledPlugin(PluginId("p_dependent"))!! as PluginMainDescriptor
    assertThat(plugin.contentModules[0].pluginClassLoader).isInstanceOf(PluginAwareClassLoader::class.java)

    val scope = createPluginDependencyAndContentBasedScope(plugin, pluginSet)!!
    assertThat(scope.isDefinitelyAlienClass(name = "dd", packagePrefix = "dd", force = false)).isNull()
    assertThat(scope.isDefinitelyAlienClass(name = "com.example.extraSupportedFeature.Foo", packagePrefix = "com.example.extraSupportedFeature.", force = false))
      .isEqualToIgnoringWhitespace("Class com.example.extraSupportedFeature.Foo must not be requested from main classloader of p_dependent plugin. " +
                 "Matches content module (packagePrefix=com.example.extraSupportedFeature., moduleId=com.example.sub).")
  }

  @Test
  fun `inject content module if another plugin specifies dependency in old format`() {
    val rootDir = inMemoryFs.fs.getPath("/")
    val pluginSet = buildPluginSet(rootDir, configureClassLoaders = false) {
      plugin("1-foo") {
        packagePrefix = "com.foo"
        content {
          module("com.example.sub") { packagePrefix="com.foo.sub" }
        }
      }
      plugin("2-bar") {
        depends("1-foo")
      }
    }

    assertThat(pluginSet.enabledPlugins).hasSize(2)
    val barPlugin = pluginSet.getEnabledPlugin("2-bar")

    val classLoaderConfigurator = ClassLoaderConfigurator(pluginSet)
    classLoaderConfigurator.configure()

    assertThat((barPlugin.pluginClassLoader as PluginClassLoader)._getParents().map { it.descriptorPath })
      .containsExactly("com.example.sub.xml", null)
  }

  // The JNA cases use a real directory: the class path opens a jar with `ZipFile`, and `ZipFile` needs the default file system.

  @Test
  fun `non-bundled plugin takes JNA from the JNA module`(@TempDir dir: Path) {
    val pluginSet = buildJnaPluginSet(dir, jnaPlugin(), pluginWithOwnJna("routed"))
    val jnaLoader = jnaModuleLoader(pluginSet)

    assertThat(loadJnaClass(pluginSet.getEnabledPlugin("routed").pluginClassLoader!!).classLoader).isSameAs(jnaLoader)
    assertThat(loadJnaClass(jnaLoader).classLoader).isSameAs(jnaLoader)
  }

  @Test
  fun `content modules of a non-bundled plugin take JNA from the JNA module`(@TempDir dir: Path) {
    val routedModules = plugin("routed-modules") {
      includeClassFile(JNA_PROBE_CLASS)
      content {
        module("routed.shared", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
          packagePrefix = "com.example.routed.shared"
        }
        module("routed.own", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
          isSeparateJar = true
          includeClassFile(JNA_PROBE_CLASS)
        }
      }
    }
    val pluginSet = buildJnaPluginSet(dir, jnaPlugin(), routedModules)
    val jnaLoader = jnaModuleLoader(pluginSet)

    val modules = (pluginSet.getEnabledPlugin("routed-modules") as PluginMainDescriptor).contentModules
    assertThat(modules.map { it.moduleId.name }).containsExactly("routed.shared", "routed.own")
    for (module in modules) {
      assertThat(loadJnaClass(module.pluginClassLoader!!).classLoader).describedAs(module.moduleId.name).isSameAs(jnaLoader)
    }
  }

  @Test
  fun `bundled plugin takes JNA through a routed non-bundled parent`(@TempDir dir: Path) {
    val bundledDependent = plugin("bundled-dependent") {
      depends("routed")
    }
    val pluginSet = buildJnaPluginSet(dir, jnaPlugin(), pluginWithOwnJna("routed"), bundledDependent, bundled = setOf("bundled-dependent"))

    val dependentLoader = pluginSet.getEnabledPlugin("bundled-dependent").pluginClassLoader!!
    assertThat(loadJnaClass(dependentLoader).classLoader).isSameAs(jnaModuleLoader(pluginSet))
  }

  @Test
  fun `plugin keeps its own JNA without the JNA module`(@TempDir dir: Path) {
    val pluginSet = buildJnaPluginSet(dir, pluginWithOwnJna("own"))

    val loader = pluginSet.getEnabledPlugin("own").pluginClassLoader!!
    assertThat(loadJnaClass(loader).classLoader).isSameAs(loader)
  }

  @Test
  fun `bundled plugin keeps its own JNA`(@TempDir dir: Path) {
    val pluginSet = buildJnaPluginSet(dir, jnaPlugin(), pluginWithOwnJna("bundled-own"), bundled = setOf("bundled-own"))

    val loader = pluginSet.getEnabledPlugin("bundled-own").pluginClassLoader!!
    assertThat(loadJnaClass(loader).classLoader).isSameAs(loader)
  }
}

private const val JNA_PROBE_CLASS = "com.sun.jna.Version"

private fun jnaPlugin(): PluginSpec = plugin("jna-host") {
  content(namespace = PluginModuleId.JETBRAINS_NAMESPACE) {
    module("intellij.libraries.jna", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
      isSeparateJar = true
      includeClassFile(JNA_PROBE_CLASS)
    }
  }
}

private fun pluginWithOwnJna(id: String): PluginSpec = plugin(id) {
  includeClassFile(JNA_PROBE_CLASS)
}

private fun buildJnaPluginSet(dir: Path, vararg plugins: PluginSpec, bundled: Set<String> = emptySet()): PluginSet {
  val paths = plugins.associate { it.id!! to it.installAt(dir) }
  return PluginSetTestBuilder.fromDescriptors { loadingContext ->
    paths.map { (id, path) -> checkNotNull(loadDescriptorFromFileOrDirInTests(path, loadingContext, isBundled = id in bundled)) }
  }.build()
}

private fun jnaModuleLoader(pluginSet: PluginSet): ClassLoader {
  return pluginSet.findEnabledModule(PluginModuleId("intellij.libraries.jna", PluginModuleId.JETBRAINS_NAMESPACE))!!.pluginClassLoader!!
}

/** Loads the probe class without its static initializer. */
private fun loadJnaClass(loader: ClassLoader): Class<*> = Class.forName(JNA_PROBE_CLASS, false, loader)
