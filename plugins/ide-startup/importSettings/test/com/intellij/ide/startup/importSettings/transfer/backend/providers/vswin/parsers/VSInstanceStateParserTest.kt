// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.parsers

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

@TestApplication
class VSInstanceStateParserTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `title is the en-us title`() {
    val parser = parser(localizedResources("de-de" to "German title", "en-us" to "Visual Studio Professional 2026"))

    assertEquals("Visual Studio Professional 2026", parser.title)
  }

  @Test
  fun `title is the first title when en-us is absent`() {
    val parser = parser(localizedResources("de-de" to "German title", "fr-fr" to "French title"))

    assertEquals("German title", parser.title)
  }

  @Test
  fun `blank title is ignored`() {
    val parser = parser(localizedResources("en-us" to " ", "de-de" to "German title"))

    assertEquals("German title", parser.title)
  }

  @Test
  fun `title is null without localized resources`() {
    assertNull(parser("""{"installationName":"VisualStudio/18.9.1+12112.369"}""").title)
  }

  @Test
  fun `title is null for malformed json`() {
    assertNull(parser("""{"localizedResources":[""").title)
  }

  @Test
  fun `title is null when the file does not exist`() {
    assertNull(VSInstanceStateParser(tempDir.resolve("missing.json")).title)
  }

  private fun parser(content: String): VSInstanceStateParser {
    val file = tempDir.resolve("state.json")
    file.writeText(content)
    return VSInstanceStateParser(file)
  }

  private fun localizedResources(vararg titles: Pair<String, String>): String =
    titles.joinToString(prefix = """{"localizedResources":[""", postfix = "]}") { [language, title] ->
      """{"language":"$language","title":"$title","description":"Description"}"""
    }
}
