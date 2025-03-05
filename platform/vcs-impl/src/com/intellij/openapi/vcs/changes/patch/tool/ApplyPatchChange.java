// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch.tool;

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.merge.MergeModelBase;
import com.intellij.diff.util.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch.HunkStatus;
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
  private final @NotNull ApplyPatchViewer myViewer;
  private final int myIndex; // index in myModelChanges

  private final @NotNull LineRange myPatchDeletionRange;
  private final @NotNull LineRange myPatchInsertionRange;
  private final @NotNull HunkStatus myStatus;

  private final @Nullable List<DiffFragment> myPatchInnerDifferences;
  private final @NotNull List<DiffGutterOperation> myOperations = new ArrayList<>();

  private final @NotNull List<RangeHighlighter> myHighlighters = new ArrayList<>();

  private boolean myResolved;

  ApplyPatchChange(@NotNull PatchChangeBuilder.AppliedHunk hunk, int index, @NotNull ApplyPatchViewer viewer) {
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

    myHighlighters.addAll(new DiffDrawUtil.LineHighlighterBuilder(editor, startLine, endLine, type)
                            .withResolved(resolved)
                            .done());
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

    RangeHighlighter highlighter = markupModel
      .addRangeHighlighter(null, textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.LAST,
                           HighlighterTargetArea.LINES_IN_RANGE);

    highlighter.setLineMarkerRenderer(new MyGutterRenderer(line1, line2, color, tooltip));

    myHighlighters.add(highlighter);
  }

  private void destroyHighlighters() {
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();

    for (DiffGutterOperation operation : myOperations) {
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

  public @NotNull HunkStatus getStatus() {
    return myStatus;
  }

  public @NotNull LineRange getPatchRange() {
    return new LineRange(myPatchDeletionRange.start, myPatchInsertionRange.end);
  }

  public @NotNull LineRange getPatchAffectedRange() {
    return isRangeApplied() ? myPatchInsertionRange : myPatchDeletionRange;
  }

  public @NotNull LineRange getPatchDeletionRange() {
    return myPatchDeletionRange;
  }

  public @NotNull LineRange getPatchInsertionRange() {
    return myPatchInsertionRange;
  }

  public @Nullable LineRange getResultRange() {
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

  public @NotNull TextDiffType getDiffType() {
    return DiffUtil.getDiffType(!myPatchDeletionRange.isEmpty(), !myPatchInsertionRange.isEmpty());
  }

  public boolean isRangeApplied() {
    return myResolved || getStatus() == HunkStatus.ALREADY_APPLIED;
  }

  private @NotNull @NlsContexts.Tooltip String getStatusText() {
    return VcsBundle.message(switch (myStatus) {
      case ALREADY_APPLIED -> "patch.apply.already.applied.status";
      case EXACTLY_APPLIED -> "patch.apply.automatically.applied.status";
      case NOT_APPLIED -> "patch.apply.not.applied.status";
    });
  }

  private @NotNull Color getStatusColor() {
    return switch (myStatus) {
      case ALREADY_APPLIED -> JBColor.YELLOW.darker();
      case EXACTLY_APPLIED -> new JBColor(new Color(0, 180, 5), new Color(0, 147, 5));
      case NOT_APPLIED -> JBColor.RED.darker();
    };
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
    else {
      ContainerUtil.addIfNotNull(myOperations, createOperation(OperationType.COPY));
    }
    ContainerUtil.addIfNotNull(myOperations, createOperation(OperationType.IGNORE));
  }

  private @Nullable DiffGutterOperation createOperation(@NotNull OperationType type) {
    if (isResolved()) return null;

    EditorEx editor = myViewer.getPatchEditor();
    int line = type == OperationType.COPY ? getPatchInsertionRange().start : getPatchRange().start;
    int offset = DiffGutterOperation.lineToOffset(editor, line);

    return new DiffGutterOperation.Simple(editor, offset, () -> {
      return switch (type) {
        case APPLY -> createApplyRenderer();
        case COPY -> createCopyRenderer();
        case IGNORE -> createIgnoreRenderer();
      };
    });
  }

  private @Nullable GutterIconRenderer createApplyRenderer() {
    return createIconRenderer(DiffBundle.message("action.presentation.diff.accept.text"), DiffUtil.getArrowIcon(Side.RIGHT),
                              () -> myViewer.executeCommand(DiffBundle.message("merge.dialog.accept.change.command"),
                                                            () -> myViewer.replaceChange(this)));
  }

  private @Nullable GutterIconRenderer createCopyRenderer() {
    if (getPatchInsertionRange().isEmpty()) return null;
    return createIconRenderer(DiffBundle.message("action.presentation.diff.copy.text"), AllIcons.Actions.Copy,
                              () -> myViewer.executeCommand(DiffBundle.message("patch.dialog.copy.change.command"),
                                                            () -> myViewer.copyChangeToClipboard(this)));
  }

  private @Nullable GutterIconRenderer createIgnoreRenderer() {
    return createIconRenderer(DiffBundle.message("action.presentation.merge.ignore.text"), AllIcons.Diff.Remove,
                              () -> myViewer.executeCommand(DiffBundle.message("merge.dialog.ignore.change.command"),
                                                            () -> myViewer.markChangeResolved(this)));
  }

  private static @Nullable GutterIconRenderer createIconRenderer(@NotNull @NlsContexts.Tooltip String text,
                                                                 final @NotNull Icon icon,
                                                                 final @NotNull Runnable perform) {
    final String tooltipText = DiffUtil.createTooltipText(text, null);
    return new DiffGutterRenderer(icon, tooltipText) {
      @Override
      protected void handleMouseClick() {
        perform.run();
      }
    };
  }

  private enum OperationType {
    APPLY, COPY, IGNORE
  }

  //
  // State
  //

  public @NotNull State storeState() {
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
    private final @NlsContexts.Tooltip String myTooltip;

    MyGutterRenderer(int line1, int line2, Color color, @NlsContexts.Tooltip String tooltip) {
      myLine1 = line1;
      myLine2 = line2;
      myColor = color;
      myTooltip = tooltip;
    }

    @Override
    public void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r) {
      LineStatusMarkerDrawUtil.paintSimpleRange(g, editor, myLine1, myLine2, myColor);
    }

    @Override
    public String getTooltipText() {
      return myTooltip;
    }

    @Override
    public boolean canDoAction(@NotNull MouseEvent e) {
      return LineStatusMarkerDrawUtil.isInsideMarkerArea(e);
    }

    @Override
    public void doAction(@NotNull Editor editor, @NotNull MouseEvent e) {
      if (getResultRange() != null) {
        e.consume();
        myViewer.scrollToChange(ApplyPatchChange.this, Side.RIGHT, false);
      }
    }

    @Override
    public @NotNull String getAccessibleName() {
      return VcsBundle.message("patch.apply.marker.renderer", getTooltipText());
    }
  }
}
