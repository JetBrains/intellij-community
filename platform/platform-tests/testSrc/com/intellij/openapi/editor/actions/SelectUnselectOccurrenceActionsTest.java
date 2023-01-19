/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.LightweightHint;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class SelectUnselectOccurrenceActionsTest extends BasePlatformTestCase {
  private int hintCount;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    EditorHintListener listener = new EditorHintListener() {
      @Override
      public void hintShown(Project project,
                            @NotNull LightweightHint hint,
                            int flags) {
        hintCount++;
      }
    };
    ApplicationManager.getApplication().getMessageBus().connect(myFixture.getTestRootDisposable()).subscribe(EditorHintListener.TOPIC, listener);
  }

  public void testAllWithoutInitialSelection() {
    init("""
           some t<caret>ext
           some texts
           another text here"""
    );
    executeSelectAllAction();
    checkResult("""
                  some <selection>t<caret>ext</selection>
                  some texts
                  another <selection>t<caret>ext</selection> here""");
  }

  public void testAllWithInitialWholeWordSelection() {
    init("""
           some <selection>t<caret>ext</selection>
           some texts
           some texts
           another text here""");
    executeSelectAllAction();
    checkResult("""
                  some <selection>t<caret>ext</selection>
                  some <selection>t<caret>ext</selection>s
                  some <selection>t<caret>ext</selection>s
                  another <selection>t<caret>ext</selection> here""");
    assertEquals(0, hintCount);
  }

  public void testNoInitialSelection() {
    init("""
           some t<caret>ext
           some texts
           another text here"""
    );
    executeAction();
    checkResult("""
                  some <selection>t<caret>ext</selection>
                  some texts
                  another text here""");
    executeAction();
    checkResult("""
                  some <selection>t<caret>ext</selection>
                  some texts
                  another <selection>t<caret>ext</selection> here""");
    assertEquals(0, hintCount);
  }

  public void testInitialWholeWordSelection() {
    init("""
           some <selection>t<caret>ext</selection>
           some texts
           another text here""");
    executeAction();
    checkResult("""
                  some <selection>t<caret>ext</selection>
                  some <selection>t<caret>ext</selection>s
                  another text here""");
    assertEquals(0, hintCount);
  }

  public void testShowingHint() {
    init("some <selection>t<caret>ext</selection>\n" +
         "another <selection>t<caret>ext</selection> here");
    executeAction();
    assertEquals(1, hintCount);
    checkResult("some <selection>t<caret>ext</selection>\n" +
                "another <selection>t<caret>ext</selection> here");
    executeAction();
    assertEquals(1, hintCount);
    checkResult("some <selection>t<caret>ext</selection>\n" +
                "another <selection>t<caret>ext</selection> here");
  }

  public void testRevert() {
    init("some <selection>t<caret>ext</selection>\n" +
         "another <selection>t<caret>ext</selection> here");
    executeReverseAction();
    checkResult("some <selection>t<caret>ext</selection>\n" +
                "another text here");
    assertEquals(0, hintCount);
  }

  public void testRevertSingleSelection() {
    init("""
           some <selection>t<caret>ext</selection>
           some texts
           another text here""");
    executeReverseAction();
    checkResult("""
                  some t<caret>ext
                  some texts
                  another text here""");
    assertEquals(0, hintCount);
  }

  public void testSelectAfterHint() {
    init("""
           some text
           some texts
           another <selection>t<caret>ext</selection> here""");
    executeAction();
    checkResult("""
                  some text
                  some texts
                  another <selection>t<caret>ext</selection> here""");
    assertEquals(1, hintCount);
    executeAction();
    checkResult("""
                  some <selection>t<caret>ext</selection>
                  some texts
                  another <selection>t<caret>ext</selection> here""");
    assertEquals(1, hintCount);
  }

  public void testInitialNonWholeWordSelection() {
    init("""
           some <selection>t<caret>ex</selection>t
           some texts
           another text here""");
    executeAction();
    checkResult("""
                  some <selection>t<caret>ex</selection>t
                  some <selection>t<caret>ex</selection>ts
                  another text here""");
    executeAction();
    checkResult("""
                  some <selection>t<caret>ex</selection>t
                  some <selection>t<caret>ex</selection>ts
                  another <selection>t<caret>ex</selection>t here""");
    assertEquals(0, hintCount);
  }

  public void testOccurrenceInCollapsedRegion() {
    init("normal <selection><caret>line</selection>\n" +
         "collapsed line");
    final FoldingModel foldingModel = myFixture.getEditor().getFoldingModel();
    final Document document = myFixture.getEditor().getDocument();
    foldingModel.runBatchFoldingOperation(() -> {
      FoldRegion foldRegion = foldingModel.addFoldRegion(document.getLineStartOffset(1), document.getLineEndOffset(1), "...");
      assertNotNull(foldRegion);
      foldRegion.setExpanded(false);
    });
    executeAction();
    checkResult("normal <selection><caret>line</selection>\n" +
                "collapsed <selection><caret>line</selection>");
    FoldRegion[] foldRegions = foldingModel.getAllFoldRegions();
    assertEquals(1, foldRegions.length);
    assertTrue(foldRegions[0].isExpanded());
  }

  public void testSelectAfterNotFoundAndUnselect() {
    init("text <selection><caret>text</selection> <selection><caret>text</selection>");
    executeAction();
    executeReverseAction();
    executeAction();
    checkResult("text <selection><caret>text</selection> <selection><caret>text</selection>");
  }

  public void testEscapeReturnsToInitialPosition() {
    init("l<caret>ine\n" +
         "another line");
    executeAction();
    myFixture.performEditorAction("EditorEscape");
    checkResult("l<caret>ine\n" +
                "another line");
  }

  public void testSelectingAdjacentFragments() {
    init("fragment<selection>fragment<caret></selection>");
    executeAction();
    executeAction();
    checkResult("<selection>fragment<caret></selection><selection>fragment<caret></selection>");
  }

  public void testSkippingOccurrence() {
    init("""
           fr<caret>uit
           fruits
           fruit
           fruits
           fruit""");
    executeAction();
    executeAction();
    executeFindNext();
    checkResult("""
                  <selection>fr<caret>uit</selection>
                  fruits
                  fruit
                  fruits
                  <selection>fr<caret>uit</selection>""");
  }

  public void testMovingSelectionBackAndForth() {
    init("""
           fr<caret>uit
           fruits
           fruit
           fruits
           fruit""");
    executeAction();
    executeAction();
    executeFindNext();
    executeFindPrevious();
    executeAction();
    checkResult("""
                  <selection>fr<caret>uit</selection>
                  fruits
                  <selection>fr<caret>uit</selection>
                  fruits
                  <selection>fr<caret>uit</selection>""");
  }

  public void testSkipDoesNotRemovePreviousSelections() {
    init("""
           <caret>fruit
           fruit
           fruit""");
    executeAction();
    executeAction();
    executeFindNext();
    executeFindNext();
    assertEquals(1, hintCount);
    executeFindNext();
    assertEquals(1, hintCount);
    checkResult("""
                  <selection><caret>fruit</selection>
                  fruit
                  <selection><caret>fruit</selection>""");
  }

  public void testNearInlays() {
    init("cat cat");
    EditorTestUtil.addInlay(myFixture.getEditor(), 0);
    EditorTestUtil.addInlay(myFixture.getEditor(), 4);
    myFixture.getEditor().getCaretModel().moveToVisualPosition(new VisualPosition(0, 1));
    executeAction();
    executeAction();
    checkResult("<selection><caret>cat</selection> <selection><caret>cat</selection>");
    assertEquals(Arrays.asList(new VisualPosition(0, 1),
                               new VisualPosition(0, 6)),
                 ContainerUtil.map(myFixture.getEditor().getCaretModel().getAllCarets(), Caret::getVisualPosition));
  }

  private void init(String text) {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, text);
  }

  private void checkResult(String text) {
    myFixture.checkResult(text);
  }

  private void executeAction() {
    myFixture.performEditorAction(IdeActions.ACTION_SELECT_NEXT_OCCURENCE);
  }

  private void executeFindNext() {
    myFixture.performEditorAction(IdeActions.ACTION_FIND_NEXT);
  }

  private void executeFindPrevious() {
    myFixture.performEditorAction(IdeActions.ACTION_FIND_PREVIOUS);
  }

  private void executeReverseAction() {
    myFixture.performEditorAction(IdeActions.ACTION_UNSELECT_PREVIOUS_OCCURENCE);
  }

  private void executeSelectAllAction() {
    myFixture.performEditorAction(IdeActions.ACTION_SELECT_ALL_OCCURRENCES);
  }
}
