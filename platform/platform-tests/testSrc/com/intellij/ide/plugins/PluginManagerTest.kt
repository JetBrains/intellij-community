// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.DisabledPluginsState.Companion.saveDisabledPluginsAndInvalidate
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.plugins.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.plugins.parser.impl.ReadModuleContext
import com.intellij.platform.plugins.parser.impl.XIncludeLoader.LoadedXIncludeReference
import com.intellij.platform.plugins.parser.impl.consume
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.TriConsumer
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import org.assertj.core.api.Assertions
import org.easymock.EasyMock
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Map
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamWriter

class PluginManagerTest {
  @Rule
  @JvmField
  val tempDir: TempDirectory = TempDirectory()

  @Test
  fun compatibilityBranchBased() {
    assertCompatible("145.2", null, null)
    assertCompatible("145.2.2", null, null)

    assertCompatible("145.2", "145", null)
    assertCompatible("145.2", null, "146")
    assertCompatible("145.2.2", "145", null)
    assertCompatible("145.2.2", null, "146")
    assertIncompatible("145.2", null, "145")

    assertIncompatible("145.2", "146", null)
    assertIncompatible("145.2", null, "144")
    assertIncompatible("145.2.2", "146", null)
    assertIncompatible("145.2.2", null, "144")

    assertCompatible("145.2", "145.2", null)
    assertCompatible("145.2", null, "145.2")
    assertCompatible("145.2.2", "145.2", null)
    assertIncompatible("145.2.2", null, "145.2")

    assertIncompatible("145.2", "145.3", null)
    assertIncompatible("145.2", null, "145.1")
    assertIncompatible("145.2.2", "145.3", null)
    assertIncompatible("145.2.2", null, "145.1")

    assertCompatible("145.2", "140.3", null)
    assertCompatible("145.2", null, "146.1")
    assertCompatible("145.2.2", "140.3", null)
    assertCompatible("145.2.2", null, "146.1")

    assertIncompatible("145.2", "145.2.0", null)
    assertIncompatible("145.2", "145.2.1", null)
    assertCompatible("145.2", null, "145.2.3")
    assertCompatible("145.2.2", "145.2.0", null)
    assertCompatible("145.2.2", null, "145.2.3")
  }

  @Test
  fun ignoredCompatibility() {
    val checkCompatibility = TriConsumer { ideVersion: String?, sinceBuild: String?, untilBuild: String? ->
      val ignoreCompatibility = PluginManagerCore.isIgnoreCompatibility
      try {
        assertIncompatible(ideVersion, sinceBuild, untilBuild)

        PluginManagerCore.isIgnoreCompatibility = true
        assertCompatible(ideVersion, sinceBuild, untilBuild)
      }
      finally {
        PluginManagerCore.isIgnoreCompatibility = ignoreCompatibility
      }
    }

    checkCompatibility.accept("42", "43", null)
    checkCompatibility.accept("43", null, "42")
  }

  @Test
  fun compatibilityBranchBasedStar() {
    assertCompatible("145.10", "144.*", null)
    assertIncompatible("145.10", "145.*", null)
    assertIncompatible("145.10", "146.*", null)
    assertIncompatible("145.10", null, "144.*")
    assertCompatible("145.10", null, "145.*")
    assertCompatible("145.10", null, "146.*")

    assertCompatible("145.10.1", null, "145.*")
    assertCompatible("145.10.1", "145.10", "145.10.*")

    assertCompatible("145.SNAPSHOT", null, "145.*")
  }

  @Test
  fun compatibilitySnapshots() {
    assertIncompatible("145.SNAPSHOT", "146", null)
    assertIncompatible("145.2.SNAPSHOT", "145.3", null)

    assertCompatible("145.SNAPSHOT", "145.2", null)

    assertCompatible("145.SNAPSHOT", null, "146")
    assertIncompatible("145.SNAPSHOT", null, "145")
    assertIncompatible("145.SNAPSHOT", null, "144")
    assertIncompatible("145.2.SNAPSHOT", null, "145")
    assertIncompatible("145.2.SNAPSHOT", null, "144")
  }

