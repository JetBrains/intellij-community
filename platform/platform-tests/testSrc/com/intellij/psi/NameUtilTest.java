// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.psi.codeStyle.NameUtil;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

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
    assertSplitEquals(new String[]{"测", "试", "打", "补", "丁"}, "测试打补丁");
  }

  private static void assertSplitEquals(String[] expected, String name) {
    final String[] result = NameUtil.splitNameIntoWords(name);
    assertEquals(Arrays.asList(expected).toString(), Arrays.asList(result).toString());
  }
}
