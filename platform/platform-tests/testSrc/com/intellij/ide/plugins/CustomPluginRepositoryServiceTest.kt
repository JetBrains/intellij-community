// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.rules.TempDirectory
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CustomPluginRepositoryServiceTest {

  @JvmField
  @Rule
  val tempDir: TempDirectory = TempDirectory()

  @JvmField
  @Rule
  val disposableRule = DisposableRule()

  class MyRepoContributor(private val xmlFile: File) : CustomPluginRepoContributor {
    override fun getRepoUrls(): MutableList<String> {
      return listOf(xmlFile.toURI().toURL().toString()).toMutableList()
    }
  }

  @Test
  fun `non-int plugin versions should not prevent other plugins from loading`() {
    val firstBadPluginRepo = tempDir.newFile("repoFirstInstanceOfBadPlugin.xml").also {
      FileUtil.writeToFile(it, firstSourceOfBadPlugin)
    }

    val secondBadPluginRepo = tempDir.newFile("repoSecondInstanceOfBadPlugin.xml").also {
      FileUtil.writeToFile(it, secondSourceOfBadPlugin)
    }

    ExtensionTestUtil.maskExtensions(CustomPluginRepoContributor.EP_NAME,
                                     listOf(MyRepoContributor(firstBadPluginRepo), MyRepoContributor(secondBadPluginRepo)),
                                     disposableRule.disposable)

    val customPlugins = CustomPluginRepositoryService.getInstance().customRepositoryPlugins
    assertEquals(2, customPlugins.size, "Expected both plugins, got: $customPlugins")
    val badPluginDescriptor = customPlugins.find { it.name == "BadVersionPlugin" }
    assertNotNull(badPluginDescriptor, "plugin with non-integer version not found, got plugins: $customPlugins")
    assertEquals("233.20000-SNAPSHOT-2023127120450", badPluginDescriptor.version, "Expected bad plugin with a newer version")
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule: ApplicationRule = ApplicationRule()

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

    // we need two plugin repos with the same plugin id because version comparison is only triggered when a plugin is encountered more than once
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
}