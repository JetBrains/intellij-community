// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.lang.Language
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Holds a custom discriminator dimension that is not the language. The tests pin it with the generic `this[key] = value` form. */
private val PROFILE = SettingsKey("test.access.profile", default = "")

/** Checks that the [access] builder overlays a context, refines an overlay, and collapses back to the base context. */
@TestApplication
class SettingsAccessContextTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val project get() = projectFixture.get()

  /** Returns a real language that is not [Language.ANY]. The `intellij.xml.plugin` test dependency registers it. */
  private val html: Language get() = requireNotNull(Language.findLanguageByID("HTML"))

  @Test
  fun `access over a plain context pins the built-in language and custom dimensions across the chain`() {
    val base = SettingsContext.project(project)
    val access = base.access {
      language = html
      this[PROFILE] = "release"
    }

    assertNotSame(base, access, "pinning a dimension yields an overlay, not the original context")
    assertEquals(html, access.language)
    assertEquals(html, access.discriminator(LANGUAGE), "language is the built-in LANGUAGE dimension")
    assertEquals("release", access.discriminator(PROFILE))

    assertEquals(base.identity, access.identity, "the overlay keeps the identity of the base")
    assertEquals(base.chain().map { it.identity }, access.chain().map { it.identity }, "the overlay mirrors the base chain, does not extend it")
    assertTrue(access.chain().all { it.language == html && it.discriminator(PROFILE) == "release" },
               "both dimensions propagate to every ancestor")
  }

  @Test
  fun `re-accessing an overlay refines it in place - keeps prior dimensions, replaces conflicts, does not nest`() {
    val base = SettingsContext.project(project)
    val first = base.access { language = Language.ANY; this[PROFILE] = "release" }
    val second = first.access { language = html }

    assertEquals(html, second.language, "a conflicting dimension is replaced")
    assertEquals("release", second.discriminator(PROFILE), "an untouched dimension is inherited")
    assertEquals(base.chain().size, second.chain().size, "refined over the same base rather than wrapped again")
  }

  @Test
  fun `access collapses back to the plain base context once no dimension remains`() {
    val base = SettingsContext.project(project)
    assertSame(base, base.access { }, "an empty build pins nothing and returns the base unchanged")

    val access = base.access { language = Language.ANY }
    assertNotSame(base, access, "the overlay is a distinct context while a dimension is pinned")
    assertSame(base, access.access { language = null }, "clearing the last dimension collapses back to the base")
  }
}

private fun SettingsContext.chain(): List<SettingsContext> = generateSequence(this) { it.parentContext }.toList()
