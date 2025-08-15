// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax", "ReplaceGetOrSet")
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.testFramework.loadDescriptorInTest
import com.intellij.platform.testFramework.plugins.*
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.util.io.directoryContent
import com.intellij.util.io.java.classFile
import com.intellij.util.io.write
import com.intellij.util.lang.UrlClassLoader
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.createParentDirectories
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PluginDescriptorTest {
  @TestDataPath("\$CONTENT_ROOT/testData/plugins/pluginDescriptor") @Suppress("unused")
  private class TestDataRef // for easy navigation

  init {
    Logger.setUnitTestMode() // due to warnInProduction use in IdeaPluginDescriptorImpl
  }

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
    assertThat(pluginDependencies.map { it.idString }).containsExactly("dep1", "dep2")
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

  @Test
  fun `multiple depends on the same plugin with both strict and optional`() {
    val descriptor = loadDescriptorFromTestDataDir("duplicateDepends-strict")
    assertThat(descriptor).isNotNull()
    assertThat(descriptor.dependencies.map { it.pluginId.idString }).isEqualTo(listOf("foo", "foo", "foo"))
    assertThat(descriptor.dependencies.map { it.isOptional }).isEqualTo(listOf(true, false, true))
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
    val descriptor = loadDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.projectContainerDescriptor.components[0].options).isEqualTo(Collections.singletonMap("workspace", "true"))
  }

  @Test
  fun `descriptor with a v2 content module with a slash in its name loads if module descriptor file has a dot instead of a slash`() {
    plugin("bar") {
      content {
        module("bar/module", loadingRule = ModuleLoadingRule.REQUIRED) { packagePrefix = "bar.module" }
      }
    }.buildDir(pluginDirPath, object : PluginPackagingConfig() {
      override val ContentModuleSpec.descriptorFilename: String get() = "bar.module.xml"
    })
    val descriptor = loadDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
      .isMarkedEnabled()
      .hasExactlyEnabledContentModules("bar/module")
  }

  @Test
  fun `descriptor with a v2 content module with a slash in its name does not load if module descriptor file is placed in a subdirectory`() {
    plugin("bar") {
      content {
        module("bar/module", loadingRule = ModuleLoadingRule.REQUIRED) { packagePrefix = "bar.module" }
      }
    }.buildDir(pluginDirPath, object : PluginPackagingConfig() {
      override val ContentModuleSpec.descriptorFilename: String get() = "bar/module.xml"
    })
    assertThatThrownBy {
      val descriptor = loadDescriptorInTest(pluginDirPath)
      assertThat(descriptor).isNotNull
        .isNotMarkedEnabled()
        .doesNotHaveEnabledContentModules()
    }.hasMessageContaining("Cannot resolve bar.module.xml")
  }

  @Test
  fun `descriptor with a v2 content module with multiple slashes in its name does not load`() {
    plugin("bar") {
      content {
        module("bar/module/sub", loadingRule = ModuleLoadingRule.REQUIRED) { packagePrefix = "bar.module.sub" }
      }
    }.buildDir(pluginDirPath, object : PluginPackagingConfig() {
      override val ContentModuleSpec.descriptorFilename: String get() = "bar.module.sub.xml"
    })
    assertThatThrownBy {
      val descriptor = loadDescriptorInTest(pluginDirPath)
      assertThat(descriptor).isNotNull
        .isNotMarkedEnabled()
        .doesNotHaveEnabledContentModules()
    }.hasMessageContaining("Cannot resolve bar/module.sub.xml") // note that only the last slash is substituted
  }

  @Test
  fun `descriptor with a v2 content module with multiple slashes in its name loads from a subdirectory`() { // FIXME
    plugin("bar") {
      content {
        module("bar/module/sub", loadingRule = ModuleLoadingRule.REQUIRED) { packagePrefix = "bar.module.sub" }
      }
    }.buildDir(pluginDirPath, object : PluginPackagingConfig() {
      override val ContentModuleSpec.descriptorFilename: String get() = "bar/module.sub.xml"
    })
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
    assertThat(descriptor.contentModules[0])
      .isMarkedEnabled()
      .hasExactlyApplicationServices("foo.module.service")
  }

  @Test
  fun `id, version, name are inherited in content modules`() {
    plugin("bar") {
      name = "Bar"
      version = "1.0.0"
      content { module("bar.sub") { } }
    }.buildDir(pluginDirPath, object : PluginPackagingConfig() {
      override val ContentModuleSpec.packageToMainJar: Boolean get() = true
    })
    val descriptor = loadDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.pluginId.idString).isEqualTo("bar")
    assertThat(descriptor.name).isEqualTo("Bar")
    assertThat(descriptor.version).isEqualTo("1.0.0")
    assertThat(descriptor.contentModules).hasSize(1)
    val subDesc = descriptor.contentModules[0]
    assertThat(subDesc.pluginId.idString).isEqualTo("bar")
    assertThat((subDesc as PluginDescriptor).name).isEqualTo("Bar")
    assertThat(subDesc.version).isEqualTo("1.0.0")
  }

  @Test
  fun `id, version, name can't overridden in submodules`() {
    plugin("bar") {
      name = "Bar"
      version = "1.0.0"
      content {
        module("bar.sub") {
          id = "bar2"
          name = "Bar Sub"
          version = "2.0.0"
        }
      }
    }.buildDir(pluginDirPath, object : PluginPackagingConfig() {
      override val ContentModuleSpec.packageToMainJar: Boolean get() = true
    })
    val (descriptor, errs) = runAndReturnWithLoggedErrors { loadDescriptorInTest(pluginDirPath) }
    Assertions.assertThat(errs.joinToString { it.message ?: "" }).isNotNull
      .contains("bar.sub", "element 'version'", "element 'name'", "element 'id'")
    assertThat(descriptor).isNotNull
    assertThat(descriptor.pluginId.idString).isEqualTo("bar")
    assertThat(descriptor.name).isEqualTo("Bar")
    assertThat(descriptor.version).isEqualTo("1.0.0")
    assertThat(descriptor.contentModules).hasSize(1)
    val subDesc = descriptor.contentModules[0]
    assertThat(subDesc.pluginId.idString).isEqualTo("bar")
    assertThat((subDesc as PluginDescriptor).name).isEqualTo("Bar")
    assertThat(subDesc.version).isEqualTo("1.0.0")
  }

  @Test
  fun `resource bundle is not inherited in content modules`() {
    plugin("bar") {
      resourceBundle = "resourceBundle"
      content {
        module("bar.opt", loadingRule = ModuleLoadingRule.OPTIONAL) {}
        module("bar.req", loadingRule = ModuleLoadingRule.REQUIRED) {}
        module("bar.emb", loadingRule = ModuleLoadingRule.EMBEDDED) {}
      }
    }.buildDir(pluginDirPath, object : PluginPackagingConfig() {
      override val ContentModuleSpec.packageToMainJar: Boolean get() = true
    })
    val descriptor = loadDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.pluginId.idString).isEqualTo("bar")
    assertThat(descriptor.resourceBundleBaseName).isEqualTo("resourceBundle")
    assertThat(descriptor.contentModules).hasSize(3)
    assertThat(descriptor.contentModules).allMatch { it.resourceBundleBaseName == null }
  }

  @Test
  fun `resource bundle can be set in content modules`() {
    plugin("bar") {
      resourceBundle = "resourceBundle"
      content {
        module("bar.opt", loadingRule = ModuleLoadingRule.OPTIONAL) { resourceBundle = "bar.opt" }
        module("bar.req", loadingRule = ModuleLoadingRule.REQUIRED) { resourceBundle = "bar.req" }
        module("bar.emb", loadingRule = ModuleLoadingRule.EMBEDDED) { resourceBundle = "bar.emb" }
      }
    }.buildDir(pluginDirPath, object : PluginPackagingConfig() {
      override val ContentModuleSpec.packageToMainJar: Boolean get() = true
    })
    val descriptor = loadDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
    assertThat(descriptor.pluginId.idString).isEqualTo("bar")
    assertThat(descriptor.resourceBundleBaseName).isEqualTo("resourceBundle")
    assertThat(descriptor.contentModules).hasSize(3)
    assertThat(descriptor.contentModules).allMatch { it.resourceBundleBaseName == it.moduleId.id }
  }

  @Test
  fun `core plugin has implicit host and product mode plugin aliases`() {
    plugin(PluginManagerCore.CORE_PLUGIN_ID) {}.buildDir(pluginDirPath)
    val descriptor = loadDescriptorInTest(pluginDirPath)
    assertThat(descriptor).isNotNull
    val hostIds = IdeaPluginOsRequirement.getHostOsModuleIds()
    if (hostIds.isEmpty()) {
      logger<PluginDescriptorTest>().warn("No host OS plugin aliases")
    }
    val productAliases = PluginMainDescriptor.productModeAliasesForCorePlugin()
    if (productAliases.isEmpty()) {
      logger<PluginDescriptorTest>().warn("No product mode plugin aliases")
    }
    assertThat(descriptor.pluginAliases)
      .containsAll(hostIds)
      .containsAll(productAliases)
  }

  @Test
  fun `content module's content modules are disregarded`() {
    plugin("bar") {
      content {
        module("bar.module", loadingRule = ModuleLoadingRule.REQUIRED) {
          packagePrefix = "bar.module"
          content {
            module("bar.module.inner", loadingRule = ModuleLoadingRule.REQUIRED) {
              packagePrefix = "bar.module.inner"
            }
          }
        }
      }
    }.buildDir(pluginDirPath)
    val (bar, err) = runAndReturnWithLoggedError { loadDescriptorInTest(pluginDirPath) }
    assertThat(err).hasMessageContainingAll("bar.module", "plugin 'bar'", "element 'content'")
    assertThat(bar).isNotNull
      .isMarkedEnabled()
      .hasExactlyEnabledContentModules("bar.module")
    val barModule = bar.contentModules[0]
    assertThat(barModule).isNotNull
      .isMarkedEnabled()
      .doesNotHaveEnabledContentModules()
    assertThat(barModule.contentModules).hasSize(0)
  }

  // todo this is rather about plugin set loading, probably needs to be moved out
  @Test
  fun `only one instance of a plugin is loaded if it's duplicated`() {
    val urls = arrayOf(
      Path.of(testDataPath, "duplicate1.jar").toUri().toURL(),
      Path.of(testDataPath, "duplicate2.jar").toUri().toURL()
    )
    val loader = URLClassLoader(urls, null)
    val pluginList = loadDescriptorsFromClassPathInTest(loader)
    val buildNumber = BuildNumber.fromString("2042.42")!!
    val initContext = PluginInitializationContext.buildForTest(
      essentialPlugins = emptySet(),
      disabledPlugins = emptySet(),
      expiredPlugins = emptySet(),
      brokenPluginVersions = emptyMap(),
      getProductBuildNumber = { buildNumber },
      requirePlatformAliasDependencyForLegacyPlugins = false,
      checkEssentialPlugins = false,
      explicitPluginSubsetToLoad = null,
      disablePluginLoadingCompletely = false,
      currentProductModeId = ProductMode.MONOLITH.id,
    )
    val result = PluginLoadingResult()
    result.initAndAddAll(
      descriptorLoadingResult = PluginDescriptorLoadingResult.build(listOf(pluginList)),
      initContext = initContext
    )
    assertThat(result.enabledPlugins).hasSize(1)
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
    assertThat(loadDescriptorsFromClassPathInTest(TestLoader("", "/spaces%20spaces/")).plugins).hasSize(1)
    assertThat(loadDescriptorsFromClassPathInTest(TestLoader("", "/spaces spaces/")).plugins).hasSize(1)
    assertThat(loadDescriptorsFromClassPathInTest(TestLoader("jar:", "/jar%20spaces.jar!/")).plugins).hasSize(1)
    assertThat(loadDescriptorsFromClassPathInTest(TestLoader("jar:", "/jar spaces.jar!/")).plugins).hasSize(1)
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
    val impl1 = loadDescriptorInTest(rootPath)

    tempFile.write("""
<idea-plugin>
  <id>ID</id>
  <name>B</name>
</idea-plugin>""")
    val impl2 = loadDescriptorInTest(rootPath)

    assertEquals(impl1, impl2)
    assertEquals(impl1.hashCode(), impl2.hashCode())
    assertNotEquals(impl1.name, impl2.name)
  }

  companion object {
    private val testDataPath: String
      get() = "${PlatformTestUtil.getPlatformTestDataPath()}plugins/pluginDescriptor"

    private fun loadDescriptorFromTestDataDir(
      dirName: String,
    ): IdeaPluginDescriptorImpl {
      return loadDescriptorInTest(
        fileOrDir = Path.of(testDataPath, dirName),
      )
    }

    private fun Path.writeXml(@Language("XML") text: String) {
      createParentDirectories().write(text.encodeToByteArray())
    }
  }
}
