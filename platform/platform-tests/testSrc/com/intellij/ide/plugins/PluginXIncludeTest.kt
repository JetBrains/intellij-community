// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.platform.plugins.testFramework.PluginSetTestBuilder
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.util.io.createParentDirectories
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.writeText

@TestApplication
internal class PluginXIncludeTest {
  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  private val rootPath get() = inMemoryFs.fs.getPath("/")
  private val pluginsPath get() = inMemoryFs.fs.getPath("/wd/plugins")
  private val pluginDirPath get() = pluginsPath.resolve("plugin")

  @Test
  fun `filename relative path is resolved from META-INF`() {
    Spec(
      includes("a.xml"),
      mapOf("META-INF/a.xml" to includes())
    ).buildIn(pluginDirPath)
    assertLoads()
  }

  @Test
  fun `unexisting filename relative path fails to load`() {
    Spec(
      includes("a.xml"),
      mapOf()
    ).buildIn(pluginDirPath)
    assertDoesNotLoad()
  }

  @Test
  fun `filename relative path is not resolved from resource root`() {
    Spec(
      includes("a.xml"),
      mapOf("a.xml" to includes())
    ).buildIn(pluginDirPath)
    assertDoesNotLoad()
  }

  @Test
  fun `subdir relative path is resolved from META-INF`() {
    Spec(
      includes("dir/a.xml"),
      mapOf("META-INF/dir/a.xml" to includes())
    ).buildIn(pluginDirPath)
    assertLoads()
  }

  @Test
  fun `subdir relative path is not resolved from resource root`() {
    Spec(
      includes("dir/a.xml"),
      mapOf("dir/a.xml" to includes())
    ).buildIn(pluginDirPath)
    assertDoesNotLoad()
  }

  @Test
  fun `dot-dot from META-INF works`() {
    Spec(
      includes("../a.xml"),
      mapOf("a.xml" to includes())
    ).buildIn(pluginDirPath)
    assertLoads()
  }

  @Test
  fun `dot-dot from META-INF subdir does not work`() {
    Spec(
      includes("dir/a.xml"),
      mapOf(
        "META-INF/dir/a.xml" to includes("../b.xml"),
        "META-INF/b.xml" to includes(),
      )
    ).buildIn(pluginDirPath)
    assertDoesNotLoad()
  }

  @Test
  fun `dot-dot from META-INF subdir works as if base dir is still META-INF`() {
    Spec(
      includes("dir/a.xml"),
      mapOf(
        "META-INF/dir/a.xml" to includes("../b.xml"),
        "b.xml" to includes(),
      )
    ).buildIn(pluginDirPath)
    assertLoads()
  }

  @Test
  fun `include by relative path is not resolved from from resource root`() {
    Spec(
      includes("META-INF/a.xml"),
      mapOf("META-INF/a.xml" to includes())
    ).buildIn(pluginDirPath)
    assertDoesNotLoad()
  }

  @Test
  fun `transitive include changes the base dir in an unconceivable way if relative path is used`() {
    Spec(
      includes("dir/a.xml"),
      mapOf(
        "META-INF/dir/a.xml" to includes("b.xml"),
        "dir/b.xml" to includes(),
      )
    ).buildIn(pluginDirPath)
    assertLoads()
  }

  @Test
  fun `transitive include changes the base dir in an unconceivable way if relative path is used - intellij-prefixed`() {
    Spec(
      includes("dir/a.xml"),
      mapOf(
        "META-INF/dir/a.xml" to includes("intellij.b.xml"),
        "intellij.b.xml" to includes(),
      )
    ).buildIn(pluginDirPath)
    assertLoads()
  }

  @Test
  fun `transitive include changes the base dir in an unconceivable way if relative path is used - kotlin-prefixed`() {
    Spec(
      includes("dir/a.xml"),
      mapOf(
        "META-INF/dir/a.xml" to includes("kotlin.b.xml"),
        "kotlin.b.xml" to includes(),
      )
    ).buildIn(pluginDirPath)
    assertLoads()
  }

  @Test
  fun `transitive include does not change the base dir if absolute path is used inside META-INF`() {
    Spec(
      includes("/META-INF/dir/a.xml"),
      mapOf(
        "META-INF/dir/a.xml" to includes("b.xml"),
        "META-INF/b.xml" to includes(),
      )
    ).buildIn(pluginDirPath)
    assertLoads()
  }

