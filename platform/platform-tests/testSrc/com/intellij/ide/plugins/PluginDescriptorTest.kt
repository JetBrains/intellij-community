// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax", "ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.logger
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.util.io.directoryContent
import com.intellij.util.io.java.classFile
import com.intellij.util.io.write
import com.intellij.util.lang.UrlClassLoader
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Enumeration
import java.util.Locale
import kotlin.io.path.createParentDirectories
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class PluginDescriptorTest {
  @TestDataPath("\$CONTENT_ROOT/testData/plugins/pluginDescriptor") @Suppress("unused")
  private class TestDataRef // for easy navigation

  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  private val rootPath get() = inMemoryFs.fs.getPath("/")
  private val pluginDirPath get() = rootPath.resolve("plugin")

  @Test
  fun `asp descriptor loads`() {
    val descriptor = loadDescriptorFromTestDataDir("asp.jar")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.pluginId.idString).isEqualTo("com.jetbrains.plugins.asp")
    assertThat(descriptor.name).isEqualTo("ASP")
  }

  @Test
  fun `descriptor with depends-optional loads`() {
    val descriptor = loadDescriptorFromTestDataDir("dependsOptional")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.dependencies)
      .singleElement()
      .extracting { it.isOptional }.isEqualTo(true)
  }

  @Test
  fun `descriptor with multiple depends-optional loads`() {
    val descriptor = loadDescriptorFromTestDataDir("multipleDependsOptional")
    assertThat(descriptor).isNotNull()
    val pluginDependencies = descriptor.dependencies
    assertThat(pluginDependencies).hasSize(2)
    assertThat(pluginDependencies.map { it.isOptional }).allMatch { it == true }
    assertThat(pluginDependencies.map { it.pluginId.idString }).containsExactly("dep2", "dep1")
  }
  
  @Test
  fun `descriptor with multiple plugin dependencies loads`() {
    val descriptor = loadDescriptorFromTestDataDir("multiplePluginDependencies")
    assertThat(descriptor).isNotNull()
    val pluginDependencies = descriptor.moduleDependencies.plugins
    assertThat(pluginDependencies).hasSize(2)
    assertThat(pluginDependencies.map { it.id.idString }).containsExactly("dep1", "dep2")
  }

  @Test
  fun `malformed descriptor fails to load`() {
    assertThatThrownBy { loadDescriptorFromTestDataDir("malformed") }
      .hasMessageContaining("Unexpected character 'o' (code 111) in prolog")
  }

  @Test
  fun `name is used as a substitute for id if id is missing`() {
    val descriptor = loadDescriptorFromTestDataDir("noId")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.name).isEqualTo("Name")
    assertThat(descriptor.pluginId.idString).isEqualTo("Name")
  }

  @Test
  fun `id is used as a substitute for name if name is missing`() {
    val descriptor = loadDescriptorFromTestDataDir("noName")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.pluginId.idString).isEqualTo("plugin.id")
    assertThat(descriptor.name).isEqualTo("plugin.id")
  }

  @Test
  fun `descriptor with cyclical optional depends config files fails to load`() {
    assertThatThrownBy { loadDescriptorFromTestDataDir("cyclicOptionalDeps") }
      .hasMessageEndingWith(" optional descriptors form a cycle: a.xml, b.xml")
  }

  @Test
  fun `descriptor with cyclical optional depends config files fails to load - 2`() {
    assertThatThrownBy { loadDescriptorFromTestDataDir("cyclicOptionalDeps2") }
      .hasMessageEndingWith(" optional descriptors form a cycle: a.xml, b.xml")
  }

  // todo revisit
  @Test
  fun `strict depends makes only one another optional depends on the same plugin strict too and is removed`() {
    val descriptor = loadDescriptorFromTestDataDir("duplicateDepends-strict")
    assertThat(descriptor).isNotNull()
    // fixme what is that result o_O
    assertThat(descriptor.dependencies.map { it.pluginId.idString }).isEqualTo(listOf("foo", "foo"))
    assertThat(descriptor.dependencies.map { it.isOptional }).isEqualTo(listOf(false, true))
    //assertThat(descriptor.pluginDependencies.map { it.pluginId }).isEqualTo(listOf("foo", "foo", "foo"))
    //assertThat(descriptor.pluginDependencies.map { it.isOptional }).isEqualTo(listOf(false, false, false))
  }

  @Test
  fun `multiple optional depends on the same plugin is allowed`() {
    val descriptor = loadDescriptorFromTestDataDir("duplicateDepends-optional")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.dependencies.map { it.pluginId.idString }).isEqualTo(listOf("foo", "foo"))
    assertThat(descriptor.dependencies.map { it.isOptional }).isEqualTo(listOf(true, true))
  }

  @Test
  fun `directory with a plugin xml in (classes - META-INF) subdirectory is a plugin`() {
    val tempDir = directoryContent {
      dir("lib") {
        zip("empty.jar") {}
      }
      dir("classes") {
        dir("META-INF") {
          file("plugin.xml",
               """<idea-plugin>
               |  <id>foo.bar</id>
               |</idea-plugin>""".trimMargin())
        }
      }
    }.generateInTempDir() // todo maybe make generateIn(path) to use rootPath here instead
    val descriptor = loadAndInitDescriptorInTest(tempDir)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.pluginId.idString).isEqualTo("foo.bar")
    assertThat(descriptor.jarFiles).isNotNull()
    assertThat(descriptor.jarFiles!!.map { it.name }).isEqualTo(listOf("classes", "empty.jar"))
  }

  @Test
  fun `directory with a plugin xml in (META-INF) subdirectory is a plugin`() {
    val tempDir = directoryContent {
      dir("classes") {
        classFile("Empty") {}
      }
      dir("lib") {
        zip("empty.jar") {}
      }
      dir("META-INF") {
        file("plugin.xml",
             """<idea-plugin>
               |  <id>foo.bar</id>
               |</idea-plugin>""".trimMargin())
      }
    }.generateInTempDir()
    val descriptor = loadAndInitDescriptorInTest(tempDir)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.pluginId.idString).isEqualTo("foo.bar")
    assertThat(descriptor.jarFiles).isNotNull()
    assertThat(descriptor.jarFiles!!.map { it.name }).isEqualTo(listOf("classes", "empty.jar"))
  }

  @Test
  fun `descriptor with vendor and release date loads`() {
    val pluginFile = pluginDirPath.resolve(PluginManagerCore.PLUGIN_XML_PATH)
    val descriptor = readAndInitDescriptorFromBytesForTest(pluginFile, false, """
    <idea-plugin>
      <id>bar</id>
      <vendor>JetBrains</vendor>
      <product-descriptor code="IJ" release-date="20190811" release-version="42" optional="true"/>
    </idea-plugin>""".encodeToByteArray())
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.vendor).isEqualTo("JetBrains")
    assertThat(SimpleDateFormat("yyyyMMdd", Locale.US).format(descriptor.releaseDate)).isEqualTo("20190811")
    assertThat(descriptor.releaseVersion).isEqualTo(42)
    assertThat(descriptor.isLicenseOptional).isTrue()
  }

  @Test
  fun `descriptor with project components loads`() {
    val pluginFile = pluginDirPath.resolve(PluginManagerCore.PLUGIN_XML_PATH)
    pluginFile.write("""<idea-plugin>
  <id>bar</id>
  <project-components>
    <component>
      <implementation-class>com.intellij.ide.favoritesTreeView.FavoritesManager</implementation-class>
      <option name="workspace" value="true"/>
    </component>
  </project-components>
</idea-plugin>""")
    val descriptor = loadAndInitDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.projectContainerDescriptor.components[0].options).isEqualTo(Collections.singletonMap("workspace", "true"))
  }

  @Test
  fun `descriptor with a v2 content module with a slash in its name loads if module descriptor file has a dot instead of a slash`() {
    PluginBuilder.empty().id("bar")
      .module(moduleName = "bar/module",
              PluginBuilder.empty().packagePrefix("bar.module"),
              loadingRule = ModuleLoadingRule.REQUIRED,
              moduleFile = "bar.module.xml")
      .build(pluginDirPath)
    val descriptor = loadAndInitDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
      .isMarkedEnabled()
      .hasExactlyEnabledContentModules("bar/module")
  }

  @Test
  fun `descriptor with a v2 content module with a slash in its name does not load if module descriptor file is placed in a subdirectory`() {
    PluginBuilder.empty().id("bar")
      .module(moduleName = "bar/module",
              PluginBuilder.empty().packagePrefix("bar.module"),
              loadingRule = ModuleLoadingRule.REQUIRED,
              moduleFile = "bar/module.xml")
      .build(pluginDirPath)
    assertThatThrownBy {
      val descriptor = loadAndInitDescriptorInTest(pluginDirPath)
      assertThat(descriptor).isNotNull
        .isNotMarkedEnabled()
        .doesNotHaveEnabledContentModules()
    }.hasMessageContaining("Cannot resolve bar.module.xml")
  }

  @Test
  fun `descriptor with a v2 content module with multiple slashes in its name does not load`() {
    PluginBuilder.empty().id("bar")
      .module(moduleName = "bar/module/sub",
              PluginBuilder.empty().packagePrefix("bar.module.sub"),
              loadingRule = ModuleLoadingRule.REQUIRED,
              moduleFile = "bar.module.sub.xml")
      .build(pluginDirPath)
    assertThatThrownBy {
      val descriptor = loadAndInitDescriptorInTest(pluginDirPath)
      assertThat(descriptor).isNotNull
        .isNotMarkedEnabled()
        .doesNotHaveEnabledContentModules()
    }.hasMessageContaining("Cannot resolve bar/module.sub.xml") // note that only the last slash is substituted
  }

  @Test
  fun `descriptor with a v2 content module with multiple slashes in its name loads from a subdirectory`() { // FIXME
    PluginBuilder.empty().id("bar")
      .module(moduleName = "bar/module/sub",
              PluginBuilder.empty().packagePrefix("bar.module.sub"),
              loadingRule = ModuleLoadingRule.REQUIRED,
              moduleFile = "bar/module.sub.xml")
      .build(pluginDirPath)
    val descriptor = loadAndInitDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
      .isMarkedEnabled()
      .hasExactlyEnabledContentModules("bar/module/sub")
  }

  @Test
  fun `module descriptor's text can be embedded inside the content module element`() {
    pluginDirPath.resolve("META-INF/plugin.xml").writeXml("""
      <idea-plugin>
        <id>foo</id>
        <content>
          <module name="foo.module"><![CDATA[
            <idea-plugin>
                <extensions defaultExtensionNs="com.intellij">
                    <applicationService serviceImplementation="foo.module.service"/>
                </extensions>
            </idea-plugin>          
          ]]></module>
        </content>
      </idea-plugin>
    """.trimIndent())
    val descriptor = loadAndInitDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
      .isMarkedEnabled()
      .hasExactlyEnabledContentModules("foo.module")
    assertThat(descriptor.content.modules[0].requireDescriptor())
      .isMarkedEnabled()
      .hasExactlyApplicationServices("foo.module.service")
  }

  @Test
  fun `id, version, name are inherited in content modules`() {
    PluginBuilder.empty()
      .id("bar")
      .name("Bar")
      .version("1.0.0")
      .module(
        moduleName = "bar.sub",
        moduleDescriptor = PluginBuilder.empty()
          .id("bar 2")
          .name("Bar Sub")
          .version("2.0.0")
      )
      .build(pluginDirPath)

    val descriptor = loadAndInitDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.pluginId.idString).isEqualTo("bar")
    assertThat(descriptor.name).isEqualTo("Bar")
    assertThat(descriptor.version).isEqualTo("1.0.0")
    assertThat(descriptor.content.modules).hasSize(1)
    val subDesc = descriptor.content.modules[0].requireDescriptor()
    assertThat(subDesc.pluginId.idString).isEqualTo("bar")
    assertThat(subDesc.name).isEqualTo("Bar")
    assertThat(subDesc.version).isEqualTo("1.0.0")
  }

  @Test
  fun `id, version, name can't overridden in submodules`() {
    PluginBuilder.empty()
      .id("bar")
      .name("Bar")
      .version("1.0.0")
      .module(
        moduleName = "bar.sub",
        moduleDescriptor = PluginBuilder.empty()
          .id("bar 2")
          .name("Bar Sub")
          .version("2.0.0")
      )
      .build(pluginDirPath)

    val descriptor = loadAndInitDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.pluginId.idString).isEqualTo("bar")
    assertThat(descriptor.name).isEqualTo("Bar")
    assertThat(descriptor.version).isEqualTo("1.0.0")
    assertThat(descriptor.content.modules).hasSize(1)
    val subDesc = descriptor.content.modules[0].requireDescriptor()
    assertThat(subDesc.pluginId.idString).isEqualTo("bar")
    assertThat(subDesc.name).isEqualTo("Bar")
    assertThat(subDesc.version).isEqualTo("1.0.0")
  }

  @Test
  fun `resource bundle is not inherited in content modules`() {
    PluginBuilder.empty().id("bar")
      .resourceBundle("resourceBundle")
      .module(moduleName = "bar.opt", moduleDescriptor = PluginBuilder.empty(), loadingRule = ModuleLoadingRule.OPTIONAL)
      .module(moduleName = "bar.req", moduleDescriptor = PluginBuilder.empty(), loadingRule = ModuleLoadingRule.REQUIRED)
      .module(moduleName = "bar.emb", moduleDescriptor = PluginBuilder.empty(), loadingRule = ModuleLoadingRule.EMBEDDED)
      .build(pluginDirPath)

    val descriptor = loadAndInitDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.pluginId.idString).isEqualTo("bar")
    assertThat(descriptor.resourceBundleBaseName).isEqualTo("resourceBundle")
    assertThat(descriptor.content.modules).hasSize(3)
    assertThat(descriptor.content.modules).allMatch { it.requireDescriptor().resourceBundleBaseName == null }
  }

  @Test
  fun `resource bundle can be set in content modules`() {
    PluginBuilder.empty().id("bar")
      .resourceBundle("resourceBundle")
      .module(moduleName = "opt", moduleDescriptor = PluginBuilder.empty().resourceBundle("opt"), loadingRule = ModuleLoadingRule.OPTIONAL)
      .module(moduleName = "req", moduleDescriptor = PluginBuilder.empty().resourceBundle("req"), loadingRule = ModuleLoadingRule.REQUIRED)
      .module(moduleName = "emb", moduleDescriptor = PluginBuilder.empty().resourceBundle("emb"), loadingRule = ModuleLoadingRule.EMBEDDED)
      .build(pluginDirPath)

    val descriptor = loadAndInitDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.pluginId.idString).isEqualTo("bar")
    assertThat(descriptor.resourceBundleBaseName).isEqualTo("resourceBundle")
    assertThat(descriptor.content.modules).hasSize(3)
    assertThat(descriptor.content.modules).allMatch { it.requireDescriptor().resourceBundleBaseName == it.name }
  }

  @Test
  fun `core plugin has implicit host and product mode plugin aliases`() {
    PluginBuilder.empty()
      .id("com.intellij")
      .build(pluginDirPath)
    val descriptor = loadAndInitDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
    val hostIds = IdeaPluginOsRequirement.getHostOsModuleIds()
    if (hostIds.isEmpty()) {
      logger<PluginDescriptorTest>().warn("No host OS plugin aliases")
    }
    val productAliases = IdeaPluginDescriptorImpl.productModeAliasesForCorePlugin()
    if (productAliases.isEmpty()) {
      logger<PluginDescriptorTest>().warn("No product mode plugin aliases")
    }
    assertThat(descriptor.pluginAliases)
      .containsAll(hostIds)
      .containsAll(productAliases)
  }

  @Test
  fun `content module may have content modules but they are disregarded`() {
    PluginBuilder.empty().id("bar")
      .module(moduleName = "bar.module",
              PluginBuilder.empty()
                .packagePrefix("bar.module")
                .module("bar.module.inner",
                        PluginBuilder.empty().packagePrefix("bar.module.inner"),
                        loadingRule = ModuleLoadingRule.REQUIRED),
              loadingRule = ModuleLoadingRule.REQUIRED)
      .build(pluginDirPath)
    val (bar, err) = runAndReturnWithLoggedError { loadAndInitDescriptorInTest(pluginDirPath) }
    assertThat(err).hasMessageContainingAll("Unexpected `content` elements in a content module")
    assertThat(bar).isNotNull
      .isMarkedEnabled()
      .hasExactlyEnabledContentModules("bar.module")
    val barModule = bar.content.modules[0].requireDescriptor()
    assertThat(barModule).isNotNull
      .isMarkedEnabled()
      .doesNotHaveEnabledContentModules()
    assertThat(barModule.content.modules).hasSize(1)
    assertThat(barModule.content.modules[0].getDescriptorOrNull()).isNull()
  }

  // todo this is rather about plugin set loading, probably needs to be moved out
  @Test
  fun `only one instance of a plugin is loaded if it's duplicated`() {
    val urls = arrayOf(
      Path.of(testDataPath, "duplicate1.jar").toUri().toURL(),
      Path.of(testDataPath, "duplicate2.jar").toUri().toURL()
    )
    assertThat(loadAndInitDescriptorsFromClassPathInTest(URLClassLoader(urls, null))).hasSize(1)
  }

  // todo this is rather about plugin set loading, probably needs to be moved out
  @Test
  fun testUrlTolerance() {
    class EnumerationAdapter<T>(elements: List<T>) : Enumeration<T> {
      val it = elements.iterator()
      override fun hasMoreElements(): Boolean = it.hasNext()
      override fun nextElement(): T = it.next()
    }
    class TestLoader(prefix: String, suffix: String) : UrlClassLoader(build(), false) {
      private val url = URL(prefix + File(testDataPath).toURI().toURL().toString() + suffix + "META-INF/plugin.xml")
      override fun getResource(name: String) = null
      override fun getResources(name: String) = EnumerationAdapter(listOf(url))
    }
    assertThat(loadAndInitDescriptorsFromClassPathInTest(TestLoader("", "/spaces%20spaces/"))).hasSize(1)
    assertThat(loadAndInitDescriptorsFromClassPathInTest(TestLoader("", "/spaces spaces/"))).hasSize(1)
    assertThat(loadAndInitDescriptorsFromClassPathInTest(TestLoader("jar:", "/jar%20spaces.jar!/"))).hasSize(1)
    assertThat(loadAndInitDescriptorsFromClassPathInTest(TestLoader("jar:", "/jar spaces.jar!/"))).hasSize(1)
  }

  // todo equals of IdeaPluginDescriptorImpl is also dependent on sub-descriptor location (depends optional)
  @Test
  fun testEqualityById() {
    val tempFile = rootPath.resolve(PluginManagerCore.PLUGIN_XML_PATH)
    tempFile.write("""
<idea-plugin>
  <id>ID</id>
  <name>A</name>
</idea-plugin>""")
    val impl1 = loadAndInitDescriptorInTest(rootPath)

    tempFile.write("""
<idea-plugin>
  <id>ID</id>
  <name>B</name>
</idea-plugin>""")
    val impl2 = loadAndInitDescriptorInTest(rootPath)

    assertEquals(impl1, impl2)
    assertEquals(impl1.hashCode(), impl2.hashCode())
    assertNotEquals(impl1.name, impl2.name)
  }

  // todo why does it needs to know disabled plugins?
  @Test
  fun testLoadDisabledPlugin() {
    val descriptor = loadDescriptorFromTestDataDir(
      dirName = "disabled",
      disabledPlugins = setOf("com.intellij.disabled"),
    )
    assertFalse(descriptor.isEnabled)
    assertEquals("This is a disabled plugin", descriptor.description)
    assertThat(descriptor.dependencies.map { it.pluginId.idString }).containsExactly("com.intellij.modules.lang")
  }

  companion object {
    private val testDataPath: String
      get() = "${PlatformTestUtil.getPlatformTestDataPath()}plugins/pluginDescriptor"

    private fun loadDescriptorFromTestDataDir(
      dirName: String,
      disabledPlugins: Set<String> = emptySet(),
    ): IdeaPluginDescriptorImpl {
      return loadAndInitDescriptorInTest(
        dir = Path.of(testDataPath, dirName),
        disabledPlugins = disabledPlugins,
      )
    }

    private fun Path.writeXml(@Language("XML") text: String) {
      createParentDirectories().write(text.encodeToByteArray())
    }
  }
}
