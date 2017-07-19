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
package com.intellij.openapi.vcs.changes.patch.tool;

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.merge.MergeModelBase;
import com.intellij.diff.util.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch.HunkStatus;
import com.intellij.openapi.vcs.ex.LineStatusMarkerRenderer;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

class ApplyPatchChange {
  @NotNull private final ApplyPatchViewer myViewer;
  private final int myIndex; // index in myModelChanges

  @NotNull private final LineRange myPatchDeletionRange;
  @NotNull private final LineRange myPatchInsertionRange;
  @NotNull private final HunkStatus myStatus;

  @Nullable private final List<DiffFragment> myPatchInnerDifferences;
  @NotNull private final List<MyGutterOperation> myOperations = new ArrayList<>();

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<>();

  private boolean myResolved;

  public ApplyPatchChange(@NotNull PatchChangeBuilder.Hunk hunk, int index, @NotNull ApplyPatchViewer viewer) {
    myIndex = index;
    myViewer = viewer;
    myPatchDeletionRange = hunk.getPatchDeletionRange();
    myPatchInsertionRange = hunk.getPatchInsertionRange();
    myStatus = hunk.getStatus();

    DocumentEx patchDocument = viewer.getPatchEditor().getDocument();
    myPatchInnerDifferences = PatchChangeBuilder.computeInnerDifferences(patchDocument, hunk);
  }

  public void reinstallHighlighters() {
    destroyHighlighters();
    installHighlighters();

    myViewer.repaintDivider();
  }

  private void installHighlighters() {
    createResultHighlighters();
    createPatchHighlighters();
    createStatusHighlighter();
    createOperations();
  }

  private void createPatchHighlighters() {
    EditorEx patchEditor = myViewer.getPatchEditor();
    myHighlighters.addAll(DiffDrawUtil.createUnifiedChunkHighlighters(patchEditor, myPatchDeletionRange, myPatchInsertionRange,
                                                                      myPatchInnerDifferences));
  }

  private void createResultHighlighters() {
    LineRange resultRange = getResultRange();
    if (resultRange == null) return;
    EditorEx editor = myViewer.getResultEditor();

    int startLine = resultRange.start;
    int endLine = resultRange.end;

    TextDiffType type = getDiffType();
    boolean resolved = isRangeApplied();

    myHighlighters.addAll(DiffDrawUtil.createHighlighter(editor, startLine, endLine, type, false, resolved, false, false));
  }

  private void createStatusHighlighter() {
    int line1 = myPatchDeletionRange.start;
    int line2 = myPatchInsertionRange.end;

    Color color = getStatusColor();
    if (isResolved()) {
      color = ColorUtil.mix(color, myViewer.getPatchEditor().getGutterComponentEx().getBackground(), 0.6f);
    }

    String tooltip = getStatusText();

    EditorEx patchEditor = myViewer.getPatchEditor();
    Document document = patchEditor.getDocument();
    MarkupModelEx markupModel = patchEditor.getMarkupModel();
    TextRange textRange = DiffUtil.getLinesRange(document, line1, line2);

    RangeHighlighter highlighter = markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(),
                                                                   HighlighterLayer.LAST, null, HighlighterTargetArea.LINES_IN_RANGE);

    highlighter.setLineMarkerRenderer(new MyGutterRenderer(line1, line2, color, tooltip));

