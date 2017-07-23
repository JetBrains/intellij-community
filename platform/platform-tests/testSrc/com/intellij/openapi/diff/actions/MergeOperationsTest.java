/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diff.BaseDiffTestCase;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.util.Assertion;

import java.util.List;

public class MergeOperationsTest extends BaseDiffTestCase {
  private final Assertion CHECK = new Assertion();
  private DiffPanelImpl myDiffPanel;
  private MergeOperations myMergeOperations1;
  private SimpleContent myContent1;
  private SimpleContent myContent2;
  private MergeOperations myMergeOperations2;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDiffPanel = createDiffPanel(null, myProject, true);
    myContent1 = new SimpleContent("abc\n123\nxyz");
    myContent1.setReadOnly(false);
    myContent2 = new SimpleContent("abc\n098\nxyz");
    myContent2.setReadOnly(false);
    myMergeOperations1 = new MergeOperations(myDiffPanel, FragmentSide.SIDE1);
    myMergeOperations2 = new MergeOperations(myDiffPanel, FragmentSide.SIDE2);
    setContents();
  }

  @Override
  protected void tearDown() throws Exception {
    myContent1 = null;
    myContent2 = null;
    myMergeOperations1 = null;
    myMergeOperations2 = null;
    myDiffPanel = null;
    super.tearDown();
  }

  private void setContents() {
    myDiffPanel.setContents(myContent1, myContent2);
  }

  public void testNothingToDo() {
    moveToOffset1(0);
    CHECK.empty(myMergeOperations1.getOperations());
  }

  public void testInsert() {
    moveToOffset1(6);
    List<MergeOperations.Operation> operations = myMergeOperations1.getOperations();
    assertEquals(3, operations.size());
    MergeOperations.Operation insert = getRemove(operations);
    insert.perform(myProject);
    assertEquals("abc\nxyz", myContent1.getText());
    moveToOffset2(5);
    myDiffPanel.getDiffUpdater().updateNow();
    operations = myMergeOperations2.getOperations();
    CHECK.size(2, operations);
  }

  public void testReplace() {
    moveToOffset1(6);
    MergeOperations.Operation replace = getReplace(myMergeOperations1.getOperations());
    replace.perform(myProject);
    assertEquals("abc\n123\nxyz", myContent2.getText());
  }

  public void testNoActionsBeforeRediff() {
    assertEquals(1, countAction(getEditor1(myDiffPanel)));
    assertEquals(1, countAction(getEditor2(myDiffPanel)));
    replaceString(myContent1.getDocument(), 4, 6, "n\ne\nw");
    assertEquals(0, myDiffPanel.getLineBlocks().getCount());
    assertEquals(0, countAction(getEditor1(myDiffPanel)));
    assertEquals(0, countAction(getEditor2(myDiffPanel)));
  }

  private static int countAction(Editor editor) {
    RangeHighlighter[] allHighlighters = editor.getMarkupModel().getAllHighlighters();
    int counter = 0;
    for (RangeHighlighter highlighter : allHighlighters) {
      GutterIconRenderer iconRenderer = (GutterIconRenderer)highlighter.getGutterIconRenderer();
      if (iconRenderer == null) continue;
      AnAction action = iconRenderer.getClickAction();
      assertEquals(action == null, iconRenderer.getIcon() == null);
      if (action != null) counter++;
    }
    return counter;
  }

  private static MergeOperations.Operation getReplace(List<MergeOperations.Operation> operations) {
    return findOperation(operations, "Replace");
  }

  private static MergeOperations.Operation getRemove(List<MergeOperations.Operation> operations) {
    return findOperation(operations, "Remove");
  }

  private static MergeOperations.Operation findOperation(List<MergeOperations.Operation> operations, String name) {
    for (MergeOperations.Operation operation : operations) {
      if (operation.getName().contains(name)) {
        return operation;
      }
    }
    return null;
  }

  private void moveToOffset1(int offset) {
    getEditor1(myDiffPanel).getCaretModel().moveToOffset(offset);
  }

  private void moveToOffset2(int offset) {
    getEditor2(myDiffPanel).getCaretModel().moveToOffset(offset);
  }
}
