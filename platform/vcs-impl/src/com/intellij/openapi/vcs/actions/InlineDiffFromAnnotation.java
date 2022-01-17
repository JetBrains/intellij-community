// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.diff.comparison.ByWord;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.iterables.DiffIterableUtil;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.Range;
import com.intellij.diff.util.TextDiffType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.AnnotatedLineModificationDetails;
import com.intellij.openapi.vcs.annotate.AnnotatedLineModificationDetails.InnerChange;
import com.intellij.openapi.vcs.annotate.AnnotatedLineModificationDetails.InnerChangeType;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.PeekableIterator;
import com.intellij.util.containers.PeekableIteratorWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

final class InlineDiffFromAnnotation implements EditorMouseListener, EditorMouseMotionListener, Disposable {
  private static final Logger LOG = Logger.getInstance(InlineDiffFromAnnotation.class);

  @NotNull private final EditorEx myEditor;
  @NotNull private final TextAnnotationPresentation myTextPresentation;
  @NotNull private final FileAnnotation.LineModificationDetailsProvider myProvider;

  private int myCurrentLine = -1;
  @Nullable private ProgressIndicator myIndicator;
  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<>();

  private InlineDiffFromAnnotation(@NotNull EditorEx editor,
                                   @NotNull TextAnnotationPresentation textPresentation,
                                   @NotNull FileAnnotation.LineModificationDetailsProvider provider,
                                   @NotNull Disposable disposable) {
    myEditor = editor;
    myTextPresentation = textPresentation;
    myProvider = provider;
    Disposer.register(disposable, this);
  }

  @Override
  public void dispose() {
    removeDiff();
  }

  public static void startShowingDiff(@NotNull Editor editor,
                                      @NotNull FileAnnotation fileAnnotation,
                                      @NotNull TextAnnotationPresentation textPresentation,
                                      @NotNull Disposable disposable) {
    if (!(editor instanceof EditorEx)) return;
    FileAnnotation.LineModificationDetailsProvider provider = fileAnnotation.getLineModificationDetailsProvider();
    if (provider == null) return;

    InlineDiffFromAnnotation inlineDiffFromAnnotation =
      new InlineDiffFromAnnotation((EditorEx)editor, textPresentation, provider, disposable);
    editor.addEditorMouseMotionListener(inlineDiffFromAnnotation);
    editor.addEditorMouseListener(inlineDiffFromAnnotation);
    Disposer.register(disposable, new Disposable() {
      @Override
      public void dispose() {
        editor.removeEditorMouseMotionListener(inlineDiffFromAnnotation);
        editor.removeEditorMouseListener(inlineDiffFromAnnotation);
      }
    });
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent e) {
    if (!mouseIsInsideAnnotationArea(myEditor, e.getMouseEvent())) {
      removeDiff();
      return;
    }
    int editorLine = EditorUtil.yToLogicalLineNoCustomRenderers(myEditor, e.getMouseEvent().getY());
    if (editorLine == myCurrentLine) {
      return;
    }

    removeDiff();

    if (!AnnotateDiffOnHoverToggleAction.isShowDiffOnHover()) return;

    int annotationLine = myTextPresentation.getAnnotationLine(editorLine);
    if (annotationLine >= 0) {
      myCurrentLine = editorLine;
      myIndicator = updateDiff(editorLine, annotationLine);
    }
  }

  @Override
  public void mouseExited(@NotNull EditorMouseEvent event) {
    removeDiff();
  }

  @RequiresEdt
  private void removeDiff() {
    if (myIndicator != null) {
      myIndicator.cancel();
      myIndicator = null;
    }
    myHighlighters.forEach(highlighter -> myEditor.getMarkupModel().removeHighlighter(highlighter));
    myHighlighters.clear();
    myCurrentLine = -1;
  }

