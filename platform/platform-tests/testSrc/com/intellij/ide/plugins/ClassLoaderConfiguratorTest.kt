// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.util.BuildNumber
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.assertions.Assertions.assertThatThrownBy
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.Murmur3_32Hash
import com.intellij.util.io.directoryStreamIfExists
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.nio.file.Path
import java.util.*
import java.util.function.Supplier

private val buildNumber = BuildNumber.fromString("2042.0")!!

internal class ClassLoaderConfiguratorTest {
  @Rule @JvmField val name = TestName()

  @Rule @JvmField val inMemoryFs = InMemoryFsRule()

  @Test
  fun packageForOptionalMustBeSpecified() {
    assertThatThrownBy {
      loadPlugins(modulePackage = null)
    }.hasMessageContaining("Package is not specified")
    .hasMessageContaining("package=null")
  }

  @Test
  fun packageForOptionalMustBeDifferent() {
    assertThatThrownBy {
      loadPlugins(modulePackage = "com.example")
    }.hasMessageContaining("Package prefix com.example is already used")
    .hasMessageContaining("com.example")
  }

  @Test
  fun packageMustBeUnique() {
    assertThatThrownBy {
      loadPlugins(modulePackage = "com.bar")
    }.hasMessageContaining("Package prefix com.bar is already used")
    .hasMessageContaining("package=com.bar")
  }

  @Test
  fun regularPluginClassLoaderIsUsedIfPackageSpecified() {
    val plugin = loadPlugins(modulePackage = "com.example.extraSupportedFeature").getEnabledPlugins().get(1)
    assertThat(plugin.content.modules.get(0).requireDescriptor().classLoader).isInstanceOf(PluginAwareClassLoader::class.java)
  }

  @Suppress("PluginXmlValidity")
  private fun loadPlugins(modulePackage: String?): PluginLoadingResult {
    val rootDir = inMemoryFs.fs.getPath("/")

    // toUnsignedLong - avoid - symbol
    val pluginIdSuffix = Integer.toUnsignedLong(Murmur3_32Hash.MURMUR3_32.hashString(javaClass.name + name.methodName)).toString(36)
    val dependencyId = "p_dependency_$pluginIdSuffix"
    plugin(rootDir, """
      <idea-plugin package="com.bar">
        <id>$dependencyId</id>
        <extensionPoints>
          <extensionPoint qualifiedName="bar.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP" dynamic="true"/>"
        </extensionPoints>
      </idea-plugin>
      """)

    val dependentPluginId = "p_dependent_$pluginIdSuffix"
    plugin(rootDir, """
      <idea-plugin package="com.example">
        <id>$dependentPluginId</id>
        <content>
          <module name="com.example.sub"/>
        </content>
      </idea-plugin>
    """)
    module(rootDir, dependentPluginId, "com.example.sub", """
      <idea-plugin ${modulePackage?.let { """package="$it"""" } ?: ""}>
        <!-- dependent must not be empty, add some extension -->
        <extensionPoints>
          <extensionPoint qualifiedName="bar.barExtension" beanClass="com.intellij.util.KeyedLazyInstanceEP" dynamic="true"/>"
        </extensionPoints>
      </idea-plugin>
    """)

    val loadResult = loadDescriptors(rootDir)
    val plugins = loadResult.getEnabledPlugins()
    assertThat(plugins).hasSize(2)

    val classLoaderConfigurator = ClassLoaderConfigurator(PluginSet.createPluginSet(plugins, plugins))
    plugins.forEach(classLoaderConfigurator::configure)
    return loadResult
  }
}

private fun loadDescriptors(dir: Path): PluginLoadingResult {
  val result = PluginLoadingResult(brokenPluginVersions = emptyMap(), productBuildNumber = Supplier { buildNumber })
  val context = DescriptorListLoadingContext(disabledPlugins = Collections.emptySet(), result = result)

  // constant order in tests
  val paths = dir.directoryStreamIfExists { it.sorted() }!!
  context.use {
    for (file in paths) {
      result.add(loadDescriptor(file, false, context) ?: continue, false)
    }
  }
  result.finishLoading()
  return result
}
