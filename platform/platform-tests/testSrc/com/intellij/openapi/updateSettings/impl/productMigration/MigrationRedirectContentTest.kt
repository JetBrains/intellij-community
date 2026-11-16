// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.openapi.application.productMigration.migrationRedirectContentFile
import com.intellij.openapi.application.productMigration.migrationStateFile
import com.intellij.openapi.application.productMigration.readMigrationRedirectContent
import com.intellij.openapi.application.productMigration.writeMigrationRedirectContent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class MigrationRedirectContentTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  private val configDir: Path by lazy { tempDir.root.toPath().resolve("config") }
  private val file: Path by lazy { migrationRedirectContentFile(migrationStateFile(configDir)) }

  @Test
  fun `content copy is next to the state file`() {
    assertEquals(configDir.resolve("migration-redirect.json"), file)
  }

  @Test
  fun `content is written and read`() {
    val content = redirectContent()
    writeMigrationRedirectContent(content, file)
    val read = readMigrationRedirectContent(file)!!
    assertEquals("GoLand", read.texts.windowTitle)
    assertEquals("GoLand is now part of Unified IDE", read.texts.title)
    assertEquals("<html>This GoLand icon now opens Unified IDE & more.", read.texts.body)
    assertEquals("Launch Unified IDE", read.texts.primaryButton)
    assertArrayEquals(content.sourceLogo.light, read.sourceLogo.light)
    assertArrayEquals(content.sourceLogo.dark, read.sourceLogo.dark)
    assertArrayEquals(content.targetLogo.light, read.targetLogo.light)
    assertArrayEquals(content.targetLogo.dark, read.targetLogo.dark)
  }

  @Test
  fun `missing file gives no content`() {
    assertNull(readMigrationRedirectContent(file))
  }

  @Test
  fun `unknown fields are ignored`() {
    write(json(extra = """, "future": { "a": [1] }""", textsExtra = """, "subtitle": { "x": 1 }""",
               imagesExtra = """, "background": ${image("#000005", "#000006")}"""))
    assertNotNull(readMigrationRedirectContent(file))
  }

  @Test
  fun `no content deletes the old copy`() {
    writeMigrationRedirectContent(redirectContent(), file)
    writeMigrationRedirectContent(null, file)
    assertFalse(file.exists())
    writeMigrationRedirectContent(null, file)
    assertFalse(file.exists())
  }

  @Test
  fun `invalid content gives no content`() {
    val invalid = mutableListOf(
      "not a json",
      "[]",
      json(version = "2"),
      json(version = "\"1\""),
      json(images = """{ "sourceLogo": ${image("#000001", "#000002")} }"""),
      json(images = """{ "sourceLogo": { "light": "${base64(svg("#000001"))}" }, "targetLogo": ${image("#000003", "#000004")} }"""),
      json(images = """{ "sourceLogo": { "light": "${base64("not an svg".toByteArray())}", "dark": "${base64(svg("#000002"))}" }, "targetLogo": ${image("#000003", "#000004")} }"""),
      json(images = """{ "sourceLogo": { "light": "%%%", "dark": "${base64(svg("#000002"))}" }, "targetLogo": ${image("#000003", "#000004")} }"""),
    )
    for (key in TEXT_KEYS) {
      invalid.add(json(texts = texts(except = key)))
      invalid.add(json(texts = texts(except = key, extra = """, "${key}": " " """)))
    }
    for (text in invalid) {
      write(text)
      assertNull(text, readMigrationRedirectContent(file))
    }
  }

  private fun write(text: String) {
    configDir.createDirectories()
    file.writeText(text)
  }

  private fun json(
    version: String = "1",
    texts: String = texts(),
    images: String = """{ "sourceLogo": ${image("#000001", "#000002")}, "targetLogo": ${image("#000003", "#000004")} }""",
    extra: String = "",
    textsExtra: String = "",
    imagesExtra: String = "",
  ): String = """{ "version": ${version}, "texts": ${texts.removeSuffix("}")}${textsExtra} }, "images": ${images.removeSuffix("}")}${imagesExtra} }${extra} }"""

  private fun texts(except: String? = null, extra: String = ""): String =
    TEXT_KEYS.filter { it != except }.joinToString(prefix = "{ ", postfix = "${extra}}") { "\"${it}\": \"text of ${it}\"" }

  private fun image(light: String, dark: String): String = """{ "light": "${base64(svg(light))}", "dark": "${base64(svg(dark))}" }"""

  private fun base64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)
}

private val TEXT_KEYS = listOf("windowTitle", "title", "body", "primaryButton")
