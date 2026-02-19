// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.Disposable
import com.intellij.openapi.updateSettings.impl.UpdateSettingsProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertNotNull

@TestApplication
class CustomPluginRepositoryServiceTest {
  @TestDisposable lateinit var disposable: Disposable

  class TestUpdateSettingsProvider(private val xmlFile: Path) : UpdateSettingsProvider {
    override fun getPluginRepositories(): List<String?> =
      listOf(xmlFile.toUri().toURL().toString())
  }

  @Test fun `non-int plugin versions should not prevent other plugins from loading`(@TempDir tempDir: Path) {
    val firstBadPluginRepo = tempDir.resolve("repoFirstInstanceOfBadPlugin.xml").apply { writeText(firstSourceOfBadPlugin) }
    val secondBadPluginRepo = tempDir.resolve("repoSecondInstanceOfBadPlugin.xml").apply { writeText(secondSourceOfBadPlugin) }
    ExtensionTestUtil.maskExtensions(
      UpdateSettingsProvider.EP_NAME,
      listOf(TestUpdateSettingsProvider(firstBadPluginRepo), TestUpdateSettingsProvider(secondBadPluginRepo)),
      disposable)

    val customPlugins = CustomPluginRepositoryService.getInstance().customRepositoryPlugins
    assertThat(customPlugins).hasSize(2)

    val badPluginDescriptor = customPlugins.find { it.name == "BadVersionPlugin" }
    assertNotNull(badPluginDescriptor, "plugin with non-integer version not found, got plugins: ${customPlugins}")
    assertThat(badPluginDescriptor.version)
      .describedAs { "Expected bad plugin with a newer version" }
      .isEqualTo("233.20000-SNAPSHOT-2023127120450")
  }

  private val firstSourceOfBadPlugin = """
    <?xml version="1.0" encoding="UTF-8"?>
    <plugin-repository>
      <category name="Category1">
        <idea-plugin downloads="1" size="1024" date="1119060380000" url="">
          <name>BadVersionPlugin</name>
          <id>com.jetbrains.BadVersionPlugin</id>
          <description>...</description>
          <version>233.20000-SNAPSHOT-2023127120450</version>
          <vendor email="foobarv@jetbrains.com" url="http://www.jetbrains.com">JetBrains</vendor>
          <idea-version min="n/a" max="n/a" since-build="133.193"/>
          <change-notes>...</change-notes>
          <rating>3.5</rating>
          <download-url>plugin.zip</download-url>
        </idea-plugin>
        </category>
    </plugin-repository>
    """.trimIndent()

  // we need more than one plugin repo with the same plugin ID to trigger version comparison
  private val secondSourceOfBadPlugin = """
    <?xml version="1.0" encoding="UTF-8"?>
    <plugin-repository>
      <category name="Category1">
        <idea-plugin downloads="1" size="1024" date="1119060380000" url="">
          <name>BadVersionPlugin</name>
          <id>com.jetbrains.BadVersionPlugin</id>
          <description>...</description>
          <version>233.20000-SNAPSHOT-2023126114357</version>
          <vendor email="foobarv@jetbrains.com" url="http://www.jetbrains.com">JetBrains</vendor>
          <idea-version min="n/a" max="n/a" since-build="133.193"/>
          <change-notes>...</change-notes>
          <rating>3.5</rating>
          <download-url>plugin.zip</download-url>
        </idea-plugin>
        <idea-plugin downloads="6182" size="131276" date="1386612959000" url="">
          <name>GoodPluginVersion</name>
          <id>com.intellij.GoodPluginVersion</id>
          <description>...</description>
          <version>1.2</version>
          <vendor email="" url="http://www.jetbrains.com">JetBrains</vendor>
          <idea-version min="n/a" max="n/a" since-build="133.193"/>
          <change-notes>...</change-notes>
          <downloadUrl>plugin.zip</downloadUrl>
        </idea-plugin>
        </category>
    </plugin-repository>
    """.trimIndent()
}
