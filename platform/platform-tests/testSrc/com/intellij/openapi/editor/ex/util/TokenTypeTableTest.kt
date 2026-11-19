// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util

import com.intellij.lang.Language
import com.intellij.psi.tree.IElementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Checks [TokenTypeTable].
 */
internal class TokenTypeTableTest {
  @Test
  fun `the empty table has no values`() {
    val table = TokenTypeTable.empty<String>()
    assertThat(table.get(INDEXED)).isNull()
    assertThat(table.get(WITHOUT_INDEX)).isNull()
  }

  @Test
  fun `a table with a value keeps the values of the old table`() {
    for (tokenType in listOf(INDEXED, WITHOUT_INDEX)) {
      val old = TokenTypeTable.empty<String>().with(OTHER_INDEXED, "other")
      val new = old.with(tokenType, "value")
      assertThat(new.get(tokenType)).isEqualTo("value")
      assertThat(new.get(OTHER_INDEXED)).isEqualTo("other")
    }
  }

  @Test
  fun `the old table has no new value of an indexed token type`() {
    val old = TokenTypeTable.empty<String>()
    old.with(INDEXED, "value")
    assertThat(old.get(INDEXED)).isNull()
  }

  @Test
  fun `the tables of one empty table share the values of token types without an index`() {
    val old = TokenTypeTable.empty<String>()
    old.with(WITHOUT_INDEX, "value")
    assertThat(old.get(WITHOUT_INDEX)).isEqualTo("value")
    assertThat(TokenTypeTable.empty<String>().get(WITHOUT_INDEX)).isNull()
  }

  @Test
  fun `each token type keeps its own value`() {
    val table = TokenTypeTable.empty<String>()
      .with(INDEXED, "indexed")
      .with(OTHER_INDEXED, "other")
      .with(WITHOUT_INDEX, "without index")
    assertThat(table.get(INDEXED)).isEqualTo("indexed")
    assertThat(table.get(OTHER_INDEXED)).isEqualTo("other")
    assertThat(table.get(WITHOUT_INDEX)).isEqualTo("without index")
  }

  private companion object {
    val INDEXED = IElementType("TOKEN_TYPE_TABLE_TEST_INDEXED", Language.ANY)
    val OTHER_INDEXED = IElementType("TOKEN_TYPE_TABLE_TEST_OTHER_INDEXED", Language.ANY)
    val WITHOUT_INDEX = object : IElementType("TOKEN_TYPE_TABLE_TEST_WITHOUT_INDEX", Language.ANY, false) {}
  }
}