  @Test
  fun compatibilityPlatform() {
    Assert.assertEquals(SystemInfo.isWindows, checkCompatibility("com.intellij.modules.os.windows"))
    Assert.assertEquals(SystemInfo.isMac, checkCompatibility("com.intellij.modules.os.mac"))
    Assert.assertEquals(SystemInfo.isLinux, checkCompatibility("com.intellij.modules.os.linux"))
    Assert.assertEquals(SystemInfo.isFreeBSD, checkCompatibility("com.intellij.modules.os.freebsd"))
    Assert.assertEquals(SystemInfo.isSolaris, checkCompatibility("com.intellij.modules.os.solaris"))
    Assert.assertEquals(SystemInfo.isUnix, checkCompatibility("com.intellij.modules.os.unix"))
    Assert.assertEquals(SystemInfo.isUnix && !SystemInfo.isMac, checkCompatibility("com.intellij.modules.os.xwindow"))
  }

  @Test
  fun convertExplicitBigNumberInUntilBuildToStar() {
    assertConvertsTo(null, null)
    assertConvertsTo("145", "145")
    assertConvertsTo("145.999", "145.999")
    assertConvertsTo("145.9999", "145.*")
    assertConvertsTo("145.99999", "145.*")
    assertConvertsTo("145.9999.1", "145.9999.1")
    assertConvertsTo("145.1000", "145.1000")
    assertConvertsTo("145.10000", "145.*")
    assertConvertsTo("145.100000", "145.*")
  }

  @Test
  @Throws(Exception::class)
  fun testSimplePluginSort() {
    doPluginSortTest("simplePluginSort", false)
  }

  /*
   Actual result:
   HTTP Client (main)
   Endpoints (main)
   HTTP Client (intellij.restClient.microservicesUI, depends on Endpoints)

   Expected:
   Endpoints (main)
   HTTP Client (main)
   HTTP Client (intellij.restClient.microservicesUI, depends on Endpoints)

   But graph is correct - HTTP Client (main) it is node that doesn't depend on Endpoints (main),
   so no reason for DFSTBuilder to put it after.
   See CachingSemiGraph.getSortedPlugins for a solution.
  */
  @Test
  @Throws(Exception::class)
  fun moduleSort() {
    doPluginSortTest("moduleSort", true)
  }

  @Test
  @Throws(Exception::class)
  fun testUltimatePlugins() {
    doPluginSortTest("ultimatePlugins", true)
  }

  @Test
  fun testModulePluginIdContract() {
    val pluginsPath = Path.of(PlatformTestUtil.getPlatformTestDataPath(), "plugins", "withModules")
    val descriptorBundled = loadAndInitDescriptorInTest(pluginsPath, true)
    val pluginSet = PluginSetBuilder(mutableSetOf(descriptorBundled)).createPluginSetWithEnabledModulesMap()

    val moduleId = PluginId.getId("foo.bar")
    val corePlugin = PluginId.getId("my.plugin")
    Assertions.assertThat(pluginSet.findEnabledPlugin(moduleId)!!.getPluginId()).isEqualTo(corePlugin)
  }

  @Test
  fun testIdentifyPreInstalledPlugins() {
    val pluginsPath = Path.of(PlatformTestUtil.getPlatformTestDataPath(), "plugins", "updatedBundled")
    val bundled = loadAndInitDescriptorInTest(pluginsPath.resolve("bundled"), true)
    val updated = loadAndInitDescriptorInTest(pluginsPath.resolve("updated"))
    val expectedPluginId = updated.getPluginId()
    Assert.assertEquals(expectedPluginId, bundled.getPluginId())

    assertPluginPreInstalled(expectedPluginId, bundled, updated)
    assertPluginPreInstalled(expectedPluginId, updated, bundled)
  }

  @Test
  @Throws(IOException::class)
  fun testSymlinkInConfigPath() {
    IoTestUtil.assumeSymLinkCreationIsSupported()

    val configPath = tempDir.root.toPath().resolve("config-link")
    val target = tempDir.newDirectory("config-target").toPath()
    Files.createSymbolicLink(configPath, target)
    saveDisabledPluginsAndInvalidate(configPath, mutableListOf("a"))
    com.intellij.testFramework.assertions.Assertions.assertThat(configPath.resolve(
      DisabledPluginsState.DISABLED_PLUGINS_FILENAME)).hasContent("a" + System.lineSeparator())
  }

