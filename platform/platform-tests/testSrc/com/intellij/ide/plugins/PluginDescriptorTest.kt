// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UsePropertyAccessSyntax")

package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.write
import com.intellij.util.lang.UrlClassLoader
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.intellij.lang.annotations.Language
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*

class PluginDescriptorTest {
  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  @Test
  fun testDescriptorLoading() {
    val descriptor = loadDescriptor("asp.jar")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.pluginId.idString).isEqualTo("com.jetbrains.plugins.asp")
    assertThat(descriptor.name).isEqualTo("ASP")
  }

  @Test
  fun testOptionalDescriptors() {
    val descriptor = loadDescriptor("family")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.optionalConfigs.size).isEqualTo(1)
  }

  @Test
  fun testMultipleOptionalDescriptors() {
    val descriptor = loadDescriptor("multipleOptionalDescriptors")
    assertThat(descriptor).isNotNull()
    val ids = descriptor.optionalConfigs.keys
    assertThat(ids).hasSize(2)
    assertThat(ids.map { it.idString }).containsExactly("dep2", "dep1")
  }

  @Test
  fun testMalformedDescriptor() {
    @Suppress("GrazieInspection")
    assertThatThrownBy { loadDescriptor("malformed") }
      .hasMessageContaining("Content is not allowed in prolog.")
  }

  @Test
  fun testAnonymousDescriptor() {
    val descriptor = loadDescriptor("anonymous")
    assertThat(descriptor.pluginId).isNull()
    assertThat(descriptor.name).isNull()
  }

  @Test
  fun testCyclicOptionalDeps() {
    assertThatThrownBy { loadDescriptor("cyclicOptionalDeps") }
      .hasMessage("Plugin someId optional descriptors form a cycle: a.xml, b.xml")
  }

  @Test
  fun testFilteringDuplicates() {
    val urls = arrayOf(
      Paths.get(testDataPath, "duplicate1.jar").toUri().toURL(),
      Paths.get(testDataPath, "duplicate2.jar").toUri().toURL()
    )
    assertThat(
      PluginManagerCore.testLoadDescriptorsFromClassPath(URLClassLoader(urls, null))).hasSize(1)
  }

  @Test
  fun testProductionPlugins() {
    assumeTrue(SystemInfo.isMac && !UsefulTestCase.IS_UNDER_TEAMCITY)
    val descriptors = PluginManagerCore.testLoadDescriptorsFromDir(Paths.get("/Applications/Idea.app/Contents/plugins")).sortedPlugins
    assertThat(descriptors).isNotEmpty()
    assertThat(descriptors.find { it!!.pluginId.idString == "com.intellij.java" }).isNotNull
  }

  @Test
  fun testProductionProductLib() {
    assumeTrue(SystemInfo.isMac && !UsefulTestCase.IS_UNDER_TEAMCITY)
    val urls = ArrayList<URL>()
    Paths.get("/Applications/Idea.app/Contents/lib").directoryStreamIfExists {
      for (path in it) {
        urls.add(path.toUri().toURL())
      }
    }
    val descriptors = PluginManagerCore.testLoadDescriptorsFromClassPath(URLClassLoader(urls.toTypedArray(), null))
    // core and com.intellij.workspace
    assertThat(descriptors).hasSize(1)
  }

  @Test
  fun testProduction2() {
    assumeTrue(SystemInfo.isMac && !UsefulTestCase.IS_UNDER_TEAMCITY)
    val descriptors = PluginManagerCore.testLoadDescriptorsFromDir(Paths.get("/Volumes/data/plugins")).sortedPlugins
    assertThat(descriptors).isNotEmpty()
  }

  @Test
  fun testDuplicateDependency() {
    val descriptor = loadDescriptor("duplicateDependency")
    assertThat(descriptor).isNotNull()
    assertThat(
      descriptor.optionalDependentPluginIds).isEmpty()
    assertThat(
      descriptor.dependentPluginIds).containsExactly(PluginId.getId("foo"))
  }

  @Test
  fun testPluginNameAsId() {
    val descriptor = loadDescriptor("noId")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.pluginId.idString).isEqualTo(descriptor.name)
  }

  @Test
  fun releaseDate() {
    val pluginFile = inMemoryFs.fs.getPath("plugin/META-INF/plugin.xml")
    pluginFile.write("""
<idea-plugin>
  <id>bar</id>
  <vendor>JetBrains</vendor>
  <product-descriptor code="IJ" release-date="20190811" release-version="42"/>
</idea-plugin>""")
    val descriptor = loadDescriptor(pluginFile.parent.parent)
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.vendor).isEqualTo("JetBrains")
    assertThat(SimpleDateFormat("yyyyMMdd", Locale.US).format(descriptor.releaseDate)).isEqualTo("20190811")
  }

  @Suppress("PluginXmlValidity")
  @Test
  fun `use newer plugin`() {
    val pluginDir = inMemoryFs.fs.getPath("/plugins")
    writeDescriptor("foo_1-0", pluginDir, """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>1.0</version>
      </idea-plugin>""")
    writeDescriptor("foo_2-0", pluginDir, """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>2.0</version>
      </idea-plugin>""")

    val result = PluginManagerCore.testLoadDescriptorsFromDir(pluginDir)
    val plugins = result.sortedEnabledPlugins
    assertThat(plugins).hasSize(1)
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("2.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(result.idMap).containsOnlyKeys(foo.pluginId)
    assertThat(result.idMap[foo.pluginId]).isSameAs(foo)
  }

  @Suppress("PluginXmlValidity")
  @Test
  fun `use first plugin if both versions the same`() {
    val pluginDir = inMemoryFs.fs.getPath("/plugins")
    writeDescriptor("foo_1-0", pluginDir, """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>1.0</version>
      </idea-plugin>""")
    writeDescriptor("foo_another", pluginDir, """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>1.0</version>
      </idea-plugin>""")

    val result = PluginManagerCore.testLoadDescriptorsFromDir(pluginDir)
    val plugins = result.sortedEnabledPlugins
    assertThat(plugins).hasSize(1)
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("1.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(result.idMap).containsOnlyKeys(foo.pluginId)
    assertThat(result.idMap[foo.pluginId]).isSameAs(foo)
  }

  @Suppress("PluginXmlValidity")
  @Test
  fun classLoader() {
    val pluginDir = inMemoryFs.fs.getPath("/plugins")
    writeDescriptor("foo", pluginDir, """
    <idea-plugin>
      <id>foo</id>
      <depends>bar</depends>
      <vendor>JetBrains</vendor>
    </idea-plugin>""")
    writeDescriptor("bar", pluginDir, """
    <idea-plugin>
      <id>bar</id>
      <vendor>JetBrains</vendor>
    </idea-plugin>""")

    checkClassLoader(pluginDir)
  }

  @Suppress("PluginXmlValidity")
  @Test
  fun `classLoader - optional dependency`() {
    val pluginDir = inMemoryFs.fs.getPath("/plugins")
    writeDescriptor("foo", pluginDir, """
    <idea-plugin>
      <id>foo</id>
      <depends optional="true" config-file="stream-debugger.xml">bar</depends>
      <vendor>JetBrains</vendor>
    </idea-plugin>""")

    pluginDir.resolve("foo/META-INF/stream-debugger.xml").write("""
       <idea-plugin>
        <actions>
        </actions>
      </idea-plugin>
    """.trimIndent())

    writeDescriptor("bar", pluginDir, """
    <idea-plugin>
      <id>bar</id>
      <vendor>JetBrains</vendor>
    </idea-plugin>""")

    checkClassLoader(pluginDir)
  }

  private fun checkClassLoader(pluginDir: Path) {
    val list = PluginManagerCore.testLoadDescriptorsFromDir(pluginDir).sortedEnabledPlugins
    assertThat(list).hasSize(2)

    val bar = list[0]
    assertThat(bar.pluginId.idString).isEqualTo("bar")

    val foo = list[1]

    assertThat(foo.dependentPluginIds).containsExactly(bar.pluginId)

    assertThat(foo.pluginId.idString).isEqualTo("foo")
    val fooClassLoader = foo.pluginClassLoader as PluginClassLoader
    assertThat(fooClassLoader._getParents()).containsExactly(bar.pluginClassLoader)
  }

  @Test
  fun componentConfig() {
    val pluginFile = inMemoryFs.fs.getPath("/plugin/META-INF/plugin.xml")
    pluginFile.write("<idea-plugin>\n  <id>bar</id>\n  <project-components>\n    <component>\n      <implementation-class>com.intellij.ide.favoritesTreeView.FavoritesManager</implementation-class>\n      <option name=\"workspace\" value=\"true\"/>\n    </component>\n\n    \n  </project-components>\n</idea-plugin>")
    val descriptor = loadDescriptor(pluginFile.parent.parent)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.projectContainerDescriptor.components!![0].options).isEqualTo(Collections.singletonMap("workspace", "true"))
  }

  @Test
  fun testPluginIdAsName() {
    val descriptor = loadDescriptor("noName")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.name).isEqualTo(descriptor.pluginId.idString)
  }

  @Test
  fun testUrlTolerance() {
    class SingleUrlEnumeration(private val myUrl: URL) : Enumeration<URL> {
      private var hasMoreElements = true
      override fun hasMoreElements(): Boolean {
        return hasMoreElements
      }

      override fun nextElement(): URL {
        if (!hasMoreElements) throw NoSuchElementException()
        hasMoreElements = false
        return myUrl
      }

    }

    class TestLoader(prefix: String, suffix: String) : UrlClassLoader(build()) {
      private val url = URL(prefix + File(testDataPath).toURI().toURL().toString() + suffix + "META-INF/plugin.xml")

      override fun getResource(name: String) = null

      override fun getResources(name: String) = SingleUrlEnumeration(url)
    }

    val loader1 = TestLoader("", "/spaces%20spaces/")
    TestCase.assertEquals(1, PluginManagerCore.testLoadDescriptorsFromClassPath(loader1).size)
    val loader2 = TestLoader("", "/spaces spaces/")
    TestCase.assertEquals(1, PluginManagerCore.testLoadDescriptorsFromClassPath(loader2).size)
    val loader3 = TestLoader("jar:", "/jar%20spaces.jar!/")
    TestCase.assertEquals(1, PluginManagerCore.testLoadDescriptorsFromClassPath(loader3).size)
    val loader4 = TestLoader("jar:", "/jar spaces.jar!/")
    assertThat(PluginManagerCore.testLoadDescriptorsFromClassPath(loader4)).hasSize(1)
  }

  @Test
  fun testEqualityById() {
    val fs = inMemoryFs.fs
    val tempFile = fs.getPath("/", PluginManagerCore.PLUGIN_XML_PATH)
    tempFile.write("""
<idea-plugin>
  <id>ID</id>
  <name>A</name>
</idea-plugin>""")
    val impl1 = loadDescriptor(fs.getPath("/"))
    tempFile.write("""
<idea-plugin>
  <id>ID</id>
  <name>B</name>
</idea-plugin>""")
    val impl2 = loadDescriptor(fs.getPath("/"))
    TestCase.assertEquals(impl1, impl2)
    TestCase.assertEquals(impl1.hashCode(), impl2.hashCode())
    TestCase.assertNotSame(impl1.name, impl2.name)
  }
}

