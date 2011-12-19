package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

/**
 * @author cdr
 */
public class GuardBlockTest extends LightPlatformCodeInsightTestCase {
  private static RangeMarker createGuard(final int start, final int end) {
    final Document document = getEditor().getDocument();
    return document.createGuardedBlock(start, end);
  }

  public void testZero() throws Exception {
    configureFromFileText("x.txt", "xxxx");
    RangeMarker guard = createGuard(0, 0);
    guard.setGreedyToLeft(true);
    guard.setGreedyToRight(true);
    checkUnableToTypeIn(0);
    checkCanTypeIn(1);
  }

  public void testInTheMiddle() throws Exception {
    configureFromFileText("x.txt", "xxxxxxxx");
    createGuard(1, 4);
    checkUnableToTypeIn(2);
    checkCanTypeIn(0);
  }

  public void testGreedy() throws Exception {
    configureFromFileText("x.txt", "012345678");
    {
      RangeMarker guard = createGuard(0, 5);
      guard.setGreedyToLeft(true);
      guard.setGreedyToRight(false);
    }
    {
      RangeMarker guard = createGuard(5, 6);
      guard.setGreedyToLeft(false);
      guard.setGreedyToRight(true);
    }
    checkCanTypeIn(5);
    checkUnableToTypeIn(0);
  }
  public void testGreedyEnd() throws Exception {
    configureFromFileText("x.txt", "012345678");
    {
      RangeMarker guard = createGuard(0, 5);
      guard.setGreedyToLeft(true);
      guard.setGreedyToRight(true);
    }
    checkUnableToTypeIn(5);
  }

  private static void checkUnableToTypeIn(int offset) {
    String text = getEditor().getDocument().getText();
    try {
      getEditor().getCaretModel().moveToOffset(offset);
      type("y");
    }
    catch (RuntimeException e) {
      assertEquals("Unable to perform an action since it changes read-only fragments of the current document",e.getMessage());
      assertEquals(text, getEditor().getDocument().getText());
      return;
    }
    fail("must be read only at "+offset);
  }

  private static void checkCanTypeIn(int offset) {
    getEditor().getCaretModel().moveToOffset(offset);
    type("yy");
  }
}
