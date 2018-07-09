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
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.ApplyNonConflicts;
import com.intellij.openapi.diff.impl.splitter.Interval;
import com.intellij.openapi.diff.impl.splitter.LineBlocks;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.Assertion;
import org.jetbrains.annotations.NotNull;

public class MergeFuncTest extends PlatformTestCase {
  private MergeTestUtils myUtils;
  private Document myLeft;
  private Document myBase;
  private Document myRight;
  private Editor myELeft;
  private Editor myEBase;
  private Editor myERight;
  private MergeList myMergeList;
  private final Assertion CHECK = new Assertion();
  private ChangeCounter myCounters;

  public void testNoConflicts() {
    useDocuments("a\nIns1\nb\nccc",
                 "a\nb\nccc",
                 "a\nb\ndddd");
    initMerge();
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[]{MergeTestUtils.ins(2, 5)});
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[]{MergeTestUtils.ins(2, 0), MergeTestUtils.mod(4, 3)});
    MergeTestUtils.Range[] mRight = {MergeTestUtils.mod(4, 4)};
    MergeTestUtils.checkMarkup(myERight, mRight);
    checkCounters(2, 0);
    // Apply Ins1->
    pressApplyActionIcon(myELeft, 0);
    MergeTestUtils.Range[] mLeft = new MergeTestUtils.Range[0];
    MergeTestUtils.checkMarkup(myELeft, mLeft);
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[]{MergeTestUtils.mod(9, 3)});
    MergeTestUtils.checkMarkup(myERight, mRight);
    checkCounters(1, 0);
    assertEquals("a\nIns1\nb\nccc", myBase.getText());
    checkApplyNoConflicts(true);
    // Apply dddd->ccc
    pressApplyActionIcon(myERight, 0);
    MergeTestUtils.checkMarkup(myELeft, mLeft);
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[0]);
    checkCounters(0, 0);
    assertEquals("a\nIns1\nb\ndddd", myBase.getText());
    checkApplyNoConflicts(false);
  }

  private void checkCounters(int changes, int conflicts) {
    ChangeCounter counters = ChangeCounter.getOrCreate(myMergeList);
    if (myCounters == null) myCounters = counters;
    else assertSame(myCounters, counters);
    assertEquals(changes, myCounters.getChangeCounter());
    assertEquals(conflicts, myCounters.getConflictCounter());
  }

  public void testConflictingChange() {
    useDocuments("1\n2\n3\nX\na\nb\nc\nY\nVer1\nVer12\nZ",
                          "X\na\nb\nc\nY\n" +  "Ver12\nVer23\nZ",
                          "X\n"   +   "Y\n"     +     "Ver23\nVer3\nZ");
    initMerge();
    MergeTestUtils.Range leftConf = MergeTestUtils.conf(16, 11);
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[]{MergeTestUtils.ins(0, 6), leftConf});
    MergeTestUtils.Range rightConf = MergeTestUtils.conf(4, 11);
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[]{MergeTestUtils.del(2, 0), rightConf});
    MergeTestUtils.Range baseConf = MergeTestUtils.conf(10, 12);
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[]{MergeTestUtils.ins(0, 0), MergeTestUtils.del(2, 6), baseConf});
    checkCounters(2, 2);

    checkApplyNoConflicts(true);
    runApplyNonConflicts();
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[]{leftConf});
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[]{rightConf});
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[]{baseConf});
    checkApplyNoConflicts(false);
    checkCounters(0, 2);
  }

  public void testApplyMergeThenUndo() {
    String baseText = "X\n1\n2\n3\nY";
    useDocuments("X\nb\nY", baseText, "X\na\nY");
    initMerge();
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[]{MergeTestUtils.conf(2, 2)});
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[]{MergeTestUtils.conf(2, 6)});
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[]{MergeTestUtils.conf(2, 2)});
    Interval[] baseLeftIntervals = getBaseIntervals();
    CHECK.compareAll(new Interval[]{new Interval(1, 3)}, baseLeftIntervals);
    checkApplyNoConflicts(false);

    pressApplyActionIcon(myERight, 0);
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[] { MergeTestUtils.conf(2, 2) });
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[] { MergeTestUtils.conf(4, 0)});
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[0]);

    pressApplyActionIcon(myELeft, 0);
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[0]);
    assertEquals(0, getBaseIntervals().length);
    checkApplyNoConflicts(false);

    undo(myEBase);
    assertEquals(baseText, myBase.getText());
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[0]);
    assertEquals(0, getBaseIntervals().length);
  }

  public void testApplyModifiedDeletedConflict() {
    String baseText = "X\n1\n2\n3\nY";
    useDocuments("X\nY", baseText, "X\na\nY");
    initMerge();
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[]{MergeTestUtils.conf(2, 0)});
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[]{MergeTestUtils.conf(2, 6)});
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[]{MergeTestUtils.conf(2, 2)});
    Interval[] baseLeftIntervals = getBaseIntervals();
    CHECK.compareAll(new Interval[]{new Interval(1, 3)}, baseLeftIntervals);
    checkApplyNoConflicts(false);

    pressApplyActionIcon(myERight, 0);
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[0]);

    assertEquals(0, getBaseIntervals().length);
    checkApplyNoConflicts(false);

    undo(myEBase);
    assertEquals(baseText, myBase.getText());
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[0]);
    assertEquals(0, getBaseIntervals().length);
  }

  public void testInvalidatingChange() {
    useDocuments("X\n1\n2\nY", "X\n1\nIns\n2\nY", "X\n1\n2\nY");
    initMerge();
    MergeTestUtils.Range[] sideConflict = {MergeTestUtils.del(4, 0)};
    MergeTestUtils.checkMarkup(myELeft, sideConflict);
    MergeTestUtils.checkMarkup(myERight, sideConflict);
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[]{MergeTestUtils.del(4, 4)});
    CHECK.singleElement(getBaseIntervals(), new Interval(2, 1));

    removeString(2, 10);
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[0]);
    assertEquals(0, getBaseIntervals().length);
  }

  public void testApplySeveralActions() {
    useDocuments("X\n1\nY\n2\nZ\n3\n4\nU\nW\n",
                 "X\na\nY\nb\nZ\nc\nU\nd\nW\n",
                 "X\na\nY\nB\nZ\nC\nU\nD\nW\n");
    initMerge();
    pressApplyActionIcon(myELeft, 0);
    pressApplyActionIcon(myERight, 2);
    assertEquals("X\n1\nY\nb\nZ\nc\nU\nD\nW\n", myBase.getText());
    pressApplyActionIcon(myERight, 0);
    assertEquals("X\n1\nY\nB\nZ\nc\nU\nD\nW\n", myBase.getText());
    pressApplyActionIcon(myELeft, 0);
    pressApplyActionIcon(myELeft, 0);
    pressApplyActionIcon(myERight, 0);
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[0]);
  }

  public void testIgnoreChangeAction() {
    useDocuments("X\n1\nY\n2\nZ", "X\na\nY\nb\nZ", "X\na\nY\nB\nZ");
    initMerge();
    pressIgnoreActionIcon(myELeft, 0);
    assertEquals("X\na\nY\nb\nZ", myBase.getText());
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[]{MergeTestUtils.conf(6, 2)});
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[]{MergeTestUtils.conf(6, 2)});
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[]{MergeTestUtils.conf(6, 2)});
    pressIgnoreActionIcon(myERight, 0);
    assertEquals("X\na\nY\nb\nZ", myBase.getText());
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[0]);
  }

  public void testLongBase() {
    useDocuments("X\n1\n2\n3\nZ", "X\n1\nb\n3\nd\ne\nf\nZ", "X\na\nb\nc\nZ");
    initMerge();
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[]{MergeTestUtils.conf(2, 6)});
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[]{MergeTestUtils.conf(2, 12)});
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[]{MergeTestUtils.conf(2, 6)});
  }

  public void testReplaceBaseWithBranch() {
    String leftVersion = "a\nX\nb\nc";
    useDocuments(leftVersion, "A\nX\nB\nc", "1\nX\n1\nc");
    initMerge();
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[]{MergeTestUtils.conf(0, 2), MergeTestUtils.conf(4, 2)});
    pressApplyActionIcon(myELeft, 0);
    replaceString(0, myBase.getTextLength(), leftVersion);
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[0]);
  }

  public void testError1() {
    useDocuments("start\n" +
                 "change\n" +
                 " a\n" +
                 " b",
                 "start\n" +
                 "CHANGE\n" +
                 "   a\n" +
                 "   b",
                 "    }\n" +
                 "    return new DiffFragment(notEmptyContent(buffer1), notEmptyContent(buffer2));\n" +
                 "  }");
    initMerge();
  }

  public void testError2() {
    useDocuments("C\nX", "C\n", "C\n");
    initMerge();
    MergeTestUtils.checkMarkup(myERight, new MergeTestUtils.Range[0]);
    MergeTestUtils.checkMarkup(myEBase, new MergeTestUtils.Range[]{MergeTestUtils.ins(2, 0)});
    MergeTestUtils.checkMarkup(myELeft, new MergeTestUtils.Range[]{MergeTestUtils.ins(2, 1)});
  }

  public void testError3() {
    useDocuments("original\nlocal\nlocal\nlocal\noriginal\n",
                 "original\noriginal\noriginal\noriginal\noriginal\n",
                 "original\nremote\nremote\nremote\noriginal\n");
    initMerge();
  }

  public void replaceString(final int start, final int end, final String text) {
    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(myProject, () -> {
      myBase.deleteString(start, end);
      myBase.insertString(start, text);
    }, null, null));
  }

  private Interval[] getBaseIntervals() {
    Interval[] left = getLineBlocks(FragmentSide.SIDE1, MergeList.BASE_SIDE);
    Interval[] right = getLineBlocks(FragmentSide.SIDE2, MergeList.BASE_SIDE);
    CHECK.compareAll(left, right);
    return left;
  }

  private void removeString(final int start, final int end) {
    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(myProject, () -> myBase.deleteString(start, end), null, null));
  }

  private Interval[] getLineBlocks(FragmentSide mergeSide, FragmentSide changeSide) {
    return LineBlocks.fromChanges(myMergeList.getChanges(mergeSide).getChanges()).getIntervals(changeSide);
  }

  private void undo(Editor editor) {
    UndoManager undoManager = UndoManager.getInstance(myProject);
    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    assertTrue(undoManager.isUndoAvailable(textEditor));
    undoManager.undo(textEditor);
  }

  private void useDocuments(String left, String base, String right) {
    myLeft = MergeTestUtils.createRODocument(left);
    myBase = MergeTestUtils.createDocument(base);
    myRight = MergeTestUtils.createRODocument(right);
  }

  private void initMerge() {
    myMergeList = MergeList.create(myProject, myLeft, myBase, myRight);
    Editor[] editors = myUtils.createEditors(new Document[]{myLeft, myBase, myRight});
    myELeft = editors[0];
    myEBase = editors[1];
    myERight = editors[2];
    myMergeList.setMarkups(myELeft, myEBase, myERight);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myUtils = new MergeTestUtils(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    myUtils.tearDown();
    myUtils = null;
    myMergeList = null;
    myCounters = null;
    myLeft = null;
    myBase = null;
    myRight = null;
    myELeft = null;
    myEBase = null;
    myERight = null;
    super.tearDown();
  }

  private static void pressApplyActionIcon(@NotNull Editor editor, int index) {
    safeFindAction(editor, index, "ccept").actionPerformed(null);
  }

  private static AnAction safeFindAction(@NotNull Editor editor, int index, String text) {
    AnAction action = findAction(editor, index, text);
    if (action == null) fail("Action not found: " + index);
    return action;
  }

  public static AnAction findAction(@NotNull Editor editor, int index, String text) {
    RangeHighlighter[] highlighters = editor.getMarkupModel().getAllHighlighters();
    for (RangeHighlighter highlighter : highlighters) {
      if (!highlighter.isValid()) continue;
      GutterIconRenderer iconRenderer = highlighter.getGutterIconRenderer();
      if (iconRenderer == null) continue;
      AnAction action = iconRenderer.getClickAction();
      if (action == null) continue;
      if (!iconRenderer.getTooltipText().contains(text)) continue;
      if (index == 0) {
        return action;
      }
      else {
        index--;
      }
    }
    return null;
  }

  private static void pressIgnoreActionIcon(Editor editor, int index) {
    safeFindAction(editor, index, "gnore").actionPerformed(null);
  }

  @Override
  protected void invokeTestRunnable(@NotNull Runnable runnable) {
    runnable.run();
  }

  private void checkApplyNoConflicts(boolean isEnabled) {
    AnActionEvent event = createApplyEvent();
    new ApplyNonConflicts(null).update(event);
    Presentation presentation = event.getPresentation();
    assertEquals(isEnabled, presentation.isEnabled());
  }

  private AnActionEvent createApplyEvent() {
    return new AnActionEvent(null, SimpleDataContext.getSimpleContext(MergeList.DATA_KEY.getName(), myMergeList), ActionPlaces.UNKNOWN,
                             new Presentation(),
                             ActionManager.getInstance(),
                             0);
  }

  public void runApplyNonConflicts() {
    new ApplyNonConflicts(null).actionPerformed(createApplyEvent());
  }
}
