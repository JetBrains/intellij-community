// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax", "ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.NoOpXmlInterner
import com.intellij.util.io.directoryContent
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.write
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.lang.ZipFilePool
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.intellij.lang.annotations.Language
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private fun loadDescriptors(dir: Path, buildNumber: BuildNumber, disabledPlugins: Set<PluginId> = emptySet()): DescriptorListLoadingContext {
  val context = DescriptorListLoadingContext(disabledPlugins = disabledPlugins,
                                             result = PluginLoadingResult(emptyMap(), Supplier { buildNumber }))
  // constant order in tests
  val paths: List<Path> = dir.directoryStreamIfExists { it.sorted() }!!
  context.use {
    for (file in paths) {
      val descriptor = loadDescriptor(file, context) ?: continue
      context.result.add(descriptor, false)
    }
  }
  context.result.finishLoading()
  return context
}

private fun loadAndInitDescriptors(dir: Path, buildNumber: BuildNumber, disabledPlugins: Set<PluginId> = emptySet()): PluginManagerState {
  return PluginManagerCore.initializePlugins(loadDescriptors(dir, buildNumber, disabledPlugins), UrlClassLoader.build().get(), false, null)
}

class PluginDescriptorTest {
  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  @Test
  fun descriptorLoading() {
    val descriptor = loadDescriptorInTest("asp.jar")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.pluginId.idString).isEqualTo("com.jetbrains.plugins.asp")
    assertThat(descriptor.name).isEqualTo("ASP")
  }

  @Test
  fun testOptionalDescriptors() {
    val descriptor = loadDescriptorInTest("family")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.pluginDependencies.size).isEqualTo(1)
  }

  @Test
  fun testMultipleOptionalDescriptors() {
    val descriptor = loadDescriptorInTest("multipleOptionalDescriptors")
    assertThat(descriptor).isNotNull()
    val pluginDependencies = descriptor.pluginDependencies
    assertThat(pluginDependencies).hasSize(2)
    assertThat(pluginDependencies.map { it.pluginId.idString }).containsExactly("dep2", "dep1")
  }

  @Test
  fun testMalformedDescriptor() {
    assertThatThrownBy { loadDescriptorInTest("malformed") }
      .hasMessageContaining("Unexpected character 'o' (code 111) in prolog")
  }

  @Test
  fun nameAsId() {
    val descriptor = loadDescriptorInTest(Path.of(testDataPath, "anonymous"))
    assertThat(descriptor.pluginId.idString).isEqualTo("test")
    assertThat(descriptor.name).isEqualTo("test")
  }

  @Test
  fun testCyclicOptionalDeps() {
    assertThatThrownBy { loadDescriptorInTest("cyclicOptionalDeps") }
      .hasMessageEndingWith(" optional descriptors form a cycle: a.xml, b.xml")
  }

  @Test
  fun testFilteringDuplicates() {
    val urls = arrayOf(
      Path.of(testDataPath, "duplicate1.jar").toUri().toURL(),
      Path.of(testDataPath, "duplicate2.jar").toUri().toURL()
    )
    assertThat(testLoadDescriptorsFromClassPath(URLClassLoader(urls, null))).hasSize(1)
  }

  @Test
  fun testProductionPlugins() {
    IoTestUtil.assumeMacOS()
    assumeNotUnderTeamcity()
    val descriptors = loadAndInitDescriptors(Paths.get("/Applications/Idea.app/Contents/plugins"), PluginManagerCore.getBuildNumber()).pluginSet.allPlugins
    assertThat(descriptors).isNotEmpty()
    assertThat(descriptors.find { it.pluginId.idString == "com.intellij.java" }).isNotNull
  }

  @Test
  fun testProductionProductLib() {
    IoTestUtil.assumeMacOS()
    assumeNotUnderTeamcity()
    val urls = ArrayList<URL>()
    Paths.get("/Applications/Idea.app/Contents/lib").directoryStreamIfExists {
      for (path in it) {
        urls.add(path.toUri().toURL())
      }
    }
    val descriptors = testLoadDescriptorsFromClassPath(URLClassLoader(urls.toTypedArray(), null))
    // core and com.intellij.workspace
    assertThat(descriptors).hasSize(1)
  }

  @Test
  fun testProduction2() {
    IoTestUtil.assumeMacOS()

    assumeNotUnderTeamcity()
    val descriptors = loadAndInitDescriptors(Paths.get("/Volumes/data/plugins"), PluginManagerCore.getBuildNumber()).pluginSet.allPlugins
    assertThat(descriptors).isNotEmpty()
  }

  private fun assumeNotUnderTeamcity() {
    assumeTrue("Must not be run under TeamCity", !UsefulTestCase.IS_UNDER_TEAMCITY)
  }

  @Test
  fun testDuplicateDependency() {
    val descriptor = loadDescriptorInTest("duplicateDependency")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.pluginDependencies.filter { it.isOptional }).isEmpty()
    assertThat(descriptor.pluginDependencies.map { it.pluginId }).containsExactly(PluginId.getId("foo"))
  }

  @Test
  fun testPluginNameAsId() {
    val descriptor = loadDescriptorInTest("noId")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.pluginId.idString).isEqualTo(descriptor.name)
  }

  @Test
  fun testMetaInfInClasses() {
    val tempDir = directoryContent {
      dir("lib") {
        zip("empty.jar") {}
      }
      dir("classes") {
        dir("META-INF") {
          file("plugin.xml",
               """<idea-plugin>
               |  <id>foo.bar</id>
               |</idea-plugin>""")
        }
      }
    }.generateInTempDir()

    val descriptor = loadDescriptorInTest(tempDir)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.pluginId.idString).isEqualTo("foo.bar")

    assertThat(descriptor.jarFiles).isNotNull()
    assertThat(descriptor.jarFiles!!.map { it.name }).isEqualTo(listOf("classes", "empty.jar"))
  }

  @Test
  fun testStandaloneMetaInf() {
    val tempDir = directoryContent {
      dir("classes") {
        file("Empty.class", "") // `com.intellij.util.io.java.classFile` requires dependency on `intellij.java.testFramework`
      }
      dir("lib") {
        zip("empty.jar") {}
      }
      dir("META-INF") {
        file("plugin.xml",
             """<idea-plugin>
               |  <id>foo.bar</id>
               |</idea-plugin>""")
      }
    }.generateInTempDir()

    val descriptor = loadDescriptorInTest(tempDir)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.pluginId.idString).isEqualTo("foo.bar")

    assertThat(descriptor.jarFiles).isNotNull()
    assertThat(descriptor.jarFiles!!.map { it.name }).isEqualTo(listOf("classes", "empty.jar"))
  }

  @Test
  fun releaseDate() {
    val pluginFile = inMemoryFs.fs.getPath("plugin/META-INF/plugin.xml")
    val descriptor = readDescriptorForTest(pluginFile, false, """
    <idea-plugin>
      <id>bar</id>
      <vendor>JetBrains</vendor>
      <product-descriptor code="IJ" release-date="20190811" release-version="42" optional="true"/>
    </idea-plugin>""".encodeToByteArray())
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.vendor).isEqualTo("JetBrains")
    assertThat(SimpleDateFormat("yyyyMMdd", Locale.US).format(descriptor.releaseDate)).isEqualTo("20190811")
    assertThat(descriptor.isLicenseOptional).isTrue()
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

    val pluginSet = loadAndInitDescriptors(pluginDir, PluginManagerCore.getBuildNumber()).pluginSet
    val plugins = pluginSet.enabledPlugins
    assertThat(plugins).hasSize(1)
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("2.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(pluginSet.allPlugins.toList()).map(Function { it.pluginId }).containsOnly(foo.pluginId)
    assertThat(pluginSet.findEnabledPlugin(foo.pluginId)).isSameAs(foo)
  }

  @Suppress("PluginXmlValidity")
  @Test
  fun `use newer plugin if disabled`() {
    val pluginDir = inMemoryFs.fs.getPath("/plugins")
    writeDescriptor("foo_3-0", pluginDir, """
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

    val context = loadDescriptors(pluginDir, PluginManagerCore.getBuildNumber(), setOf(PluginId.getId("foo")))
    val plugins = context.result.incompletePlugins
    assertThat(plugins).hasSize(1)
    val foo = plugins.values.single()
    assertThat(foo.version).isEqualTo("2.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")
  }

  @Suppress("PluginXmlValidity")
  @Test
  fun `prefer bundled if custom is incompatible`() {
    val pluginDir = inMemoryFs.fs.getPath("/plugins")
    // names are important - will be loaded in alphabetical order
    writeDescriptor("foo_1-0", pluginDir, """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>2.0</version>
        <idea-version until-build="2"/>
      </idea-plugin>""")
    writeDescriptor("foo_2-0", pluginDir, """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>2.0</version>
        <idea-version until-build="4"/>
      </idea-plugin>""")

    val result = loadDescriptors(pluginDir, BuildNumber.fromString("4.0")!!).result
    assertThat(result.hasPluginErrors).isFalse()
    val plugins = result.getEnabledPlugins()
    assertThat(plugins).hasSize(1)
    assertThat(result.duplicateModuleMap).isNull()
    assertThat(result.incompletePlugins).isEmpty()
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("2.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(result.idMap).containsOnlyKeys(foo.pluginId)
    assertThat(result.idMap.get(foo.pluginId)).isSameAs(foo)
  }

  @Suppress("PluginXmlValidity")
  @Test
  fun `select compatible plugin if both versions provided`() {
    val pluginDir = inMemoryFs.fs.getPath("/plugins")
    writeDescriptor("foo_1-0", pluginDir, """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>1.0</version>
        <idea-version since-build="1.*" until-build="2.*"/>
      </idea-plugin>""")
    writeDescriptor("foo_2-0", pluginDir, """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>2.0</version>
        <idea-version since-build="2.0" until-build="4.*"/>
      </idea-plugin>""")

    val pluginSet = loadAndInitDescriptors(pluginDir, BuildNumber.fromString("3.12")!!).pluginSet

    val plugins = pluginSet.enabledPlugins
    assertThat(plugins).hasSize(1)
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("2.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(pluginSet.allPlugins.toList()).map(Function { it.pluginId }).containsOnly(foo.pluginId)
    assertThat(pluginSet.findEnabledPlugin(foo.pluginId)).isSameAs(foo)
  }

  @Suppress("PluginXmlValidity")
  @Test
  fun `use first plugin if both versions the same`() {
    val pluginDir = inMemoryFs.fs.getPath("/plugins")
    PluginBuilder().noDepends().id("foo").version("1.0").build(pluginDir.resolve("foo_1-0"))
    PluginBuilder().noDepends().id("foo").version("1.0").build(pluginDir.resolve("foo_another"))

    val pluginSet = loadAndInitDescriptors(pluginDir, PluginManagerCore.getBuildNumber()).pluginSet
    val plugins = pluginSet.enabledPlugins
    assertThat(plugins).hasSize(1)
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("1.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(pluginSet.allPlugins.toList()).map(Function { it.pluginId }).containsOnly(foo.pluginId)
    assertThat(pluginSet.findEnabledPlugin(foo.pluginId)).isSameAs(foo)
  }

  @Suppress("PluginXmlValidity")
  @Test
  fun classLoader() {
    val pluginDir = inMemoryFs.fs.getPath("/plugins")
    PluginBuilder().noDepends().id("foo").depends("bar").build(pluginDir.resolve("foo"))
    PluginBuilder().noDepends().id("bar").build(pluginDir.resolve("bar"))
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
    </idea-plugin>
    """)

    pluginDir.resolve("foo/META-INF/stream-debugger.xml").write("""
     <idea-plugin>
      <actions>
      </actions>
    </idea-plugin>
    """)

    writeDescriptor("bar", pluginDir, """
    <idea-plugin>
      <id>bar</id>
      <vendor>JetBrains</vendor>
    </idea-plugin>
    """)

    checkClassLoader(pluginDir)
  }

  private fun checkClassLoader(pluginDir: Path) {
    val list = loadAndInitDescriptors(pluginDir, PluginManagerCore.getBuildNumber()).pluginSet.enabledPlugins
    assertThat(list).hasSize(2)

    val bar = list[0]
    assertThat(bar.pluginId.idString).isEqualTo("bar")

    val foo = list[1]

    assertThat(foo.pluginDependencies.map { it.pluginId }).containsExactly(bar.pluginId)

    assertThat(foo.pluginId.idString).isEqualTo("foo")
    val fooClassLoader = foo.pluginClassLoader as PluginClassLoader
    assertThat(fooClassLoader._getParents()).containsExactly(bar)
  }

  @Test
  fun componentConfig() {
    val pluginFile = inMemoryFs.fs.getPath("/plugin/META-INF/plugin.xml")
    pluginFile.write("<idea-plugin>\n  <id>bar</id>\n  <project-components>\n    <component>\n      <implementation-class>com.intellij.ide.favoritesTreeView.FavoritesManager</implementation-class>\n      <option name=\"workspace\" value=\"true\"/>\n    </component>\n\n    \n  </project-components>\n</idea-plugin>")
    val descriptor = loadDescriptorInTest(pluginFile.parent.parent)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.projectContainerDescriptor.components!![0].options).isEqualTo(Collections.singletonMap("workspace", "true"))
  }

  @Test
  fun testPluginIdAsName() {
    val descriptor = loadDescriptorInTest("noName")
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

    class TestLoader(prefix: String, suffix: String) : UrlClassLoader(build(), false) {
      private val url = URL(prefix + File(testDataPath).toURI().toURL().toString() + suffix + "META-INF/plugin.xml")

      override fun getResource(name: String) = null

      override fun getResources(name: String) = SingleUrlEnumeration(url)
    }

    val loader1 = TestLoader("", "/spaces%20spaces/")
    TestCase.assertEquals(1, testLoadDescriptorsFromClassPath(loader1).size)
    val loader2 = TestLoader("", "/spaces spaces/")
    TestCase.assertEquals(1, testLoadDescriptorsFromClassPath(loader2).size)
    val loader3 = TestLoader("jar:", "/jar%20spaces.jar!/")
    TestCase.assertEquals(1, testLoadDescriptorsFromClassPath(loader3).size)
    val loader4 = TestLoader("jar:", "/jar spaces.jar!/")
    assertThat(testLoadDescriptorsFromClassPath(loader4)).hasSize(1)
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
    val impl1 = loadDescriptorInTest(fs.getPath("/"))
    tempFile.write("""
<idea-plugin>
  <id>ID</id>
  <name>B</name>
</idea-plugin>""")
    val impl2 = loadDescriptorInTest(fs.getPath("/"))
    TestCase.assertEquals(impl1, impl2)
    TestCase.assertEquals(impl1.hashCode(), impl2.hashCode())
    TestCase.assertNotSame(impl1.name, impl2.name)
  }

  @Test
  fun testLoadDisabledPlugin() {
    val descriptor = loadDescriptorInTest("disabled", setOf(PluginId.getId("com.intellij.disabled")))
    assertFalse(descriptor.isEnabled)
    assertEquals("This is a disabled plugin", descriptor.description)
    assertThat(descriptor.pluginDependencies.map { it.pluginId.idString }).containsExactly("com.intellij.modules.lang")
  }

  @Test
  fun testLoadPluginWithDisabledDependency() {
    val pluginDir = inMemoryFs.fs.getPath("/plugins")
    PluginBuilder().noDepends().id("foo").depends("bar").build(pluginDir.resolve("foo"))
    PluginBuilder().noDepends().id("bar").build(pluginDir.resolve("bar"))

    val pluginSet = loadAndInitDescriptors(pluginDir, PluginManagerCore.getBuildNumber(), setOf(PluginId.getId("bar"))).pluginSet
    assertThat(pluginSet.enabledPlugins).isEmpty()
  }

  @Test
  fun testLoadPluginWithDisabledTransitiveDependency() {
    val pluginDir = inMemoryFs.fs.getPath("/plugins")
    PluginBuilder()
      .noDepends()
      .id("org.jetbrains.plugins.gradle.maven")
      .implementationDetail()
      .depends("org.jetbrains.plugins.gradle")
      .build(pluginDir.resolve("intellij.gradle.java.maven"))
    PluginBuilder()
      .noDepends()
      .id("org.jetbrains.plugins.gradle")
      .depends("com.intellij.gradle")
      .implementationDetail()
      .build(pluginDir.resolve("intellij.gradle.java"))
    PluginBuilder()
      .noDepends()
      .id("com.intellij.gradle")
      .build(pluginDir.resolve("intellij.gradle"))

    val result = loadAndInitDescriptors(pluginDir, PluginManagerCore.getBuildNumber(), setOf(PluginId.getId("com.intellij.gradle"))).pluginSet
    assertThat(result.enabledPlugins).isEmpty()
  }
}

