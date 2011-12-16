package com.intellij.psi;

import junit.framework.TestCase;

import java.util.Arrays;

import com.intellij.psi.codeStyle.NameUtil;

/**
 *  @author dsl
 */
public class PsiNameHelperTest extends TestCase {
  public void testSplitIntoWords1() throws Exception {
    assertSplitEquals(new String[]{"I", "Base"}, "IBase");
  }

  public void testSplitIntoWords2() throws Exception {
    assertSplitEquals(new String[]{"Order", "Index"}, "OrderIndex");
  }

  public void testSplitIntoWords3() throws Exception {
    assertSplitEquals(new String[]{"order", "Index"}, "orderIndex");
  }

  public void testSplitIntoWords4() throws Exception {
    assertSplitEquals(new String[]{"Order", "Index"}, "Order_Index");
  }

  public void testSplitIntoWords5() throws Exception {
    assertSplitEquals(new String[]{"ORDER", "INDEX"}, "ORDER_INDEX");
  }


  public void testSplitIntoWords6() throws Exception {
    assertSplitEquals(new String[]{"gg", "J"}, "ggJ");
  }

  private void assertSplitEquals(String[] expected, String name) {
    final String[] result = NameUtil.splitNameIntoWords(name);
    assertEquals(Arrays.asList(expected).toString(), Arrays.asList(result).toString());
  }
}
