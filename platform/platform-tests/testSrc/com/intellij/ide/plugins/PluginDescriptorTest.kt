// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax", "ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.directoryContent
import com.intellij.util.io.java.classFile
import com.intellij.util.io.write
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.xml.dom.NoOpXmlInterner
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
import java.util.function.Function
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PluginDescriptorTest {
  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  private val rootPath get() = inMemoryFs.fs.getPath("/")
  private val pluginDirPath get() = rootPath.resolve("plugin")

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
    val descriptors = PluginSetTestBuilder(path = Paths.get("/Applications/Idea.app/Contents/plugins"))
      .build()
      .allPlugins
    assertThat(descriptors).isNotEmpty()
    assertThat(descriptors.find { it.pluginId.idString == "com.intellij.java" }).isNotNull
  }

  @Test
  fun testProductionProductLib() {
    IoTestUtil.assumeMacOS()
    assumeNotUnderTeamcity()
    val dir = Path.of("/Applications/Idea.app/Contents/lib")
    assumeTrue(Files.exists(dir))

    val urls = Files.newDirectoryStream(dir).use { stream ->
      stream.map { it.toUri().toURL() }
    }
    val descriptors = testLoadDescriptorsFromClassPath(URLClassLoader(urls.toTypedArray(), null))
    // core and com.intellij.workspace
    assertThat(descriptors).hasSize(1)
  }

  @Test
  fun testProduction2() {
    IoTestUtil.assumeMacOS()

    assumeNotUnderTeamcity()
    val descriptors = PluginSetTestBuilder(path = Paths.get("/Volumes/data/plugins"))
      .build()
      .allPlugins
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
        classFile("Empty") {}
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
    assertThat(descriptor.isLicenseOptional).isTrue()
  }

  @Test
  fun `use newer plugin`() {
    writeDescriptor("foo_1-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>1.0</version>
      </idea-plugin>""")
    writeDescriptor("foo_2-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>2.0</version>
      </idea-plugin>""")

    val pluginSet = PluginSetTestBuilder(pluginDirPath).build()
    val plugins = pluginSet.enabledPlugins
    assertThat(plugins).hasSize(1)
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("2.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(pluginSet.allPlugins.toList()).map(Function { it.pluginId }).containsOnly(foo.pluginId)
    assertThat(pluginSet.findEnabledPlugin(foo.pluginId)).isSameAs(foo)
  }

  @Test
  fun `use newer plugin if disabled`() {
    writeDescriptor("foo_3-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>1.0</version>
      </idea-plugin>""")
    writeDescriptor("foo_2-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>2.0</version>
      </idea-plugin>""")

    val result = PluginSetTestBuilder(pluginDirPath)
      .withDisabledPlugins("foo")
      .withLoadingContext()
      .withLoadingResult()
      .loadingResult

    val incompletePlugins = result.getIncompleteIdMap().values
    assertThat(incompletePlugins).hasSize(1)
    val foo = incompletePlugins.single()
    assertThat(foo.version).isEqualTo("2.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")
  }

  @Test
  fun `prefer bundled if custom is incompatible`() {
    // names are important - will be loaded in alphabetical order
    writeDescriptor("foo_1-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>2.0</version>
        <idea-version until-build="2"/>
      </idea-plugin>""")
    writeDescriptor("foo_2-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>2.0</version>
        <idea-version until-build="4"/>
      </idea-plugin>""")

    val result = PluginSetTestBuilder(pluginDirPath)
      .withProductBuildNumber(BuildNumber.fromString("4.0")!!)
      .withLoadingContext()
      .withLoadingResult()
      .loadingResult

    assertThat(result.hasPluginErrors).isFalse()
    val plugins = result.enabledPlugins.toList()
    assertThat(plugins).hasSize(1)
    assertThat(result.duplicateModuleMap).isNull()
    assertThat(result.getIncompleteIdMap()).isEmpty()
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("2.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(result.getIdMap()).containsOnlyKeys(foo.pluginId)
    assertThat(result.getIdMap().get(foo.pluginId)).isSameAs(foo)
  }

  @Test
  fun `select compatible plugin if both versions provided`() {
    writeDescriptor("foo_1-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>1.0</version>
        <idea-version since-build="1.*" until-build="2.*"/>
      </idea-plugin>""")
    writeDescriptor("foo_2-0", """
      <idea-plugin>
        <id>foo</id>
        <vendor>JetBrains</vendor>
        <version>2.0</version>
        <idea-version since-build="2.0" until-build="4.*"/>
      </idea-plugin>""")

    val pluginSet = PluginSetTestBuilder(pluginDirPath)
      .withProductBuildNumber(BuildNumber.fromString("3.12")!!)
      .build()
    val plugins = pluginSet.enabledPlugins
    assertThat(plugins).hasSize(1)
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("2.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(pluginSet.allPlugins.toList()).map(Function { it.pluginId }).containsOnly(foo.pluginId)
    assertThat(pluginSet.findEnabledPlugin(foo.pluginId)).isSameAs(foo)
  }

  @Test
  fun `use first plugin if both versions the same`() {
    PluginBuilder().noDepends().id("foo").version("1.0").build(pluginDirPath.resolve("foo_1-0"))
    PluginBuilder().noDepends().id("foo").version("1.0").build(pluginDirPath.resolve("foo_another"))

    val pluginSet = PluginSetTestBuilder(pluginDirPath).build()
    val plugins = pluginSet.enabledPlugins
    assertThat(plugins).hasSize(1)
    val foo = plugins[0]
    assertThat(foo.version).isEqualTo("1.0")
    assertThat(foo.pluginId.idString).isEqualTo("foo")

    assertThat(pluginSet.allPlugins.toList()).map(Function { it.pluginId }).containsOnly(foo.pluginId)
    assertThat(pluginSet.findEnabledPlugin(foo.pluginId)).isSameAs(foo)
  }

  @Test
  fun classLoader() {
    PluginBuilder().noDepends().id("foo").depends("bar").build(pluginDirPath.resolve("foo"))
    PluginBuilder().noDepends().id("bar").build(pluginDirPath.resolve("bar"))
    checkClassLoader()
  }

  @Test
  fun `classLoader - optional dependency`() {
    writeDescriptor("foo", """
    <idea-plugin>
      <id>foo</id>
      <depends optional="true" config-file="stream-debugger.xml">bar</depends>
      <vendor>JetBrains</vendor>
    </idea-plugin>
    """)

    pluginDirPath.resolve("foo/META-INF/stream-debugger.xml").write("""
     <idea-plugin>
      <actions>
      </actions>
    </idea-plugin>
    """)

    writeDescriptor("bar", """
    <idea-plugin>
      <id>bar</id>
      <vendor>JetBrains</vendor>
    </idea-plugin>
    """)

    checkClassLoader()
  }

  private fun checkClassLoader() {
    val list = PluginSetTestBuilder(pluginDirPath)
      .build()
      .enabledPlugins
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
    val pluginFile = pluginDirPath.resolve(PluginManagerCore.PLUGIN_XML_PATH)
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

  @Test
  fun testLoadDisabledPlugin() {
    val descriptor = loadDescriptorInTest(
      dirName = "disabled",
      disabledPlugins = setOf("com.intellij.disabled"),
    )
    assertFalse(descriptor.isEnabled)
    assertEquals("This is a disabled plugin", descriptor.description)
    assertThat(descriptor.pluginDependencies.map { it.pluginId.idString }).containsExactly("com.intellij.modules.lang")
  }

  @Test
  fun testLoadPluginWithDisabledDependency() {
    PluginBuilder().noDepends().id("foo").depends("bar").build(pluginDirPath.resolve("foo"))
    PluginBuilder().noDepends().id("bar").build(pluginDirPath.resolve("bar"))

    val pluginSet = PluginSetTestBuilder(pluginDirPath)
      .withDisabledPlugins("bar")
      .build()
    assertThat(pluginSet.enabledPlugins).isEmpty()
  }

  @Test
  fun testLoadPluginWithDisabledTransitiveDependency() {
    PluginBuilder()
      .noDepends()
      .id("org.jetbrains.plugins.gradle.maven")
      .implementationDetail()
      .depends("org.jetbrains.plugins.gradle")
      .build(pluginDirPath.resolve("intellij.gradle.java.maven"))
    PluginBuilder()
      .noDepends()
      .id("org.jetbrains.plugins.gradle")
      .depends("com.intellij.gradle")
      .implementationDetail()
      .build(pluginDirPath.resolve("intellij.gradle.java"))
    PluginBuilder()
      .noDepends()
      .id("com.intellij.gradle")
      .build(pluginDirPath.resolve("intellij.gradle"))

    val result = PluginSetTestBuilder(pluginDirPath)
      .withDisabledPlugins("com.intellij.gradle")
      .build()
    assertThat(result.enabledPlugins).isEmpty()
  }
  
  @Test
  fun `disable plugin if dependency of required content module is not available`() {
    PluginBuilder()
      .noDepends()
      .id("sample.plugin")
      .module("required.module", PluginBuilder().packagePrefix("required").dependency("unknown"), loadingRule = ModuleLoadingRule.REQUIRED)
      .build(pluginDirPath.resolve("sample-plugin"))
    val result = PluginSetTestBuilder(pluginDirPath).build()
    assertThat(result.enabledPlugins).isEmpty()
    val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
    assertThat(errors).isNotEmpty
    assertThat(errors.first().toString()).contains("sample.plugin", "requires plugin", "unknown")
  }
  
  @Test
  fun `required content module uses same classloader as the main module`() {
    val samplePluginDir = pluginDirPath.resolve("sample-plugin")
    PluginBuilder()
      .noDepends()
      .id("sample.plugin")
      .module("required.module", PluginBuilder().packagePrefix("required"), loadingRule = ModuleLoadingRule.REQUIRED, separateJar = true)
      .module("optional.module", PluginBuilder().packagePrefix("optional"))
      .build(samplePluginDir)
    val result = PluginSetTestBuilder(pluginDirPath).build()
    assertThat(result.enabledPlugins).hasSize(1)
    val mainClassLoader = result.enabledPlugins.single().pluginClassLoader
    val requiredModuleClassLoader = result.findEnabledModule("required.module")!!.pluginClassLoader
    assertThat(requiredModuleClassLoader).isSameAs(mainClassLoader)
    assertThat((mainClassLoader as PluginClassLoader).files).containsExactly(
      samplePluginDir.resolve("lib/sample.plugin.jar"),
      samplePluginDir.resolve("lib/modules/required.module.jar"),
    )
    val optionalModuleClassLoader = result.findEnabledModule("optional.module")!!.pluginClassLoader
    assertThat(optionalModuleClassLoader).isNotSameAs(mainClassLoader)
  }
  
  @Test
  fun `dependencies of required content module are added to the main class loader`() {
    PluginBuilder()
      .noDepends()
      .id("dep")
      .build(pluginDirPath.resolve("dep"))
    PluginBuilder()
      .noDepends()
      .id("sample.plugin")
      .module("required.module", PluginBuilder().packagePrefix("required").pluginDependency("dep"), loadingRule = ModuleLoadingRule.REQUIRED)
      .build(pluginDirPath.resolve("sample-plugin"))
    val result = PluginSetTestBuilder(pluginDirPath).build()
    assertThat(result.enabledPlugins).hasSize(2)
    val depPluginDescriptor = result.findEnabledPlugin(PluginId.getId("dep"))!!
    val mainClassLoader = result.findEnabledPlugin(PluginId.getId("sample.plugin"))!!.pluginClassLoader
    assertThat((mainClassLoader as PluginClassLoader)._getParents()).contains(depPluginDescriptor)
  }
  
  @Test
  fun `content module in separate JAR`() {
    val pluginDir = pluginDirPath.resolve("sample-plugin")
    PluginBuilder()
      .noDepends()
      .id("sample.plugin")
      .module("dep", PluginBuilder(), separateJar = true)
      .build(pluginDir)
    val result = PluginSetTestBuilder(pluginDirPath).build()
    assertThat(result.enabledPlugins).hasSize(1)
    assertThat(result.getEnabledModules()).hasSize(2)
    val depModuleDescriptor = result.findEnabledModule("dep")!!
    assertThat(depModuleDescriptor.jarFiles).containsExactly(pluginDir.resolve("lib/modules/dep.jar"))
  }

  @Test
  fun testExpiredPluginNotLoaded() {
    PluginBuilder()
      .noDepends()
      .id("foo")
      .build(pluginDirPath.resolve("foo"))

    PluginBuilder()
      .noDepends()
      .id("bar")
      .build(pluginDirPath.resolve("bar"))

    val enabledPlugins = PluginSetTestBuilder(pluginDirPath)
      .withExpiredPlugins("foo")
      .build()
      .enabledPlugins

    assertThat(enabledPlugins).hasSize(1)
    assertThat(enabledPlugins.single().pluginId.idString).isEqualTo("bar")
  }

  private fun writeDescriptor(id: String, @Language("xml") data: String) {
    pluginDirPath.resolve(id)
      .resolve(PluginManagerCore.PLUGIN_XML_PATH)
      .write(data.trimIndent())
  }
}

private val testDataPath: String
  get() = "${PlatformTestUtil.getPlatformTestDataPath()}plugins/pluginDescriptor"

private fun loadDescriptorInTest(
  dirName: String,
  disabledPlugins: Set<String> = emptySet(),
): IdeaPluginDescriptorImpl {
  return loadDescriptorInTest(
    dir = Path.of(testDataPath, dirName),
    disabledPlugins = disabledPlugins,
  )
}

fun readDescriptorForTest(path: Path, isBundled: Boolean, input: ByteArray, id: PluginId? = null): IdeaPluginDescriptorImpl {
  val pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
  val dataLoader = object : DataLoader {
    override fun load(path: String, pluginDescriptorSourceOnly: Boolean) = throw UnsupportedOperationException()

    override fun toString() = throw UnsupportedOperationException()
  }

  val raw = readModuleDescriptor(
    input = input,
    readContext = object : ReadModuleContext {
      override val interner = NoOpXmlInterner
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
  initMainDescriptorByRaw(
    descriptor = result,
    raw = raw,
    context = DescriptorListLoadingContext(customDisabledPlugins = emptySet()),
    pathResolver = pathResolver,
    dataLoader = dataLoader,
    pluginDir = path,
    pool = ZipFilePoolImpl(),
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
  initMainDescriptorByRaw(descriptor = result, raw = raw, pathResolver = pathResolver, context = context, dataLoader = dataLoader, pluginDir = path, pool = ZipFilePoolImpl())
  return result
}
