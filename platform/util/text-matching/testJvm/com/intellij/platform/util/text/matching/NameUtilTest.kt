// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.text.matching

import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.text.NameUtilCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * todo: move to platform test module together with com.intellij.psi.codeStyle.NameUtil
 */
class NameUtilTest {
  @Test
  fun testSplitIntoWords1() {
    assertSplitEquals(listOf("I", "Base"), "IBase")
  }

  @Test
  fun testSplitIntoWords2() {
    assertSplitEquals(listOf("Order", "Index"), "OrderIndex")
  }

  @Test
  fun testSplitIntoWords3() {
    assertSplitEquals(listOf("order", "Index"), "orderIndex")
  }

  @Test
  fun testSplitIntoWords4() {
    assertSplitEquals(listOf("Order", "Index"), "Order_Index")
  }

  @Test
  fun testSplitIntoWords5() {
    assertSplitEquals(listOf("ORDER", "INDEX"), "ORDER_INDEX")
  }

  @Test
  fun testSplitIntoWords6() {
    assertSplitEquals(listOf("gg", "J"), "ggJ")
  }

  @Test
  fun testSplitIntoWordsCN() {
    assertSplitEquals(listOf("测", "试", "打", "补", "丁", "2"), "测试打补丁2")
  }

  @Test
  fun testSplitIntoWordsJP() {
    assertSplitEquals(
      listOf("ローマ", "由", "来", "の", "アルファベット", "（", "ラテン", "文", "字", "）", "を", "用", "いて", "日", "本", "語",
             "を", "表", "記", "することもでき", "、", "日", "本", "では", "ローマ", "字", "と", "呼", "ばれる"),
      "ローマ由来のアルファベット（ラテン文字）を用いて日本語を表記することもでき、日本ではローマ字と呼ばれる")
    assertSplitEquals(
      listOf("近", "代", "では", "日", "本", "人", "が", "漢", "語", "を", "造", "語", "する", "例", "もあり", "、", "英", "語",
             "の", "philosophy", "、", "ドイツ", "語", "の", "Philosophie", "を", "指", "す", "用", "語"),
      "近代では日本人が漢語を造語する例もあり、英語のphilosophy、ドイツ語のPhilosophieを指す用語")
  }

  @Test
  fun testEmoji() {
    assertSplitEquals(listOf("\uD83E\uDD2B", " ", "\uD83D\uDD2B", "\uD83E\uDDD2"),
                      "\uD83E\uDD2B \uD83D\uDD2B\uD83E\uDDD2")
  }


  @Test
  fun testIsWordStart() {
    assertTrue(NameUtilCore.isWordStart("测试打补丁", 0))
    assertTrue(NameUtilCore.isWordStart("测试打补丁", 2))
  }

  private fun assertSplitEquals(expected: List<String>, name: String) {
    assertEquals(expected, NameUtil.splitNameIntoWordList(name))
  }
}