private fun writeDescriptor(id: String, pluginDir: Path, @Language("xml") data: String) {
  pluginDir.resolve("$id/META-INF/plugin.xml").write(data.trimIndent())
}

private val testDataPath: String
  get() = "${PlatformTestUtil.getPlatformTestDataPath()}plugins/pluginDescriptor"

private fun loadDescriptorInTest(dirName: String, disabledPlugins: Set<PluginId> = emptySet()): IdeaPluginDescriptorImpl {
  return loadDescriptorInTest(Path.of(testDataPath, dirName), disabledPlugins = disabledPlugins)
}

fun readDescriptorForTest(path: Path, isBundled: Boolean, input: ByteArray, id: PluginId? = null): IdeaPluginDescriptorImpl {
  val pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
  val dataLoader = object : DataLoader {
    override val pool: ZipFilePool?
      get() = null

    override fun load(path: String) = throw UnsupportedOperationException()

    override fun toString() = throw UnsupportedOperationException()
  }

  val raw = readModuleDescriptor(
    input = input,
    readContext = object : ReadModuleContext {
      override val interner = NoOpXmlInterner
      override val isMissingIncludeIgnored: Boolean
        get() = false
    },
    pathResolver = pathResolver,
    dataLoader = dataLoader,
    includeBase = null,
    readInto = null,
    locationSource = path.toString()
  )
  if (id != null) {
    raw.id = id.idString
  }
  val result = IdeaPluginDescriptorImpl(raw = raw, path = path, isBundled = isBundled, id = id, moduleName = null)
  result.readExternal(
    raw = raw,
    isSub = false,
    context = DescriptorListLoadingContext(disabledPlugins = Collections.emptySet()),
    pathResolver = pathResolver,
    dataLoader = dataLoader
  )
  return result
}

fun createFromDescriptor(path: Path,
                         isBundled: Boolean,
                         data: ByteArray,
                         context: DescriptorListLoadingContext,
                         pathResolver: PathResolver,
                         dataLoader: DataLoader): IdeaPluginDescriptorImpl {
  val raw = readModuleDescriptor(input = data,
                                 readContext = context,
                                 pathResolver = pathResolver,
                                 dataLoader = dataLoader,
                                 includeBase = null,
                                 readInto = null,
                                 locationSource = path.toString())
  val result = IdeaPluginDescriptorImpl(raw = raw, path = path, isBundled = isBundled, id = null, moduleName = null)
  result.readExternal(raw = raw,
                      pathResolver = pathResolver,
                      context = context,
                      isSub = false,
                      dataLoader = dataLoader)
  return result
}