  @Test
  fun `transitive include changes the base dir if absolute path is used outside META-INF`() {
    Spec(
      includes("/dir/a.xml"),
      mapOf(
        "dir/a.xml" to includes("b.xml"),
        "dir/b.xml" to includes(),
      )
    ).buildIn(pluginDirPath) // == /wd/plugins/plugin
    assertDoesNotLoad()
    rootPath.resolve("dir/b.xml") // in real root !! FIXME
      .createParentDirectories().writeXml(includes())
    assertLoads()
  }

  @Test
  fun `intellij-prefixed relative path is resolved from resource root`() {
    Spec(
      includes("intellij.something.xml"),
      mapOf("intellij.something.xml" to includes())
    ).buildIn(pluginDirPath)
    assertLoads()
  }

  @Test
  fun `intellij-prefixed relative path is not resolved from META-INF`() {
    Spec(
      includes("intellij.something.xml"),
      mapOf("META-INF/intellij.something.xml" to includes())
    ).buildIn(pluginDirPath)
    assertDoesNotLoad()
  }

  @Test
  fun `kotlin-prefixed relative path is resolved from resource root`() {
    Spec(
      includes("kotlin.something.xml"),
      mapOf("kotlin.something.xml" to includes())
    ).buildIn(pluginDirPath)
    assertLoads()
  }

  @Test
  fun `kotlin-prefixed relative path is not resolved from META-INF`() {
    Spec(
      includes("kotlin.something.xml"),
      mapOf("META-INF/kotlin.something.xml" to includes())
    ).buildIn(pluginDirPath)
    assertDoesNotLoad()
  }

  @Test
  fun `include by absolute path works - root`() {
    Spec(
      includes("/a.xml"),
      mapOf("a.xml" to includes())
    ).buildIn(pluginDirPath)
    assertLoads()
  }

  @Test
  fun `include by absolute path works - META-INF`() {
    Spec(
      includes("/META-INF/a.xml"),
      mapOf("META-INF/a.xml" to includes())
    ).buildIn(pluginDirPath)
    assertLoads()
  }

  @Test
  fun `transitive include by absolute path works - META-INF`() {
    Spec(
      includes("/META-INF/a.xml"),
      mapOf(
        "META-INF/a.xml" to includes("/META-INF/b.xml", "/META-INF/dir/c.xml"),
        "META-INF/b.xml" to includes(),
        "META-INF/dir/c.xml" to includes()
      )
    ).buildIn(pluginDirPath)
    assertLoads()
  }

  @Test
  fun `transitive include by absolute path works - subdir META-INF`() {
    Spec(
      includes("/META-INF/dir/a.xml"),
      mapOf(
        "META-INF/dir/a.xml" to includes("/META-INF/dir2/b.xml"),
        "META-INF/dir2/b.xml" to includes(),
      )
    ).buildIn(pluginDirPath)
    assertLoads()
  }

