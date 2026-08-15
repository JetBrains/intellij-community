// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

@TestApplication
internal class DocumentTextTest {

  @Test
  fun `withMetadata of the same snapshot is a no-op`() {
    val snapshot = snapshot("abc")
    assertSame(snapshot, snapshot.withMetadata(snapshot))
  }

  @Test
  fun `withMetadata with the same text takes the metadata snapshot`() {
    val base = snapshot("abc")
    val metadata = base.withModStamp(42L, true) // shares the text instance with `base`
    val merged = base.withMetadata(metadata)
    assertSame(metadata, merged)
    assertEquals(42L, merged.modState().stamp())
    assertEquals(base.modState().sequence() + 1, merged.modState().sequence())
  }

  @Test
  fun `withMetadata with a different text keeps this text and takes the other metadata`() {
    val base = snapshot("a\nb")
    val changed = base.withPatch(
      DocumentTextPatch.simple(
        startOffset = 3,
        endOffset = 3,
        newFragment = "\nc",
        newModStamp = base.modState().stamp() + 1,
        clearLineFlags = false,
      )
    )
    val metadata = base
      .withModStamp(42L, true)
      .withModStamp(43L, true) // twice, so that modSequence differs from the one of `changed`
    val merged = changed.withMetadata(metadata)
    assertEquals("a\nb\nc", merged.text().string()) // this text survives
    assertEquals(3, merged.text().lineCount()) // and so does its line structure
    assertEquals(43L, merged.modState().stamp()) // metadata is taken from the other snapshot
    assertEquals(metadata.modState().sequence(), merged.modState().sequence())
    assertNotEquals(changed.modState().sequence(), merged.modState().sequence())
  }

  private fun snapshot(text: String): DocumentSnapshot {
    return DocumentImpl(text).core.snapshot()
  }
}