  @RequiresEdt
  private @NotNull ProgressIndicator updateDiff(int editorLine, int annotationLine) {
    return BackgroundTaskUtil.executeOnPooledThread(this, () -> {
      try {
        AnnotatedLineModificationDetails details = myProvider.getDetails(annotationLine);
        if (details == null) return;

        ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
        ApplicationManager.getApplication().invokeLater(() -> {
          showDiff(editorLine, details, indicator);
        });
      }
      catch (VcsException e) {
        LOG.warn(e);
      }
    });
  }

  private void showDiff(int editorLine, @NotNull AnnotatedLineModificationDetails details, @Nullable ProgressIndicator indicator) {
    if (indicator != null) indicator.checkCanceled();
    if (editorLine == myCurrentLine) {
      addHighlighters(editorLine, details);
    }
  }

  private void addHighlighters(int editorLine, @NotNull AnnotatedLineModificationDetails details) {
    Document document = myEditor.getDocument();
    int lineStartOffset = document.getLineStartOffset(editorLine);
    int lineEndOffset = document.getLineEndOffset(editorLine);
    String currentContent = document.getCharsSequence().subSequence(lineStartOffset, lineEndOffset).toString();

    String contentAfter = details.lineContentAfter;
    List<InnerChange> changes = details.changes;

    InnerChange onlyItem = ContainerUtil.getOnlyItem(changes);
    if (onlyItem != null && onlyItem.startOffset == 0 && onlyItem.endOffset == contentAfter.length()) {
      TextDiffType diffType = getDiffType(onlyItem.type);
      myHighlighters.addAll(DiffDrawUtil.createHighlighter(myEditor, editorLine, editorLine + 1, diffType, false));
    }
    else {
      myHighlighters.addAll(DiffDrawUtil.createHighlighter(myEditor, editorLine, editorLine + 1, TextDiffType.MODIFIED, true));

      List<InnerChange> currentChanges = adjustChangesToCurrent(currentContent, contentAfter, changes);
      for (InnerChange change : currentChanges) {
        int start = lineStartOffset + change.startOffset;
        int end = lineStartOffset + change.endOffset;
        TextDiffType diffType = getDiffType(change.type);
        LOG.assertTrue(start <= end && end <= lineEndOffset, String.format("Range: [%s, %s), Line range: [%s, %s)",
                                                                           start, end, lineStartOffset, lineEndOffset));
        myHighlighters.addAll(DiffDrawUtil.createInlineHighlighter(myEditor, start, end, diffType));
      }
    }
  }

  /**
   * Handle cases when expected and actual lines do not match. Ex: if whitespaces were ignored while computing annotations.
   */
  @NotNull
  private static List<InnerChange> adjustChangesToCurrent(@NotNull String currentContent,
                                                          @NotNull String contentAfter,
                                                          @NotNull List<InnerChange> changes) {
    if (contentAfter.equals(currentContent)) return changes;

    List<DiffFragment> delta = ByWord.compare(contentAfter, currentContent, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE);
    FairDiffIterable diffIterable =
      DiffIterableUtil.fair(DiffIterableUtil.createFragments(delta, contentAfter.length(), currentContent.length()));
    return new ChangedRangesFixer().execute(changes, diffIterable);
  }

  @NotNull
  private static TextDiffType getDiffType(@NotNull InnerChangeType changeType) {
    switch (changeType) {
      case DELETED:
        return TextDiffType.DELETED;
      case INSERTED:
        return TextDiffType.INSERTED;
      case MODIFIED:
        return TextDiffType.MODIFIED;
      default:
        throw new IllegalArgumentException(changeType.name());
    }
  }

  private static boolean mouseIsInsideAnnotationArea(@NotNull EditorEx editor, @NotNull MouseEvent event) {
    if (event.getSource() != editor.getGutter()) {
      return false;
    }

    EditorGutterComponentEx gutter = editor.getGutterComponentEx();
    int x = event.getX();
    int areaOffset = gutter.getAnnotationsAreaOffset();
    int areaWidth = gutter.getAnnotationsAreaWidth();
    return x >= areaOffset && x < areaOffset + areaWidth;
  }

