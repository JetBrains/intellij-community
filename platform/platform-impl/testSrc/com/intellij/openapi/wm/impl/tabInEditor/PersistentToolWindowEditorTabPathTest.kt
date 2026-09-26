// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class PersistentToolWindowEditorTabPathTest {

  @Nested
  @DisplayName("Contract: Serialization & Deserialization (Round-trip)")
  inner class Serialization {

    @Test
    fun `should losslessly serialize and parse back any valid strings`() {

      // A list of unusual paths that must remain exactly the same after being serialized and deserialized.
      val edgeCases = mapOf(
        "Standard ASCII" to PersistentToolWindowEditorTabPath("hash", "tool", "persist", "name"),
        "Empty strings" to PersistentToolWindowEditorTabPath("", "", "", ""),
        "Strings with spaces" to PersistentToolWindowEditorTabPath("h a s h", "t o o l", " ", "  "),
        "Strings with slashes (delimiter injection)" to PersistentToolWindowEditorTabPath("h/a/s/h", "t/o/o/l", "p/e/r/s/i/s/t", "n/a/m/e"),
        "Cyrillic & Unicode" to PersistentToolWindowEditorTabPath("хэш", "окно", "ид", "Название Вкладки"),
        "Emojis and Surrogate Pairs" to PersistentToolWindowEditorTabPath("hash🔧", "tool🛠", "persist💾", "name🔥"),
        "Special characters" to PersistentToolWindowEditorTabPath("\n\t\r", "!@#$%^&*()", "{}", "<>")
      )

      edgeCases.forEach { (description, original) ->
        val serialized = original.toString()
        val parsed = PersistentToolWindowEditorTabPath.parse(serialized)

        assertNotNull(parsed) { "[$description] Parsing should not return null for valid serialized string" }

        assertAll("[$description] Deserialized fields must match original exactly",
                  { assertEquals(original.projectLocationHash, parsed?.projectLocationHash, "Hash mismatch") },
                  { assertEquals(original.toolWindowId, parsed?.toolWindowId, "ToolWindowId mismatch") },
                  { assertEquals(original.persistenceId, parsed?.persistenceId, "PersistenceId mismatch") },
                  { assertEquals(original.name, parsed?.name, "Name mismatch") }
        )
      }
    }

    @Test
    fun `serialization must produce exactly 4 URL-safe segments separated by slashes`() {
      val path = PersistentToolWindowEditorTabPath("hash/1", "window/2", "id/3", "name/4")

      val serialized = path.toString()
      val segments = serialized.split("/")

      assertEquals(4, segments.size) { "Serialized path must contain exactly 3 slashes" }
    }
  }

  @Nested
  @DisplayName("Contract: Parsing Failures")
  inner class ParsingFailures {

    @Test
    fun `parse should return null when segment count is not exactly 4`() {
      val invalidPaths = listOf(
        "",                                 // 1 empty segment
        "c29tZQ/c29tZQ",                    // 2 segments
        "c29tZQ/c29tZQ/c29tZQ",             // 3 segments
        "c29tZQ/c29tZQ/c29tZQ/c29tZQ/extra" // 5 segments
      )

      invalidPaths.forEach { invalidPath ->
        assertNull(
          PersistentToolWindowEditorTabPath.parse(invalidPath),
          "Expected null for path with invalid segments count: '$invalidPath'"
        )
      }
    }

    @Test
    fun `parse should gracefully return null on malformed Base64 payload`() {
      // "!" is not valid Base64
      val pathWithBadBase64 = "c29tZQ/c29tZQ/c29tZQ/fls!!sls!"
      assertNull(PersistentToolWindowEditorTabPath.parse(pathWithBadBase64))
    }
  }

  @Nested
  @DisplayName("Contract: Identity (equals & hashCode)")
  inner class Identity {

    @Test
    fun `equality must be based strictly on structural keys, IGNORING name`() {
      val path1 = PersistentToolWindowEditorTabPath("hash1", "tool1", "persist1", "Old Name")
      val path2 = PersistentToolWindowEditorTabPath("hash1", "tool1", "persist1", "New Name")

      assertEquals(path1, path2) { "Paths with same keys but different names must be equal" }
      assertEquals(path1.hashCode(), path2.hashCode()) { "Hash codes must match if equals is true" }
    }

    @Test
    fun `equality must strictly differentiate upon any key mutation`() {
      val base = PersistentToolWindowEditorTabPath("H", "T", "P", "N")

      assertAll(
        { assertNotEquals(base, PersistentToolWindowEditorTabPath("H_DIFF", "T", "P", "N")) },
        { assertNotEquals(base, PersistentToolWindowEditorTabPath("H", "T_DIFF", "P", "N")) },
        { assertNotEquals(base, PersistentToolWindowEditorTabPath("H", "T", "P_DIFF", "N")) }
      )
    }
  }

  @Nested
  @DisplayName("Contract: Mutations")
  inner class Mutations {

    @Test
    fun `withName should return a new immutable instance with updated name only`() {
      val original = PersistentToolWindowEditorTabPath("H", "T", "P", "Old Name")

      val updated = original.withName("New Name")

      assertNotSame(original, updated) { "withName must return a new instance" }
      assertEquals("New Name", updated.name)

      assertEquals(original, updated)
    }
  }
}