  companion object {
    private val testDataPath: String
      get() = PlatformTestUtil.getPlatformTestDataPath() + "plugins/sort"

    private fun assertPluginPreInstalled(expectedPluginId: PluginId?, vararg descriptors: IdeaPluginDescriptorImpl) {
      val loadingResult = createPluginLoadingResult()
      loadingResult.initAndAddAll(descriptors.toList(), isPluginDisabled = { false }, isPluginBroken = { _, _ -> false }) // TODO refactor the test
      Assert.assertTrue("Plugin should be pre installed", loadingResult.shadowedBundledIds.contains(expectedPluginId))
    }

    @Throws(IOException::class, XMLStreamException::class)
    private fun doPluginSortTest(testDataName: String?, isBundled: Boolean) {
      PluginManagerCore.getAndClearPluginLoadingErrors()
      val loadPluginResult: PluginManagerState = loadAndInitializeDescriptors("$testDataName.xml", isBundled)
      val text = StringBuilder()
      for (descriptor in loadPluginResult.pluginSet.getEnabledModules()) {
        text.append(if (descriptor.isEnabled()) "+ " else "  ").append(descriptor.getPluginId().idString)
        if (descriptor.moduleName != null) {
          text.append(" | ").append(descriptor.moduleName)
        }
        text.append('\n')
      }
      text.append("\n\n")
      for (html in PluginManagerCore.getAndClearPluginLoadingErrors()) {
        text.append(html.get().toString().replace("<br/>", "\n").replace("&#39;", "")).append('\n')
      }
      UsefulTestCase.assertSameLinesWithFile(File(testDataPath, "$testDataName.txt").path, text.toString())
    }

    private fun assertConvertsTo(untilBuild: String?, result: String?) {
      Assert.assertEquals(result, PluginManager.convertExplicitBigNumberInUntilBuildToStar(untilBuild))
    }

    private fun assertIncompatible(ideVersion: String?, sinceBuild: String?, untilBuild: String?) {
      Assert.assertNotNull(checkCompatibility(ideVersion, sinceBuild, untilBuild))
    }

    private fun checkCompatibility(ideVersion: String?, sinceBuild: String?, untilBuild: String?): PluginLoadingError? {
      val mock = EasyMock.niceMock<IdeaPluginDescriptor>(IdeaPluginDescriptor::class.java)
      EasyMock.expect<String?>(mock.getSinceBuild()).andReturn(sinceBuild).anyTimes()
      EasyMock.expect<String?>(mock.getUntilBuild()).andReturn(untilBuild).anyTimes()
      EasyMock.expect<MutableList<IdeaPluginDependency?>?>(mock.getDependencies()).andReturn(
        mutableListOf()).anyTimes()
      EasyMock.replay(mock)

      return PluginManagerCore.checkBuildNumberCompatibility(mock, BuildNumber.fromString(ideVersion)!!)
    }

    private fun checkCompatibility(platformId: String): Boolean {
      val platformDependencyMock = EasyMock.niceMock<IdeaPluginDependency>(IdeaPluginDependency::class.java)
      EasyMock.expect(platformDependencyMock.pluginId).andReturn(PluginId.getId(platformId))
      EasyMock.replay(platformDependencyMock)

      val mock = EasyMock.niceMock<IdeaPluginDescriptor>(IdeaPluginDescriptor::class.java)
      EasyMock.expect<String?>(mock.getSinceBuild()).andReturn(null).anyTimes()
      EasyMock.expect<String?>(mock.getUntilBuild()).andReturn(null).anyTimes()
      EasyMock.expect<MutableList<IdeaPluginDependency?>?>(mock.getDependencies()).andReturn(
        mutableListOf(platformDependencyMock)).anyTimes()
      EasyMock.replay(mock)

      return PluginManagerCore.checkBuildNumberCompatibility(mock, BuildNumber.fromString("145")!!) == null
    }

    private fun assertCompatible(ideVersion: String?, sinceBuild: String?, untilBuild: String?) {
      Assert.assertNull(checkCompatibility(ideVersion, sinceBuild, untilBuild))
    }

    @Throws(IOException::class, XMLStreamException::class)
    private fun loadAndInitializeDescriptors(testDataName: String, isBundled: Boolean): PluginManagerState {
      val file = Path.of(testDataPath, testDataName)
      val buildNumber = BuildNumber.fromString("2042.42")
      val parentContext = DescriptorListLoadingContext(mutableSetOf<PluginId>(), mutableSetOf<PluginId>(),
                                                       Map.of<PluginId, MutableSet<String?>>(), mutableListOf<PluginId>(),
                                                       { buildNumber!! }, false, false, false)

      val root = readXmlAsModel(Files.newInputStream(file))
      val autoGenerateModuleDescriptor = Ref<Boolean>(false)
      val moduleMap = HashMap<String?, XmlElement?>()
      val pathResolver: PathResolver? = object : PathResolver {
        override val isFlat: Boolean
          get() = false

        override fun loadXIncludeReference(dataLoader: DataLoader, path: String): LoadedXIncludeReference? {
          throw UnsupportedOperationException()
        }

        override fun resolvePath(
          readContext: ReadModuleContext,
          dataLoader: DataLoader,
          relativePath: String
        ): PluginDescriptorBuilder {
          for (child in root.children) {
            if (child.name == "config-file-idea-plugin") {
              val url = child.getAttributeValue("url")!!
              if (url.endsWith("/$relativePath")) {
                try {
                  val reader = PluginDescriptorFromXmlStreamConsumer(readContext, this.toXIncludeLoader(dataLoader))
                  reader.consume(elementAsBytes(child), null)
                  return reader.getBuilder()
                }
                catch (e: XMLStreamException) {
                  throw RuntimeException(e)
                }
              }
            }
          }
          throw AssertionError("Unexpected: $relativePath")
        }

        override fun resolveModuleFile(
          readContext: ReadModuleContext,
          dataLoader: DataLoader,
          path: String
        ): PluginDescriptorBuilder {
          if (autoGenerateModuleDescriptor.get() && path.startsWith("intellij.")) {
            val element = moduleMap.get(path)
            if (element != null) {
              try {
                return readModuleDescriptorForTest(elementAsBytes(element))
              }
              catch (e: XMLStreamException) {
                throw RuntimeException(e)
              }
            }
            // auto-generate empty descriptor
            return readModuleDescriptorForTest(("<idea-plugin package=\"$path\"></idea-plugin>").toByteArray(
              StandardCharsets.UTF_8))
          }
          return resolvePath(readContext, dataLoader, path)
        }
      }

      for (element in root.children) {
        val moduleFile = element.attributes["moduleFile"]
        if (moduleFile != null) {
          moduleMap[moduleFile] = element
        }
      }

      val list = ArrayList<IdeaPluginDescriptorImpl>()
      for (element in root.children) {
        if (element.name != "idea-plugin") {
          continue
        }

        val url = element.getAttributeValue("url")
        val pluginPath: Path?
        if (url == null) {
          val id = element.getChild("id")
          if (id == null) {
            assert(element.attributes.containsKey("moduleFile"))
            continue
          }

          pluginPath = Path.of(id.content!!.replace('.', '_') + ".xml")
          autoGenerateModuleDescriptor.set(true)
        }
        else {
          pluginPath = Path.of(Strings.trimStart(url, "file://"))
        }
        val descriptor = readAndInitDescriptorFromBytesForTest(
          pluginPath, isBundled, elementAsBytes(element), parentContext, pathResolver!!, LocalFsDataLoader(pluginPath))
        list.add(descriptor)
        descriptor.jarFiles = mutableListOf<Path>()
      }
      parentContext.close()
      val result = PluginLoadingResult(false)
      result.initAndAddAll(list, isPluginDisabled = parentContext::isPluginDisabled, isPluginBroken = parentContext::isPluginBroken)
      return PluginManagerCore.initializePlugins(parentContext, result, PluginManagerTest::class.java.getClassLoader(), false, null)
    }

    @Throws(XMLStreamException::class)
    private fun elementAsBytes(child: XmlElement): ByteArray {
      val byteOut = ByteArrayOutputStream()
      writeXmlElement(child, XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(byteOut, "utf-8"))
      return byteOut.toByteArray()
    }

    @Throws(XMLStreamException::class)
    private fun writeXmlElement(element: XmlElement, writer: XMLStreamWriter) {
      writer.writeStartElement(element.name)
      for (entry in element.attributes.entries) {
        writer.writeAttribute(entry.key, entry.value)
      }
      if (element.content != null) {
        writer.writeCharacters(element.content)
      }
      for (child in element.children) {
        writeXmlElement(child, writer)
      }
      writer.writeEndElement()
    }
  }
}
