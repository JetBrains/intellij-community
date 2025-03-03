// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax", "ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.directoryContent
import com.intellij.util.io.java.classFile
import com.intellij.util.io.write
import com.intellij.util.lang.UrlClassLoader
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.createParentDirectories
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PluginDescriptorTest {
  @TestDataPath("\$CONTENT_ROOT/testData/plugins/pluginDescriptor") @Suppress("unused")
  private class TestDataRef // for easy navigation

  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

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
    assertThat(descriptor.pluginDependencies)
      .singleElement()
      .extracting { it.isOptional }.isEqualTo(true)
  }

  @Test
  fun `descriptor with multiple depends-optional loads`() {
    val descriptor = loadDescriptorFromTestDataDir("multipleDependsOptional")
    assertThat(descriptor).isNotNull()
    val pluginDependencies = descriptor.pluginDependencies
    assertThat(pluginDependencies).hasSize(2)
    assertThat(pluginDependencies.map { it.isOptional }).allMatch { it == true }
    assertThat(pluginDependencies.map { it.pluginId.idString }).containsExactly("dep2", "dep1")
  }
  
  @Test
  fun `descriptor with multiple plugin dependencies loads`() {
    val descriptor = loadDescriptorFromTestDataDir("multiplePluginDependencies")
    assertThat(descriptor).isNotNull()
    val pluginDependencies = descriptor.dependencies.plugins
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

  // todo revisit
  @Test
  fun `strict depends makes only one another optional depends on the same plugin strict too and is removed`() {
    val descriptor = loadDescriptorFromTestDataDir("duplicateDepends-strict")
    assertThat(descriptor).isNotNull()
    // fixme what is that result o_O
    assertThat(descriptor.pluginDependencies.map { it.pluginId.idString }).isEqualTo(listOf("foo", "foo"))
    assertThat(descriptor.pluginDependencies.map { it.isOptional }).isEqualTo(listOf(false, true))
    //assertThat(descriptor.pluginDependencies.map { it.pluginId }).isEqualTo(listOf("foo", "foo", "foo"))
    //assertThat(descriptor.pluginDependencies.map { it.isOptional }).isEqualTo(listOf(false, false, false))
  }

  @Test
  fun `multiple optional depends on the same plugin is allowed`() {
    val descriptor = loadDescriptorFromTestDataDir("duplicateDepends-optional")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.pluginDependencies.map { it.pluginId.idString }).isEqualTo(listOf("foo", "foo"))
    assertThat(descriptor.pluginDependencies.map { it.isOptional }).isEqualTo(listOf(true, true))
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
    val descriptor = loadDescriptorInTest(tempDir)
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
    val descriptor = loadDescriptorInTest(tempDir)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.pluginId.idString).isEqualTo("foo.bar")
    assertThat(descriptor.jarFiles).isNotNull()
    assertThat(descriptor.jarFiles!!.map { it.name }).isEqualTo(listOf("classes", "empty.jar"))
  }

  @Test
  fun `descriptor with vendor and release date loads`() {
    val pluginFile = pluginDirPath.resolve(PluginManagerCore.PLUGIN_XML_PATH)
    val descriptor = readDescriptorForTest(pluginFile, false, """
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
    val descriptor = loadDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.projectContainerDescriptor.components[0].options).isEqualTo(Collections.singletonMap("workspace", "true"))
  }

  @Test
  fun `descriptor with a v2 content module with a slash in its name loads if module descriptor file has a dot instead of a slash`() {
    PluginBuilder.empty().id("bar")
      .module(moduleName = "bar/module",
              PluginBuilder.withModulesLang().packagePrefix("bar.module"),
              loadingRule = ModuleLoadingRule.REQUIRED,
              moduleFile = "bar.module.xml")
      .build(pluginDirPath)
    val descriptor = loadDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
      .isMarkedEnabled()
      .hasExactlyEnabledContentModules("bar/module")
  }

  @Test
  fun `descriptor with a v2 content module with a slash in its name does not load if module descriptor file is placed in a subdirectory`() {
    PluginBuilder.empty().id("bar")
      .module(moduleName = "bar/module",
              PluginBuilder.withModulesLang().packagePrefix("bar.module"),
              loadingRule = ModuleLoadingRule.REQUIRED,
              moduleFile = "bar/module.xml")
      .build(pluginDirPath)
    assertThatThrownBy {
      val descriptor = loadDescriptorInTest(pluginDirPath)
      assertThat(descriptor).isNotNull
        .isNotMarkedEnabled()
        .doesNotHaveEnabledContentModules()
    }.hasMessageContaining("Cannot resolve bar.module.xml")
  }

  @Test
  fun `descriptor with a v2 content module with multiple slashes in its name does not load`() {
    PluginBuilder.empty().id("bar")
      .module(moduleName = "bar/module/sub",
              PluginBuilder.withModulesLang().packagePrefix("bar.module.sub"),
              loadingRule = ModuleLoadingRule.REQUIRED,
              moduleFile = "bar.module.sub.xml")
      .build(pluginDirPath)
    assertThatThrownBy {
      val descriptor = loadDescriptorInTest(pluginDirPath)
      assertThat(descriptor).isNotNull
        .isNotMarkedEnabled()
        .doesNotHaveEnabledContentModules()
    }.hasMessageContaining("Cannot resolve bar/module.sub.xml") // note that only the last slash is substituted
  }

  @Test
  fun `descriptor with a v2 content module with multiple slashes in its name loads from a subdirectory`() { // FIXME
    PluginBuilder.empty().id("bar")
      .module(moduleName = "bar/module/sub",
              PluginBuilder.withModulesLang().packagePrefix("bar.module.sub"),
              loadingRule = ModuleLoadingRule.REQUIRED,
              moduleFile = "bar/module.sub.xml")
      .build(pluginDirPath)
    val descriptor = loadDescriptorInTest(pluginDirPath)
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
    val descriptor = loadDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
      .isMarkedEnabled()
      .hasExactlyEnabledContentModules("foo.module")
    assertThat(descriptor.content.modules[0].requireDescriptor())
      .isMarkedEnabled()
      .hasExactlyApplicationServices("foo.module.service")
  }

  // todo this is rather about plugin set loading, probably needs to be moved out
  @Test
  fun `only one instance of a plugin is loaded if it's duplicated`() {
    val urls = arrayOf(
      Path.of(testDataPath, "duplicate1.jar").toUri().toURL(),
      Path.of(testDataPath, "duplicate2.jar").toUri().toURL()
    )
    assertThat(testLoadDescriptorsFromClassPath(URLClassLoader(urls, null))).hasSize(1)
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
    assertThat(testLoadDescriptorsFromClassPath(TestLoader("", "/spaces%20spaces/"))).hasSize(1)
    assertThat(testLoadDescriptorsFromClassPath(TestLoader("", "/spaces spaces/"))).hasSize(1)
    assertThat(testLoadDescriptorsFromClassPath(TestLoader("jar:", "/jar%20spaces.jar!/"))).hasSize(1)
    assertThat(testLoadDescriptorsFromClassPath(TestLoader("jar:", "/jar spaces.jar!/"))).hasSize(1)
  }

  // todo equals of IdeaPluginDescriptorImpl is also dependent on subdescriptor location (depends optional)
  @Test
  fun testEqualityById() {
    val tempFile = rootPath.resolve(PluginManagerCore.PLUGIN_XML_PATH)
    tempFile.write("""
<idea-plugin>
  <id>ID</id>
  <name>A</name>
</idea-plugin>""")
    val impl1 = loadDescriptorInTest(rootPath)

    tempFile.write("""
<idea-plugin>
  <id>ID</id>
  <name>B</name>
</idea-plugin>""")
    val impl2 = loadDescriptorInTest(rootPath)

    TestCase.assertEquals(impl1, impl2)
    TestCase.assertEquals(impl1.hashCode(), impl2.hashCode())
    TestCase.assertNotSame(impl1.name, impl2.name)
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
    assertThat(descriptor.pluginDependencies.map { it.pluginId.idString }).containsExactly("com.intellij.modules.lang")
  }

  companion object {
    private val testDataPath: String
      get() = "${PlatformTestUtil.getPlatformTestDataPath()}plugins/pluginDescriptor"

    private fun loadDescriptorFromTestDataDir(
      dirName: String,
      disabledPlugins: Set<String> = emptySet(),
    ): IdeaPluginDescriptorImpl {
      return loadDescriptorInTest(
        dir = Path.of(testDataPath, dirName),
        disabledPlugins = disabledPlugins,
      )
    }

    private fun Path.writeXml(@Language("XML") text: String) {
      createParentDirectories().write(text.encodeToByteArray())
    }
  }
}
