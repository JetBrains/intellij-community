// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Pins the runtime fix that made [getMessageByCoordinate] return `null` when an input contained
 * `|b|<bundle>|k|<key>|` markers but none of them could be resolved, instead of returning the raw
 * marker string. Without this, the placeholder text leaks straight into Search Everywhere -> Actions
 * (e.g. "|b|messages.J2EEBundle|k|title.j2ee.names|" appearing as a result row).
 */
@TestApplication
class GetMessageByCoordinateTest {
  @Test
  fun `plain hit without markers is returned unchanged`() {
    val classLoader = URLClassLoader(emptyArray(), null)
    val result = getMessageByCoordinate(s = "Plain Hit", classLoader = classLoader, locale = Locale.ROOT)
    assertEquals("Plain Hit", result)
  }

  @Test
  fun `unresolvable marker returns null instead of raw marker text`(@TempDir tempDir: Path) {
    // Empty classpath: the marker references a bundle that does not exist anywhere reachable.
    // Before f7a9f938 this used to fall through to `first ?: s` and return the raw "|b|...|k|...|"
    // text, which leaked into the Actions search.
    val classLoader = URLClassLoader(arrayOf(tempDir.toUri().toURL()), null)
    val marker = "|b|test.absolutely.no.such.bundle.GetMessageByCoordinateTest|k|missing.key|"
    val result = getMessageByCoordinate(s = marker, classLoader = classLoader, locale = Locale.ROOT)
    assertNull(result, "Unresolvable marker must return null so doRegisterIndex drops the entry")
  }

  @Test
  fun `unresolvable marker with dots ending returns null instead of raw marker text`(@TempDir tempDir: Path) {
    val classLoader = URLClassLoader(arrayOf(tempDir.toUri().toURL()), null)
    val marker = "|b|messages.IdeBundle|k|encoding.name..."
    val result = getMessageByCoordinate(s = marker, classLoader = classLoader, locale = Locale.ROOT)
    assertNull(result, "Unresolvable marker must return null so doRegisterIndex drops the entry")
  }

  @Test
  fun `resolvable marker returns the localized value`(@TempDir tempDir: Path) {
    writeProperties(
      tempDir.resolve("messages/GetMessageByCoordinateTestBundle.properties"),
      "settings.hints" to "Hint Settings",
    )
    val classLoader = URLClassLoader(arrayOf(tempDir.toUri().toURL()), null)
    val result = getMessageByCoordinate(
      s = "|b|messages.GetMessageByCoordinateTestBundle|k|settings.hints|",
      classLoader = classLoader,
      locale = Locale.ROOT,
    )
    assertEquals("Hint Settings", result)
  }

  @Test
  fun `bundle present but key missing returns null`(@TempDir tempDir: Path) {
    writeProperties(
      tempDir.resolve("messages/GetMessageByCoordinateTestBundle.properties"),
      "some.other.key" to "Some Other Value",
    )
    val classLoader = URLClassLoader(arrayOf(tempDir.toUri().toURL()), null)
    val result = getMessageByCoordinate(
      s = "|b|messages.GetMessageByCoordinateTestBundle|k|missing.key|",
      classLoader = classLoader,
      locale = Locale.ROOT,
    )
    assertNull(result, "Marker with a missing key must not leak the raw marker text")
  }

  private fun writeProperties(file: Path, vararg entries: Pair<String, String>) {
    Files.createDirectories(file.parent)
    Files.writeString(file, entries.joinToString(separator = "\n") { "${it.first}=${it.second}" })
  }
}
