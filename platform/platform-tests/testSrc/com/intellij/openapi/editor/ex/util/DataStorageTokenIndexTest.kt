// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util

import com.intellij.lang.Language
import com.intellij.psi.tree.IElementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Checks [DataStorage.unpackTokenIndexFromData].
 */
internal class DataStorageTokenIndexTest {
  @Test
  fun `the short storage gives the index of the token type in the data`() {
    assertIndexOfTokenType(ShortBasedStorage())
  }

  @Test
  fun `the int storage gives the index of the token type in the data`() {
    assertIndexOfTokenType(IntBasedStorage())
  }

  @Test
  fun `a subclass of a storage does not know the index`() {
    for (storage in listOf(object : ShortBasedStorage() {}, object : IntBasedStorage() {})) {
      val data = storage.packData(TOKEN_TYPE, 0, true)
      assertThat(storage.unpackTokenIndexFromData(data)).isEqualTo(-1)
    }
  }

  @Test
  fun `a segment array that keeps the unpacking of its storage gives the index`() {
    val segments = object : SegmentArrayWithData(ShortBasedStorage()) {}
    val data = segments.packData(TOKEN_TYPE, 0, true)
    assertThat(segments.unpackTokenIndexFromData(data)).isEqualTo(TOKEN_TYPE.index.toInt())
  }

  @Test
  fun `a segment array that unpacks token types itself does not know the index`() {
    val segments = object : SegmentArrayWithData(ShortBasedStorage()) {
      override fun unpackTokenFromData(data: Int): IElementType {
        return OTHER_TOKEN_TYPE
      }
    }
    val data = segments.packData(TOKEN_TYPE, 0, true)
    assertThat(segments.unpackTokenIndexFromData(data)).isEqualTo(-1)
  }

  private fun assertIndexOfTokenType(storage: DataStorage) {
    for (restartable in listOf(true, false)) {
      val data = storage.packData(TOKEN_TYPE, 0, restartable)
      assertThat(storage.unpackTokenIndexFromData(data)).isEqualTo(TOKEN_TYPE.index.toInt())
      assertThat(storage.unpackTokenFromData(data)).isSameAs(TOKEN_TYPE)
    }
  }

  private companion object {
    val TOKEN_TYPE = IElementType("DATA_STORAGE_TOKEN_INDEX_TEST", Language.ANY)
    val OTHER_TOKEN_TYPE = IElementType("DATA_STORAGE_TOKEN_INDEX_TEST_OTHER", Language.ANY)
  }
}
