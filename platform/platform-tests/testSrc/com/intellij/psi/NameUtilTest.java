/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

import com.intellij.psi.codeStyle.NameUtil;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author dsl
 */
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

  private static void assertSplitEquals(String[] expected, String name) {
    final String[] result = NameUtil.splitNameIntoWords(name);
    assertEquals(Arrays.asList(expected).toString(), Arrays.asList(result).toString());
  }
}
