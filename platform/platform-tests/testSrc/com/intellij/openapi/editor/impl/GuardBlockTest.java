package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.CommonProcessors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

  public void testGuardedBlockListIsEmpty() {
    myFixture.configureByText("x.txt", "0123456789");
    assertEmpty("expected no guarded block in the document", getGuardedBlocks());
  }

  public void testCreateGuardedBlock() {
    myFixture.configureByText("x.txt", "0123456789");
    assertEmpty("expected no guarded block in the document", getGuardedBlocks());
    createGuard(0, 1);
    assertEquals("expected one guarded block in the document", 1, getGuardedBlocks().size());
    createGuard(0, 1);
    assertEquals("expected two guarded blocks in the document", 2, getGuardedBlocks().size());
  }

  public void testRemoveGuardedBlock() {
    myFixture.configureByText("x.txt", "0123456789");
    getDocument().removeGuardedBlock(createGuard(0, 1));
    assertEmpty("expected the guarded block to be removed", getGuardedBlocks());
  }

  public void testCreateRemoveCreateGuardedBlock() {
    myFixture.configureByText("x.txt", "0123456789");
    getDocument().removeGuardedBlock(createGuard(0, 1));
    createGuard(0, 1);
    assertEquals(1, getGuardedBlocks().size());
  }

  public void testGetGuardedBlockByOffset() {
    myFixture.configureByText("x.txt", "0123456789");
    DocumentEx document = getDocument();
    RangeMarker guard = createGuard(1, 2);
    assertNull("no guarded block expected covering the offset", document.getOffsetGuard(0));
    assertNull("no guarded block expected covering the offset", document.getOffsetGuard(2));
    assertEquals("guarded block expected covering the offset", guard, document.getOffsetGuard(1));
  }

  public void testGetRangeGuardInRange() {
    myFixture.configureByText("x.txt", "0123456789");
    DocumentEx document = getDocument();
    RangeMarker guard = createGuard(2, 5);
    assertEquals("intersection expected ( [ ] )", guard, document.getRangeGuard(0, 6));
    assertEquals("intersection expected ( [ ) ]", guard, document.getRangeGuard(0, 3));
    assertEquals("intersection expected [ ( ] )", guard, document.getRangeGuard(4, 6));
    assertEquals("intersection expected [ () ]", guard, document.getRangeGuard(3, 4));
    assertEquals("intersection expected [()  ]", guard, document.getRangeGuard(2, 3));
    assertEquals("intersection expected [  ()]", guard, document.getRangeGuard(4, 5));
  }

  public void testGetRangeGuardNotInRange() {
    myFixture.configureByText("x.txt", "0123456789");
    DocumentEx document = getDocument();
    createGuard(2, 5);
    assertNull("no intersection expected () [ ]", document.getRangeGuard(0, 1));
    assertNull("no intersection expected [ ] ()", document.getRangeGuard(6, 7));
  }

  public void testGetRangeGuardInRangeGreedy() {
    myFixture.configureByText("x.txt", "0123456789");
    DocumentEx document = getDocument();
    RangeMarker guard = createGuard(2, 5);
    assertNull("no intersection expected ( | ]", document.getRangeGuard(0, 2));
    assertNull("no intersection expected [ | )", document.getRangeGuard(5, 6));
    guard.setGreedyToLeft(true);
    guard.setGreedyToRight(true);
    assertEquals("intersection expected ( | ]", guard, document.getRangeGuard(0, 2));
    assertEquals("intersection expected [ | )", guard, document.getRangeGuard(5, 6));
  }

  public void testGuardIsAvailableViaDocumentRangeMarkerTree() {
    myFixture.configureByText("x.txt", "0123456789");
    createGuard(0, 1);
    var collector = new CommonProcessors.CollectProcessor<>();
    getDocument().processRangeMarkers(collector);
    assertEquals("guarded block expected to be available via range marker tree", 1, collector.getResults().size());
  }

  public void testGuardedBlockApiIsNotBypassed() {
    myFixture.configureByText("x.txt", "0123456789");
    RangeMarker guard = createGuard(0, 1);
    assertThrows(Exception.class, () -> getGuardedBlocks().add(guard));
    assertThrows(Exception.class, () -> getGuardedBlocks().clear());
    assertThrows(Exception.class, () -> getGuardedBlocks().remove(guard));
    assertEquals("guarded block list must not be modified directly", 1, getGuardedBlocks().size());
    assertNotEmpty(getGuardedBlocks());
  }

  @SuppressWarnings("CallToSystemGC")
  public void testGuardIsNotCollectedByGC() throws InterruptedException {
    myFixture.configureByText("x.txt", "0123456789");
    createGuard(0, 1);
    System.gc(); Thread.sleep(200);
    System.gc(); Thread.sleep(200);
    List<RangeMarker> blocks = getGuardedBlocks();
    RangeMarker guard = blocks.get(0);

    assertTrue("guarded block expected to be valid", guard.isValid());
    assertEquals("guarded block bounds expected to be preserved", new TextRange(0, 1), guard.getTextRange());
    assertEquals("expected only one guarded block in the document", 1, blocks.size());
  }

  public void testRemovedGuardIsDisposed() {
    myFixture.configureByText("x.txt", "0123456789");
    RangeMarker guard = createGuard(0, 1);
    getDocument().removeGuardedBlock(guard);

    assertEmpty("expected no guarded block in document", getDocument().getGuardedBlocks());
    assertFalse("removed guard expected to be not valid", guard.isValid());

    var collector = new CommonProcessors.CollectProcessor<>();
    getDocument().processRangeMarkers(collector);
    assertEmpty("removed guard expected to be unregistered from range tree", collector.getResults());
  }

  private @NotNull List<RangeMarker> getGuardedBlocks() {
    return getDocument().getGuardedBlocks();
  }

  private @NotNull DocumentEx getDocument() {
    return ((DocumentEx)myFixture.getEditor().getDocument());
  }
}