private fun writeDescriptor(id: String, pluginDir: Path, @Language("xml") data: String) {
  pluginDir.resolve("$id/META-INF/plugin.xml").write(data.trimIndent())
}

private val testDataPath: String
  get() = PlatformTestUtil.getPlatformTestDataPath() + "plugins/pluginDescriptor"

private fun loadDescriptor(dirName: String): IdeaPluginDescriptorImpl {
  return loadDescriptor(Paths.get(testDataPath, dirName))
}

private fun loadDescriptor(dir: Path): IdeaPluginDescriptorImpl {
  assertThat(dir).exists()
  PluginManagerCore.ourPluginError = null
  val parentContext = DescriptorListLoadingContext.createSingleDescriptorContext(emptySet())
  val result = DescriptorLoadingContext(parentContext, /* isBundled = */ false, /* isEssential = */ true, PathBasedJdomXIncluder.DEFAULT_PATH_RESOLVER).use { context ->
    PluginManagerCore.loadDescriptorFromFileOrDir(dir, PluginManagerCore.PLUGIN_XML, context, Files.isDirectory(dir))
  }
  if (result == null) {
    @Suppress("USELESS_CAST")
    assertThat(PluginManagerCore.ourPluginError as String?).isNotNull()
    PluginManagerCore.ourPluginError = null
  }
  return result!!
}