  /**
   * Algorithm is similar to building ranges for a merge conflict,
   * see {@link com.intellij.diff.comparison.ComparisonMergeUtil.FairMergeBuilder}
   * and {@link com.intellij.openapi.vcs.ex.BulkRangeChangeHandler}.
   * <p>
   * Given
   * - Original line content (before commit) // is not available, replaced with {@link InnerChangeType}.
   * - Modified line content (after commit)
   * - Current line content (local state)
   * <p>
   * innerChanges - difference between Original and Modified
   * diffIterable - difference between Modified and Current
   * newInnerChanges - difference between Original and Current
   * <p>
   * We iterate both lists in 'Modified' order, collecting interleaving blocks of differences. Each block becomes a single InnerChange.
   * [changeShift] indicates how 'Modified' offset is mapped to the 'Current' offset at the start of the current block.
   * [dirtyChangeShift] accumulates offset difference by the current block.
   */
  private static final class ChangedRangesFixer {
    private final List<InnerChange> newInnerChanges = new ArrayList<>();

    private int dirtyStart = -1;
    private int dirtyEnd = -1;
    // whether text range in 'Original' is not empty (to differentiate 'insertion' from 'modification')
    private boolean dirtyNonEmptyOriginal = false;

    private int changeShift = 0;
    private int dirtyChangeShift = 0;

    @NotNull
    public List<InnerChange> execute(@NotNull List<InnerChange> innerChanges,
                                     @NotNull FairDiffIterable diffIterable) {
      PeekableIterator<InnerChange> it1 = new PeekableIteratorWrapper<>(innerChanges.iterator());
      PeekableIterator<Range> it2 = new PeekableIteratorWrapper<>(diffIterable.changes());

      while (it1.hasNext() || it2.hasNext()) {
        if (!it2.hasNext()) {
          handleChange(it1.next());
          continue;
        }
        if (!it1.hasNext()) {
          handleDelta(it2.next());
          continue;
        }

        InnerChange range1 = it1.peek();
        Range range2 = it2.peek();
        if (range1.endOffset <= range2.start1) {
          handleChange(it1.next());
        }
        else {
          handleDelta(it2.next());
        }
      }
      flush(Integer.MAX_VALUE);

      return newInnerChanges;
    }

    private void handleChange(@NotNull InnerChange innerChange) {
      flush(innerChange.startOffset);
      markDirtyRange(innerChange.startOffset, innerChange.endOffset);
      dirtyNonEmptyOriginal |= innerChange.type != InnerChangeType.INSERTED;
    }

    private void handleDelta(@NotNull Range range) {
      flush(range.start1);
      dirtyChangeShift += (range.end2 - range.start2) - (range.end1 - range.start1);
      markDirtyRange(range.start1, range.end1);
      dirtyNonEmptyOriginal |= range.start1 != range.end1;
    }

    private void markDirtyRange(int start, int end) {
      if (dirtyEnd == -1) {
        dirtyStart = start;
        dirtyEnd = end;
      }
      else {
        dirtyEnd = Math.max(dirtyEnd, end);
      }
    }

    private void flush(int nextOffset) {
      if (dirtyEnd == -1) return;
      if (dirtyEnd >= nextOffset) return;

      int blockStart = dirtyStart + changeShift;
      int blockEnd = dirtyEnd + changeShift + dirtyChangeShift;
      InnerChangeType blockType = blockStart == blockEnd
                                  ? InnerChangeType.DELETED
                                  : dirtyNonEmptyOriginal ? InnerChangeType.MODIFIED
                                                          : InnerChangeType.INSERTED;
      newInnerChanges.add(new InnerChange(blockStart, blockEnd, blockType));

      dirtyStart = -1;
      dirtyEnd = -1;
      dirtyNonEmptyOriginal = false;

      changeShift += dirtyChangeShift;
      dirtyChangeShift = 0;
    }
  }
}