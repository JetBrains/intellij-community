// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MigrationContentManifestTest {
  @Test
  fun `locale falls back to the language and then to the default locale`() {
    val manifest = manifest(locales = mapOf("en" to offer("English"), "zh-CN" to offer("Simplified"), "zh" to offer("Chinese")))
    assertEquals("Simplified", valid(manifest, Locale.forLanguageTag("zh-CN")).offer.title)
    assertEquals("Chinese", valid(manifest, Locale.forLanguageTag("zh-TW")).offer.title)
    assertEquals("English", valid(manifest, Locale.forLanguageTag("de")).offer.title)
  }

  @Test
  fun `locale match ignores the case`() {
    val manifest = manifest(locales = mapOf("en" to offer("English"), "ZH-cn" to offer("Simplified")))
    assertEquals("Simplified", valid(manifest, Locale.forLanguageTag("zh-CN")).offer.title)
  }

  @Test
  fun `locale without a valid offer falls back to the next one`() {
    val manifest = manifest(locales = mapOf("en" to offer("English"), "ja" to """{ "ready": { "title": "x" } }""",
                                            "ko" to """{ "offer": { "title": "no buttons", "body": "b" } }"""))
    assertEquals("English", valid(manifest, Locale.JAPANESE).offer.title)
    assertEquals("English", valid(manifest, Locale.KOREAN).offer.title)
  }

  @Test
  fun `offer fields are parsed`() {
    val offer = valid(manifest(), Locale.ENGLISH).offer
    assertEquals("GoLand moves to Unified IDE", offer.title)
    assertEquals("Your projects come with you.", offer.body)
    assertEquals(listOf("One IDE", "Starts faster"), offer.bullets)
    val learnMore = offer.learnMore!!
    assertEquals("Learn more", learnMore.text)
    assertEquals("https://www.jetbrains.com/unified/", learnMore.url)
    assertEquals("Update to Unified IDE", offer.primaryButton)
    assertEquals("Remind Me Later", offer.secondaryButton)
  }

  @Test
  fun `ready fields are parsed`() {
    val ready = valid(manifest(), Locale.ENGLISH).ready!!
    assertEquals("Unified IDE is ready", ready.title)
    assertEquals("Restart to Update", ready.action)
  }

  @Test
  fun `manifest without a valid ready section is valid`() {
    assertNull(valid(manifest(locales = mapOf("en" to offer("English", ready = null))), Locale.ENGLISH).ready)
    val partial = offer("English", ready = """{ "title": "Unified IDE is ready", "action": " " }""")
    assertNull(valid(manifest(locales = mapOf("en" to partial)), Locale.ENGLISH).ready)
  }

  @Test
  fun `ready section follows the locale match`() {
    val japanese = offer("Japanese", ready = """{ "title": "Japanese ready", "action": "Japanese action" }""")
    val korean = offer("Korean", ready = """{ "title": "Korean ready" }""")
    val manifest = manifest(locales = mapOf("en" to offer("English"), "ja" to japanese, "ko" to korean))
    assertEquals("Japanese ready", valid(manifest, Locale.JAPANESE).ready!!.title)
    val koreanResult = valid(manifest, Locale.KOREAN)
    assertEquals("Korean", koreanResult.offer.title)
    assertEquals("Unified IDE is ready", koreanResult.ready!!.title)
  }

  @Test
  fun `redirect fields are parsed`() {
    val redirect = valid(manifest(locales = mapOf("en" to offer("English", redirect = redirect("GoLand is now part of Unified IDE")))),
                         Locale.ENGLISH).redirect!!
    assertEquals("GoLand", redirect.windowTitle)
    assertEquals("GoLand is now part of Unified IDE", redirect.title)
    assertEquals("This icon now opens Unified IDE.", redirect.body)
    assertEquals("Launch Unified IDE", redirect.primaryButton)
  }

  @Test
  fun `manifest without a valid redirect section is valid`() {
    assertNull(valid(manifest(), Locale.ENGLISH).redirect)
    val blank = offer("English", redirect = redirect(" "))
    assertNull(valid(manifest(locales = mapOf("en" to blank)), Locale.ENGLISH).redirect)
    val partial = offer("English", redirect = """{ "title": "GoLand is now part of Unified IDE" }""")
    assertNull(valid(manifest(locales = mapOf("en" to partial)), Locale.ENGLISH).redirect)
  }

  @Test
  fun `redirect section follows the locale match`() {
    val english = offer("English", redirect = redirect("English redirect"))
    val japanese = offer("Japanese", redirect = redirect("Japanese redirect"))
    val korean = offer("Korean", redirect = """{ "title": "Korean redirect" }""")
    val manifest = manifest(locales = mapOf("en" to english, "ja" to japanese, "ko" to korean))
    assertEquals("Japanese redirect", valid(manifest, Locale.JAPANESE).redirect!!.title)
    assertEquals("English redirect", valid(manifest, Locale.KOREAN).redirect!!.title)
  }

  @Test
  fun `no offer in any locale is invalid`() {
    invalid(manifest(locales = mapOf("en" to """{ "ready": { "title": "x" } }""")), Locale.ENGLISH)
  }

  @Test
  fun `unknown major schema version is invalid`() {
    invalid(manifest(schemaVersion = "2"), Locale.ENGLISH)
    invalid(manifest(schemaVersion = "0"), Locale.ENGLISH)
    valid(manifest(schemaVersion = "1.3"), Locale.ENGLISH)
  }

  @Test
  fun `unknown fields are ignored`() {
    val text = manifest().trimEnd().removeSuffix("}") + """, "future": { "anything": [1, 2] } }"""
    valid(text, Locale.ENGLISH)
  }

  @Test
  fun `missing required fields are invalid`() {
    invalid("""{ "schemaVersion": 1 }""", Locale.ENGLISH)
    invalid("not a json", Locale.ENGLISH)
    invalid(manifest(images = """{ "sourceLogo": ${image("go")} }"""), Locale.ENGLISH)
    invalid(manifest(images = """{ "sourceLogo": { "light": "https://www.jetbrains.com/go.svg" }, "targetLogo": ${image("jb")} }"""),
            Locale.ENGLISH)
  }

  @Test
  fun `background is optional`() {
    val images = valid(manifest(), Locale.ENGLISH).images
    assertNull(images.background)
    val withBackground = manifest(images = """{ "sourceLogo": ${image("go")}, "targetLogo": ${image("jb")}, "background": ${image("bg")} }""")
    assertEquals("https://www.jetbrains.com/bg_dark.svg", valid(withBackground, Locale.ENGLISH).images.background!!.dark)
  }

  @Test
  fun `content URLs must be https on jetbrains com`() {
    assertTrue(isAllowedContentUrl("https://www.jetbrains.com/a.svg", allowFileUrls = false))
    assertTrue(isAllowedContentUrl("https://jetbrains.com/a.svg", allowFileUrls = false))
    assertTrue(isAllowedContentUrl("HTTPS://Resources.JetBrains.com/a.svg", allowFileUrls = false))
    assertFalse(isAllowedContentUrl("http://www.jetbrains.com/a.svg", allowFileUrls = false))
    assertFalse(isAllowedContentUrl("https://example.com/a.svg", allowFileUrls = false))
    assertFalse(isAllowedContentUrl("https://jetbrains.com.evil.com/a.svg", allowFileUrls = false))
    assertFalse(isAllowedContentUrl("https://evil-jetbrains.com/a.svg", allowFileUrls = false))
    assertFalse(isAllowedContentUrl("https://www.jetbrains.com@evil.com/a.svg", allowFileUrls = false))
    assertFalse(isAllowedContentUrl("javascript:alert(1)", allowFileUrls = true))
    assertFalse(isAllowedContentUrl("not a url", allowFileUrls = true))
  }

  @Test
  fun `final URL can be on the regional mirror, the first URL cannot`() {
    val mirror = "https://download-cdn.clf.jetbrains.com.cn/go/goland-2027.1-aarch64.dmg"
    assertTrue(isAllowedFinalContentUrl(mirror, allowFileUrls = false))
    assertFalse(isAllowedContentUrl(mirror, allowFileUrls = false))
    assertTrue(isAllowedFinalContentUrl("https://jetbrains.com.cn/a.dmg", allowFileUrls = false))
    assertTrue(isAllowedFinalContentUrl("https://download-cdn.jetbrains.com/a.dmg", allowFileUrls = false))
  }

  @Test
  fun `final URL must be https on a JetBrains domain`() {
    assertFalse(isAllowedFinalContentUrl("https://jetbrains.com.cn.evil.com/a.dmg", allowFileUrls = false))
    assertFalse(isAllowedFinalContentUrl("https://evil-jetbrains.com.cn/a.dmg", allowFileUrls = false))
    assertFalse(isAllowedFinalContentUrl("http://download-cdn.clf.jetbrains.com.cn/a.dmg", allowFileUrls = false))
    assertFalse(isAllowedFinalContentUrl("https://jetbrains.com.evil.com/a.dmg", allowFileUrls = false))
    assertFalse(isAllowedFinalContentUrl("https://example.com/a.dmg", allowFileUrls = false))
    assertFalse(isAllowedFinalContentUrl("https://www.jetbrains.com.cn@evil.com/a.dmg", allowFileUrls = false))
  }

  @Test
  fun `final file URL needs the debug flag`() {
    assertFalse(isAllowedFinalContentUrl("file:///tmp/a.dmg", allowFileUrls = false))
    assertTrue(isAllowedFinalContentUrl("file:///tmp/a.dmg", allowFileUrls = true))
  }

  @Test
  fun `file URLs need the debug flag`() {
    assertFalse(isAllowedContentUrl("file:///tmp/a.svg", allowFileUrls = false))
    assertTrue(isAllowedContentUrl("file:///tmp/a.svg", allowFileUrls = true))

    val fileImages = """{ "sourceLogo": ${image("go", "file:///tmp")}, "targetLogo": ${image("jb", "file:///tmp")} }"""
    invalid(manifest(images = fileImages), Locale.ENGLISH, allowFileUrls = false)
    valid(manifest(images = fileImages), Locale.ENGLISH, allowFileUrls = true)
  }

  @Test
  fun `invalid image or link URL makes the manifest invalid`() {
    invalid(manifest(images = """{ "sourceLogo": ${image("go", "http://www.jetbrains.com")}, "targetLogo": ${image("jb")} }"""),
            Locale.ENGLISH)
    val badLink = offer("English", learnMoreUrl = "https://example.com/")
    invalid(manifest(locales = mapOf("en" to badLink)), Locale.ENGLISH)
  }

  private fun valid(text: String, locale: Locale, allowFileUrls: Boolean = false): MigrationManifestParseResult.Valid {
    val result = parseMigrationManifest(text, locale, allowFileUrls)
    return when (result) {
      is MigrationManifestParseResult.Valid -> result
      is MigrationManifestParseResult.Invalid -> throw AssertionError("expected a valid manifest: ${result.reason}")
    }
  }

  private fun invalid(text: String, locale: Locale, allowFileUrls: Boolean = false) {
    when (parseMigrationManifest(text, locale, allowFileUrls)) {
      is MigrationManifestParseResult.Valid -> throw AssertionError("expected an invalid manifest")
      is MigrationManifestParseResult.Invalid -> {}
    }
  }

  private fun manifest(
    schemaVersion: String = "1",
    images: String = """{ "sourceLogo": ${image("go")}, "targetLogo": ${image("jb")} }""",
    locales: Map<String, String> = mapOf("en" to offer("GoLand moves to Unified IDE")),
  ): String = """
    {
      "schemaVersion": ${schemaVersion},
      "id": "go-to-unified-2027.1",
      "defaultLocale": "en",
      "images": ${images},
      "locales": { ${locales.entries.joinToString { "\"${it.key}\": ${it.value}" }} }
    }"""

  private fun image(name: String, base: String = "https://www.jetbrains.com"): String =
    """{ "light": "${base}/${name}.svg", "dark": "${base}/${name}_dark.svg" }"""

  private fun offer(
    title: String,
    learnMoreUrl: String = "https://www.jetbrains.com/unified/",
    ready: String? = """{ "title": "Unified IDE is ready", "action": "Restart to Update" }""",
    redirect: String? = null,
  ): String = """
    {
      "offer": {
        "title": "${title}",
        "body": "Your projects come with you.",
        "bullets": ["One IDE", "Starts faster"],
        "learnMore": { "text": "Learn more", "url": "${learnMoreUrl}" },
        "primaryButton": "Update to Unified IDE",
        "secondaryButton": "Remind Me Later"
      }${ready?.let { """, "ready": ${it}""" } ?: ""}${redirect?.let { """, "redirect": ${it}""" } ?: ""}
    }"""

  private fun redirect(title: String): String = """
    {
      "windowTitle": "GoLand",
      "title": "${title}",
      "body": "This icon now opens Unified IDE.",
      "primaryButton": "Launch Unified IDE"
    }"""
}
