// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.text.NameUtilCore;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NameUtilTest {
  @Test
  public void testSplitIntoWords1() {
    assertSplitEquals(new String[]{"I", "Base"}, "IBase");
  }

  @Test
  public void testSplitIntoWords2() {
    assertSplitEquals(new String[]{"Order", "Index"}, "OrderIndex");
  }

  @Test
  public void testSplitIntoWords3() {
    assertSplitEquals(new String[]{"order", "Index"}, "orderIndex");
  }

  @Test
  public void testSplitIntoWords4() {
    assertSplitEquals(new String[]{"Order", "Index"}, "Order_Index");
  }

  @Test
  public void testSplitIntoWords5() {
    assertSplitEquals(new String[]{"ORDER", "INDEX"}, "ORDER_INDEX");
  }

  @Test
  public void testSplitIntoWords6() {
    assertSplitEquals(new String[]{"gg", "J"}, "ggJ");
  }

  @Test
  public void testSplitIntoWordsCN() {
    assertSplitEquals(new String[]{"测", "试", "打", "补", "丁", "2"}, "测试打补丁2");
  }

  @Test
  public void testSplitIntoWordsJP() {
    assertSplitEquals(
      new String[]{"ローマ", "由", "来", "の", "アルファベット", "（", "ラテン", "文", "字", "）", "を", "用", "いて", "日", "本", "語", "を", "表", "記", "することもでき", "、", "日", "本", "では", "ローマ", "字", "と", "呼", "ばれる"}, 
      "ローマ由来のアルファベット（ラテン文字）を用いて日本語を表記することもでき、日本ではローマ字と呼ばれる");
    //noinspection NonAsciiCharacters
    assertSplitEquals(new String[]{"近", "代", "では", "日", "本", "人", "が", "漢", "語", "を", "造", "語", "する", "例", "もあり", "、", "英", "語", "の", "philosophy", "、", "ドイツ", "語", "の", "Philosophie", "を", "指", "す", "用", "語"}, 
                      "近代では日本人が漢語を造語する例もあり、英語のphilosophy、ドイツ語のPhilosophieを指す用語");
  }
  
  @Test
  public void testEmoji() {
    assertSplitEquals(new String[]{"\uD83E\uDD2B", " ", "\uD83D\uDD2B", "\uD83E\uDDD2"}, "\uD83E\uDD2B \uD83D\uDD2B\uD83E\uDDD2");
  }
  
  
  @Test
  public void testIsWordStart() {
    assertTrue(NameUtilCore.isWordStart("测试打补丁", 0));
    assertTrue(NameUtilCore.isWordStart("测试打补丁", 2));
  }

  private static void assertSplitEquals(String[] expected, String name) {
    final String[] result = NameUtil.splitNameIntoWords(name);
    assertEquals(Arrays.asList(expected).toString(), Arrays.asList(result).toString());
  }
}
