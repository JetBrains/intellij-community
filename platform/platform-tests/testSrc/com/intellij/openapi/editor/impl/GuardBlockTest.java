package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class GuardBlockTest extends BasePlatformTestCase {
  private RangeMarker createGuard(final int start, final int end) {
    final Document document = myFixture.getEditor().getDocument();
    return document.createGuardedBlock(start, end);
  }

  public void testZero() {
    myFixture.configureByText("x.txt", "xxxx");
    RangeMarker guard = createGuard(0, 0);
    guard.setGreedyToLeft(true);
    guard.setGreedyToRight(true);
    checkUnableToTypeIn(0);
    checkCanTypeIn(1);
  }

  public void testInTheMiddle() {
    myFixture.configureByText("x.txt", "xxxxxxxx");
    createGuard(1, 4);
    checkUnableToTypeIn(2);
    checkCanTypeIn(0);
  }

  public void testGreedy() {
    myFixture.configureByText("x.txt", "012345678");
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
  public void testGreedyEnd() {
    myFixture.configureByText("x.txt", "012345678");
    {
      RangeMarker guard = createGuard(0, 5);
      guard.setGreedyToLeft(true);
      guard.setGreedyToRight(true);
    }
    checkUnableToTypeIn(5);
  }

  private void checkUnableToTypeIn(int offset) {
    String text = myFixture.getEditor().getDocument().getText();
    try {
      myFixture.getEditor().getCaretModel().moveToOffset(offset);
      myFixture.type("y");
    }
    catch (RuntimeException e) {
      assertEquals("Unable to perform an action since it changes read-only fragments of the current document",e.getMessage());
      assertEquals(text, myFixture.getEditor().getDocument().getText());
      return;
    }
    fail("must be read only at "+offset);
  }

  private void checkCanTypeIn(int offset) {
    myFixture.getEditor().getCaretModel().moveToOffset(offset);
    myFixture.type("yy");
  }

  public void testNoCompletion() {
    String text = "abc abd a<caret> abx";
    myFixture.configureByText("x.txt", text);
    int offset = myFixture.getEditor().getCaretModel().getOffset();
    createGuard(offset - 1, myFixture.getFile().getTextLength()).setGreedyToRight(true);

    assertNull(myFixture.completeBasic());
    myFixture.checkResult(text);

    //no hippie completion
    myFixture.performEditorAction(IdeActions.ACTION_HIPPIE_BACKWARD_COMPLETION);
    assertNull(LookupManager.getInstance(getProject()).getActiveLookup());
    myFixture.checkResult(text);
    
    //no completion at the file end
    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getFile().getTextLength());
    assertNull(myFixture.completeBasic());
    myFixture.checkResult("abc abd a abx<caret>");

    //completion at the beginning of the guard fragment
    myFixture.getEditor().getCaretModel().moveToOffset(offset - 1);
    assertNotNull(myFixture.completeBasic());
  }

  public void testDocumentGuardedTextUtilDuplicateLine() {
    String text = "a\n#%%\nc";
    //              ^^^^^^^   <- guarded
    // com.intellij.openapi.editor.actions.DocumentGuardedTextUtil.insertString handles this case and inserts "\na" after first "a"
    myFixture.configureByText("x.txt", text);
    RangeMarker guard = createGuard(1, 6);

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE_LINES);
    myFixture.checkResult("a\na\n#%%\nc");
    assertTrue(guard.isValid());
    assertEquals(3, guard.getStartOffset());
    assertEquals(8, guard.getEndOffset());
  }

  public void testDocumentGuardedTextUtilDeleteLine() {
    String text = "a\n<caret>b\n#%%\nc";
    //                        ^^^^^^^   <- guarded
    // com.intellij.openapi.editor.actions.DocumentGuardedTextUtil.deleteString handles this case and removes \n before caret
    myFixture.configureByText("x.txt", text);
    RangeMarker guard = createGuard(3, 8);

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE_LINE);
    myFixture.checkResult("a\n#%%\nc");
    assertTrue(guard.isValid());
    assertEquals(1, guard.getStartOffset());
    assertEquals(6, guard.getEndOffset());
  }

  public void testDocumentGuardedTextUtilDuplicateLineWithGreedy() {
    String text = "a\n#%%\nc";
    //              ^^^^^^^   <- guarded
    myFixture.configureByText("x.txt", text);
    createGuard(1, 6).setGreedyToLeft(true);

    try {
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE_LINES);
    }
    catch (RuntimeException e) {
      assertEquals("Unable to perform an action since it changes read-only fragments of the current document", e.getMessage());
      myFixture.checkResult(text);
      return;
    }
    fail("must be read only at " + 1);
  }

  public void testDocumentGuardedTextUtilDeleteLineWithGreedy() {
    String text = "a\n<caret>b\n#%%\nc";
    //                        ^^^^^^^   <- guarded
    myFixture.configureByText("x.txt", text);
    createGuard(3, 8).setGreedyToLeft(true);

    try {
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE_LINE);
    }
    catch (RuntimeException e) {
      assertEquals("Unable to perform an action since it changes read-only fragments of the current document", e.getMessage());
      myFixture.checkResult(text);
      return;
    }
    fail("must be read only at " + 3);
  }
}