    myHighlighters.add(highlighter);
  }

  private void destroyHighlighters() {
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();

    for (MyGutterOperation operation : myOperations) {
      operation.dispose();
    }
    myOperations.clear();
  }

  //
  // Getters
  //

  public int getIndex() {
    return myIndex;
  }

  @NotNull
  public HunkStatus getStatus() {
    return myStatus;
  }

  @NotNull
  public LineRange getPatchRange() {
    return new LineRange(myPatchDeletionRange.start, myPatchInsertionRange.end);
  }

  @NotNull
  public LineRange getPatchAffectedRange() {
    return isRangeApplied() ? myPatchInsertionRange : myPatchDeletionRange;
  }

  @NotNull
  public LineRange getPatchDeletionRange() {
    return myPatchDeletionRange;
  }

  @NotNull
  public LineRange getPatchInsertionRange() {
    return myPatchInsertionRange;
  }

  @Nullable
  public LineRange getResultRange() {
    ApplyPatchViewer.MyModel model = myViewer.getModel();
    int lineStart = model.getLineStart(myIndex);
    int lineEnd = model.getLineEnd(myIndex);

    if (lineStart != -1 || lineEnd != -1) return new LineRange(lineStart, lineEnd);
    return null;
  }

  public boolean isResolved() {
    return myResolved;
  }

  public void setResolved(boolean resolved) {
    myResolved = resolved;
  }

  @NotNull
  public TextDiffType getDiffType() {
    return DiffUtil.getDiffType(!myPatchDeletionRange.isEmpty(), !myPatchInsertionRange.isEmpty());
  }

  public boolean isRangeApplied() {
    return myResolved || getStatus() == HunkStatus.ALREADY_APPLIED;
  }

  @NotNull
  private String getStatusText() {
    switch (myStatus) {
      case ALREADY_APPLIED:
        return "Already applied";
      case EXACTLY_APPLIED:
        return "Automatically applied";
      case NOT_APPLIED:
        return "Not applied";
      default:
        throw new IllegalStateException();
    }
  }

  @NotNull
  private Color getStatusColor() {
    switch (myStatus) {
      case ALREADY_APPLIED:
        return JBColor.YELLOW.darker();
      case EXACTLY_APPLIED:
        return new JBColor(new Color(0, 180, 5), new Color(0, 147, 5));
      case NOT_APPLIED:
        return JBColor.RED.darker();
      default:
        throw new IllegalStateException();
    }
  }

  //
  // Operations
  //

  private void createOperations() {
    if (myViewer.isReadOnly()) return;
    if (isResolved()) return;

    if (myStatus == HunkStatus.EXACTLY_APPLIED) {
      ContainerUtil.addIfNotNull(myOperations, createOperation(OperationType.APPLY));
    }
    ContainerUtil.addIfNotNull(myOperations, createOperation(OperationType.IGNORE));
  }

  @Nullable
  private MyGutterOperation createOperation(@NotNull OperationType type) {
    if (isResolved()) return null;

    EditorEx editor = myViewer.getPatchEditor();
    Document document = editor.getDocument();

    int line = getPatchRange().start;
    int offset = line == DiffUtil.getLineCount(document) ? document.getTextLength() : document.getLineStartOffset(line);

    RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(offset, offset,
                                                                               HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                               null,
                                                                               HighlighterTargetArea.LINES_IN_RANGE);
    return new MyGutterOperation(highlighter, type);
  }

  private class MyGutterOperation {
    @NotNull private final RangeHighlighter myHighlighter;
    @NotNull private final OperationType myType;

    private MyGutterOperation(@NotNull RangeHighlighter highlighter, @NotNull OperationType type) {
      myHighlighter = highlighter;
      myType = type;

      myHighlighter.setGutterIconRenderer(createRenderer());
    }

    public void dispose() {
      myHighlighter.dispose();
    }

    @Nullable
    public GutterIconRenderer createRenderer() {
      switch (myType) {
        case APPLY:
          return createApplyRenderer();
        case IGNORE:
          return createIgnoreRenderer();
        default:
          throw new IllegalArgumentException(myType.name());
      }
    }
  }

  @Nullable
  private GutterIconRenderer createApplyRenderer() {
    return createIconRenderer(DiffBundle.message("merge.dialog.apply.change.action.name"), DiffUtil.getArrowIcon(Side.RIGHT), () -> {
      myViewer.executeCommand("Accept change", () -> {
        myViewer.replaceChange(this);
      });
    });
  }

  @Nullable
  private GutterIconRenderer createIgnoreRenderer() {
    return createIconRenderer(DiffBundle.message("merge.dialog.ignore.change.action.name"), AllIcons.Diff.Remove, () -> {
      myViewer.executeCommand("Ignore change", () -> {
        myViewer.markChangeResolved(this);
      });
    });
  }

  @Nullable
  private static GutterIconRenderer createIconRenderer(@NotNull final String text,
                                                       @NotNull final Icon icon,
                                                       @NotNull final Runnable perform) {
    final String tooltipText = DiffUtil.createTooltipText(text, null);
    return new DiffGutterRenderer(icon, tooltipText) {
      @Override
      protected void performAction(AnActionEvent e) {
        perform.run();
      }
    };
  }

  private enum OperationType {
    APPLY, IGNORE
  }

  //
  // State
  //

  @NotNull
  public State storeState() {
    LineRange resultRange = getResultRange();
    return new State(
      myIndex,
      resultRange != null ? resultRange.start : -1,
      resultRange != null ? resultRange.end : -1,
      myResolved);
  }

  public void restoreState(@NotNull State state) {
    myResolved = state.myResolved;
  }

  public static class State extends MergeModelBase.State {
    private final boolean myResolved;

    public State(int index,
                 int startLine,
                 int endLine,
                 boolean resolved) {
      super(index, startLine, endLine);
      myResolved = resolved;
    }
  }

  private class MyGutterRenderer implements ActiveGutterRenderer {
    private final int myLine1;
    private final int myLine2;
    private final Color myColor;
    private final String myTooltip;

    public MyGutterRenderer(int line1, int line2, Color color, String tooltip) {
      myLine1 = line1;
      myLine2 = line2;
      myColor = color;
      myTooltip = tooltip;
    }

    @Override
    public void paint(Editor editor, Graphics g, Rectangle r) {
      LineStatusMarkerRenderer.paintSimpleRange(g, editor, myLine1, myLine2, myColor);
    }

    @Override
    public String getTooltipText() {
      return myTooltip;
    }

    @Override
    public boolean canDoAction(MouseEvent e) {
      return LineStatusMarkerRenderer.isInsideMarkerArea(e);
    }

    @Override
    public void doAction(Editor editor, MouseEvent e) {
      if (getResultRange() != null) myViewer.scrollToChange(ApplyPatchChange.this, Side.RIGHT, false);
    }
  }
}
