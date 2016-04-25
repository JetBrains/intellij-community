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

import com.intellij.diff.comparison.ByWord;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.tools.util.SyncScrollSupport;
import com.intellij.diff.util.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch;
import com.intellij.openapi.vcs.ex.LineStatusMarkerRenderer;
import com.intellij.ui.JBColor;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

class ApplyPatchChange {
  @NotNull private final ApplyPatchMergeViewer myViewer;

  @NotNull private final LineRange myPatchDeletionRange;
  @NotNull private final LineRange myPatchInsertionRange;
  @NotNull private final AppliedTextPatch.HunkStatus myStatus;

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<>();

  @Nullable private LineRange myAppliedTo;
  private boolean myIsValid = true;

  public ApplyPatchChange(@NotNull ApplyPatchMergeViewer viewer, @NotNull PatchChangeBuilder.Hunk hunk) {
    myViewer = viewer;
    myPatchDeletionRange = hunk.getPatchDeletionRange();
    myPatchInsertionRange = hunk.getPatchInsertionRange();
    myStatus = hunk.getStatus();
    myAppliedTo = hunk.getAppliedToLines();

    installHighlighters();
    installPersistentHighlighters();
  }

  public void reinstallHighlighters() {
    destroyHighlighters();
    installHighlighters();
  }

  private void installHighlighters() {
    createStatusHighlighter(myViewer.getPatchEditor(), myPatchDeletionRange.start, myPatchInsertionRange.end);

    if (myAppliedTo != null) {
      EditorEx resultEditor = myViewer.getResultEditor();
      createStatusHighlighter(resultEditor, myAppliedTo.start, myAppliedTo.end);

      myHighlighters.addAll(DiffDrawUtil.createLineMarker(resultEditor, myAppliedTo.start, myAppliedTo.end,
                                                          TextDiffType.MODIFIED, true));
    }
  }

  private void destroyHighlighters() {
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();
  }

  private void createStatusHighlighter(@NotNull EditorEx editor, int line1, int line2) {
    Color color = getStatusColor();
    String tooltip = getStatusText();

    Document document = editor.getDocument();
    MarkupModelEx markupModel = editor.getMarkupModel();
    TextRange textRange = DiffUtil.getLinesRange(document, line1, line2);

    RangeHighlighter highlighter = markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(),
                                                                   HighlighterLayer.LAST, null, HighlighterTargetArea.LINES_IN_RANGE);

    PairConsumer<Editor, MouseEvent> clickHandler = myAppliedTo != null ? (e, event) -> handleStatusClick(editor, event) : null;
    highlighter.setLineMarkerRenderer(LineStatusMarkerRenderer.createRenderer(line1, line2, color, tooltip, clickHandler));

    myHighlighters.add(highlighter);
  }

  private void handleStatusClick(@NotNull EditorEx editor, @NotNull MouseEvent event) {
    assert myAppliedTo != null;
    EditorEx resultEditor = myViewer.getResultEditor();
    EditorEx patchEditor = myViewer.getPatchEditor();

    Side clickSide = Side.fromLeft(editor == resultEditor);
    int clickLine = clickSide.select(myAppliedTo.start, myPatchInsertionRange.start);
    EditorEx clickEditor = clickSide.select(resultEditor, patchEditor);
    int targetY = clickEditor.logicalPositionToXY(new LogicalPosition(clickLine, 0)).y;
    int topShift = targetY - clickEditor.getScrollingModel().getVerticalScrollOffset();

    int[] offsets = SyncScrollSupport.getTargetOffsets(resultEditor, patchEditor,
                                                       myAppliedTo.start, myAppliedTo.end,
                                                       myPatchInsertionRange.start, myPatchInsertionRange.end,
                                                       topShift);
    DiffUtil.scrollToPoint(resultEditor, new Point(0, offsets[0]), false);
    DiffUtil.scrollToPoint(patchEditor, new Point(0, offsets[1]), false);
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
        return JBColor.BLUE.darker();
      case NOT_APPLIED:
        return JBColor.RED.darker();
      default:
        throw new IllegalStateException();
    }
  }

  private void installPersistentHighlighters() {
    EditorEx patchEditor = myViewer.getPatchEditor();
    DocumentEx patchDocument = patchEditor.getDocument();

    if (myPatchDeletionRange.isEmpty() || myPatchInsertionRange.isEmpty()) {
      DiffDrawUtil.createUnifiedChunkHighlighters(patchEditor, myPatchDeletionRange, myPatchInsertionRange, false);
    }
    else {
      DiffDrawUtil.createUnifiedChunkHighlighters(patchEditor, myPatchDeletionRange, myPatchInsertionRange, true);

      CharSequence deleted = DiffUtil.getLinesContent(patchDocument, myPatchDeletionRange.start, myPatchDeletionRange.end);
      CharSequence inserted = DiffUtil.getLinesContent(patchDocument, myPatchInsertionRange.start, myPatchInsertionRange.end);
      List<DiffFragment> wordDiff = ByWord.compare(deleted, inserted, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE);

      int deletionStartShift = patchDocument.getLineStartOffset(myPatchDeletionRange.start);
      int insertionStartShift = patchDocument.getLineStartOffset(myPatchInsertionRange.start);
      for (DiffFragment fragment : wordDiff) {
        int deletedWordStart = deletionStartShift + fragment.getStartOffset1();
        int deletedWordEnd = deletionStartShift + fragment.getEndOffset1();
        DiffDrawUtil.createInlineHighlighter(patchEditor, deletedWordStart, deletedWordEnd, TextDiffType.DELETED);

        int insertedWordStart = insertionStartShift + fragment.getStartOffset2();
        int insertedWordEndEnd = insertionStartShift + fragment.getEndOffset2();
        DiffDrawUtil.createInlineHighlighter(patchEditor, insertedWordStart, insertedWordEndEnd, TextDiffType.INSERTED);
      }
    }
  }

  //
  // Getters
  //

  @NotNull
  public LineRange getPatchDeletionRange() {
    return myPatchDeletionRange;
  }

  @NotNull
  public LineRange getPatchInsertionRange() {
    return myPatchInsertionRange;
  }

  @NotNull
  public AppliedTextPatch.HunkStatus getStatus() {
    return myStatus;
  }

  @Nullable
  public LineRange getAppliedTo() {
    return myAppliedTo;
  }

  public boolean isValid() {
    return myIsValid;
  }

  //
  // Shift
  //

  public boolean processChange(int oldLine1, int oldLine2, int shift) {
    if (myAppliedTo == null) return false;
    int line1 = myAppliedTo.start;
    int line2 = myAppliedTo.end;

    DiffUtil.UpdatedLineRange newRange = DiffUtil.updateRangeOnModification(line1, line2, oldLine1, oldLine2, shift, true, true);

    myAppliedTo = new LineRange(newRange.startLine, newRange.endLine);
    if (newRange.damaged) myIsValid = false;

    return newRange.damaged || myAppliedTo.start != line1 || myAppliedTo.end != line2;
  }
}