  @Test
  fun `recursive include fails eventually`() {
    var err: Throwable? = null
    LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        assertThat(err).isNull()
        err = t
        return false
      }
    }) {
      Spec(
        includes("a.xml"),
        mapOf(
          "META-INF/a.xml" to includes("b.xml"),
          "META-INF/b.xml" to includes("a.xml"),
        )
      ).buildIn(pluginDirPath)
      assertDoesNotLoad()
    }
    assertThat(err).isNotNull()
    val allErrors = generateSequence(err) { it.cause }.toList()
    if (allErrors.any { it is StackOverflowError }) {
      return // ok
    }
    assertThat(allErrors).hasSizeGreaterThan(500)
    val msgs = allErrors.map { it.message ?: "" }
    assertThat(msgs.count { it.contains("Cannot resolve a.xml") }).withFailMessage { msgs.joinToString() }.isGreaterThan(250)
    assertThat(msgs.count { it.contains("Cannot resolve b.xml") }).withFailMessage { msgs.joinToString() }.isGreaterThan(250)
  }

  @Test
  fun `includes are allowed in content modules`() {
    pluginDirPath.resolve("META-INF/plugin.xml").writeXml("""
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>$pluginId</id>
        <content>
          <module name="plugin.id.module" loading="required"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    @Suppress("XmlPathReference")
    pluginDirPath.resolve("plugin.id.module.xml").writeXml(
      """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude" package="plugin.id.module">
        <xi:include href="a.xml"/>
      </idea-plugin>
    """.trimIndent())
    pluginDirPath.resolve("META-INF/a.xml").writeXml(includes = includes(), appServices = listOf("included"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins(pluginId)
    assertThat(pluginSet.getEnabledModule("plugin.id.module")).hasExactlyApplicationServices("included")
  }

  @Test
  fun `includes are allowed inside extensionPoints element`() {
    @Suppress("XmlPathReference")
    pluginDirPath.resolve("META-INF/plugin.xml").writeXml("""
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>$pluginId</id>
        <extensionPoints>
          <extensionPoint qualifiedName="before" interface="before"/>
          <xi:include href="a.xml"/>
          <extensionPoint qualifiedName="after" interface="after"/>
        </extensionPoints>
      </idea-plugin>
    """.trimIndent())
    pluginDirPath.resolve("META-INF/a.xml").writeXml(includes(), appServices = emptyList(), extensionPoints = listOf("included"))
    val pluginSet: PluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins(pluginId)
    assertThat(pluginSet.getEnabledPlugin(pluginId)).hasExactlyExtensionPointsNames("before", "included", "after")
  }

  @Test
  fun `includes are forbidden inside extensions element, yet the plugin loads`() {
    @Suppress("XmlPathReference")
    pluginDirPath.resolve("META-INF/plugin.xml").writeXml("""
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>$pluginId</id>
        <extensions defaultExtensionNs="com.intellij">
          <applicationService serviceImplementation="before"/>
          <xi:include href="a.xml"/>
          <applicationService serviceImplementation="after"/>
        </extensions>
      </idea-plugin>
    """.trimIndent())
    pluginDirPath.resolve("META-INF/a.xml").writeXml(includes(), listOf("included"))
    lateinit var pluginSet: PluginSet
    val err = LoggedErrorProcessor.executeAndReturnLoggedError {
      pluginSet = buildPluginSet()
    }
    assertThat(err).isNotNull.hasMessageContaining("`include` is supported only on a root level")
    assertThat(pluginSet).hasExactlyEnabledPlugins(pluginId)
    assertThat(pluginSet.getEnabledPlugin(pluginId)).hasExactlyApplicationServices("before", "after")
  }

  private fun buildPluginSet(expiredPluginIds: Array<String> = emptyArray(), disabledPluginIds: Array<String> = emptyArray()) =
    PluginSetTestBuilder.fromPath(pluginsPath)
      .withExpiredPlugins(*expiredPluginIds)
      .withDisabledPlugins(*disabledPluginIds)
      .build()

  private fun assertLoads() = assertThat(buildPluginSet()).hasExactlyEnabledPlugins(pluginId)
  private fun assertDoesNotLoad() = assertThat(buildPluginSet()).doesNotHaveEnabledPlugins()

  companion object {
    const val pluginId: String = "plugin.id"

    private fun includes(vararg paths: String) = paths.map(::Include)

    private data class Include(val path: String)

    private data class Spec(
      val pluginIncludes: List<Include>,
      val additionalFiles: Map<String, List<Include>>,
    ) {
      @Language("XML")
      fun pluginXML(): String = """
        <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
          <id>$pluginId</id>
          ${pluginIncludes.xml()}
        </idea-plugin>
      """.trimIndent()
    }

    private fun List<Include>.xml(): String = joinToString("\n") { """<xi:include href="${it.path}"/>""" }

    private fun Spec.buildIn(dir: Path) {
      dir.resolve("META-INF/plugin.xml").createParentDirectories().writeText(pluginXML())
      for ((path, inc) in additionalFiles) {
        dir.resolve(path).writeXml(inc)
      }
    }

    private fun Path.writeXml(includes: List<Include>, appServices: List<String> = emptyList(), extensionPoints: List<String> = emptyList()) {
      createParentDirectories()
      val extPoints = extensionPoints.joinToString("\n") { """<extensionPoint qualifiedName="${it}" interface="${it}"/>""" }
      val services = appServices.joinToString("\n") { """<applicationService serviceImplementation="${it}"/>""" }
      writeText("""
          <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
          ${includes.xml()}
          <extensionPoints>
            $extPoints
          </extensionPoints>
          <extensions defaultExtensionNs="com.intellij">
            $services
          </extensions>
          </idea-plugin>
        """.trimIndent())
    }

    private fun Path.writeXml(@Language("XML") text: String) {
      createParentDirectories()
      writeText(text)
    }
  }
}

