// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class GitLabOAuthSettingsTest {

  @Test
  fun `empty text produces empty map`() {
    val settings = GitLabOAuthSettings()
    settings.clientIdsText = ""
    assertEquals(emptyMap<String, String>(), settings.clientIds)
  }

  @Test
  fun `blank lines only produce empty map`() {
    val settings = GitLabOAuthSettings()
    settings.clientIdsText = "\n   \n\t\n"
    assertEquals(emptyMap<String, String>(), settings.clientIds)
  }

  @Test
  fun `setting clientIdsText persists into clientIds state`() {
    val settings = GitLabOAuthSettings()
    settings.clientIdsText = "https://gitlab.com=abc123"
    assertEquals("abc123", settings.clientIds["https://gitlab.com"])
  }

  @Test
  fun `single well-formed line is parsed`() {
    val settings = GitLabOAuthSettings()
    settings.clientIdsText = "https://gitlab.com=abc123"
    assertEquals(mapOf("https://gitlab.com" to "abc123"), settings.clientIds)
  }

  @Test
  fun `multiple well-formed lines are all parsed`() {
    val settings = GitLabOAuthSettings()
    settings.clientIdsText = "https://gitlab.com=abc123\nhttps://gitlab.example.com=def456"
    assertEquals(
      mapOf(
        "https://gitlab.com" to "abc123",
        "https://gitlab.example.com" to "def456",
      ),
      settings.clientIds
    )
  }

  @Test
  fun `surrounding whitespace around server, client id and separator is trimmed`() {
    val settings = GitLabOAuthSettings()
    settings.clientIdsText = "  https://gitlab.com  =  abc123  "
    assertEquals(mapOf("https://gitlab.com" to "abc123"), settings.clientIds)
  }

  @Test
  fun `blank lines between valid lines are ignored`() {
    val settings = GitLabOAuthSettings()
    settings.clientIdsText = "https://gitlab.com=abc123\n\n   \nhttps://gitlab.example.com=def456\n"
    assertEquals(
      mapOf(
        "https://gitlab.com" to "abc123",
        "https://gitlab.example.com" to "def456",
      ),
      settings.clientIds
    )
  }

  @Test
  fun `line without a separator is skipped`() {
    val settings = GitLabOAuthSettings()
    settings.clientIdsText = "https://gitlab.com=abc123\nnoSeparatorHere"
    assertEquals(mapOf("https://gitlab.com" to "abc123"), settings.clientIds)
  }

  @Test
  fun `line with empty server is skipped`() {
    val settings = GitLabOAuthSettings()
    settings.clientIdsText = "https://gitlab.com=abc123\n=abc123"
    assertEquals(mapOf("https://gitlab.com" to "abc123"), settings.clientIds)
  }

  @Test
  fun `line with empty client id is skipped`() {
    val settings = GitLabOAuthSettings()
    settings.clientIdsText = "https://gitlab.com=abc123\nhttps://gitlab.example.com="
    assertEquals(mapOf("https://gitlab.com" to "abc123"), settings.clientIds)
  }

  @Test
  fun `only the first separator splits server from client id`() {
    val settings = GitLabOAuthSettings()
    settings.clientIdsText = "https://gitlab.com=part1=part2"
    assertEquals(mapOf("https://gitlab.com" to "part1=part2"), settings.clientIds)
  }

  @Test
  fun `duplicate server key keeps the last occurrence`() {
    val settings = GitLabOAuthSettings()
    settings.clientIdsText = "https://gitlab.com=first\nhttps://gitlab.com=second"
    assertEquals(mapOf("https://gitlab.com" to "second"), settings.clientIds)
  }

  @Test
  fun `mix of valid and malformed lines keeps only valid entries`() {
    val settings = GitLabOAuthSettings()
    settings.clientIdsText = """
      https://gitlab.com=abc123
      noSeparatorHere
      =missingServer
      https://missing.clientid.com=

      https://gitlab.example.com=def456
    """.trimIndent()
    assertEquals(
      mapOf(
        "https://gitlab.com" to "abc123",
        "https://gitlab.example.com" to "def456",
      ),
      settings.clientIds
    )
  }

  @Test
  fun `setting clientIds map is reflected by clientIdsText getter`() {
    val settings = GitLabOAuthSettings()
    settings.clientIds = mapOf("https://gitlab.com" to "abc123")
    assertEquals("https://gitlab.com=abc123", settings.clientIdsText)
  }
}
