// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.diff.comparison.ByWord;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.iterables.DiffIterable;
import com.intellij.diff.comparison.iterables.DiffIterableUtil;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.tools.fragmented.LineNumberConvertor;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.TextDiffType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.*;
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
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

final class InlineDiffFromAnnotation implements EditorMouseListener, EditorMouseMotionListener, DocumentListener, Disposable {
  private static final Logger LOG = Logger.getInstance(InlineDiffFromAnnotation.class);

  private final @NotNull EditorEx myEditor;
  private final @NotNull FileAnnotation myFileAnnotation;
  private final @NotNull TextAnnotationPresentation myTextPresentation;
  private final @NotNull FileAnnotation.LineModificationDetailsProvider myProvider;

  private final @NotNull Alarm myAlarm;

  private int myCurrentLine = -1;
  private @Nullable ProgressIndicator myIndicator;
  private @Nullable Disposable myDisposable;
  private final @NotNull List<RangeHighlighter> myHighlighters = new ArrayList<>();

  private InlineDiffFromAnnotation(@NotNull EditorEx editor,
                                   @NotNull FileAnnotation fileAnnotation,
                                   @NotNull TextAnnotationPresentation textPresentation,
                                   @NotNull FileAnnotation.LineModificationDetailsProvider provider,
                                   @NotNull Disposable disposable) {
    myEditor = editor;
    myFileAnnotation = fileAnnotation;
    myTextPresentation = textPresentation;
    myProvider = provider;
    myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable);
    Disposer.register(disposable, this);
  }

  @Override
  public void dispose() {
    removeDiff();
  }

  public static void showDiffOnHover(@NotNull Editor editor,
                                     @NotNull FileAnnotation fileAnnotation,
                                     @NotNull TextAnnotationPresentation textPresentation,
                                     @NotNull Disposable disposable) {
    if (!(editor instanceof EditorEx)) return;
    FileAnnotation.LineModificationDetailsProvider provider = fileAnnotation.getLineModificationDetailsProvider();
    if (provider == null) return;

    InlineDiffFromAnnotation inlineDiffFromAnnotation =
      new InlineDiffFromAnnotation((EditorEx)editor, fileAnnotation, textPresentation, provider, disposable);
    editor.addEditorMouseMotionListener(inlineDiffFromAnnotation, disposable);
    editor.addEditorMouseListener(inlineDiffFromAnnotation, disposable);
    editor.getDocument().addDocumentListener(inlineDiffFromAnnotation, disposable);
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
    if (annotationLine >= 0 && annotationLine < myFileAnnotation.getLineCount()) {
      myCurrentLine = editorLine;
      scheduleUpdateDiff(editorLine, annotationLine);
    }
  }

  @Override
  public void mouseExited(@NotNull EditorMouseEvent event) {
    removeDiff();
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    removeDiff();
  }

  @RequiresEdt
  private void removeDiff() {
    myAlarm.cancelAllRequests();
    if (myIndicator != null) {
      myIndicator.cancel();
      myIndicator = null;
    }
    removeHighlighters();
    myCurrentLine = -1;
  }

  private void removeHighlighters() {
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
      myDisposable = null;
    }
    myHighlighters.forEach(highlighter -> myEditor.getMarkupModel().removeHighlighter(highlighter));
    myHighlighters.clear();
  }

  @RequiresEdt
  private void scheduleUpdateDiff(int editorLine, int annotationLine) {
    myAlarm.addRequest(() -> {
      if (myCurrentLine == editorLine) {
        updateDiff(editorLine, annotationLine);
      }
    }, 100);
  }

  @RequiresEdt
  private void updateDiff(int editorLine, int annotationLine) {
    myIndicator = BackgroundTaskUtil.executeOnPooledThread(this, () -> {
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
      removeHighlighters();
      addHighlighters(editorLine, details);
    }
  }

  private void addHighlighters(int editorLine, @NotNull AnnotatedLineModificationDetails details) {
    Document document = myEditor.getDocument();
    if (editorLine >= DiffUtil.getLineCount(document)) return;

    int lineStartOffset = document.getLineStartOffset(editorLine);
    int lineEndOffset = document.getLineEndOffset(editorLine);
    String currentContent = document.getCharsSequence().subSequence(lineStartOffset, lineEndOffset).toString();

    String contentAfter = details.lineContentAfter;
    List<InnerChange> changes = details.changes;

    myDisposable = Disposer.newDisposable();
    DiffDrawUtil.setupLayeredRendering(myEditor, editorLine, editorLine + 1, DiffDrawUtil.LAYER_PRIORITY_MAX, myDisposable);

    InnerChange onlyItem = ContainerUtil.getOnlyItem(changes);
    if (onlyItem != null && onlyItem.startOffset == 0 && onlyItem.endOffset == contentAfter.length()) {
      TextDiffType diffType = getDiffType(onlyItem.type);
      myHighlighters.addAll(
        new DiffDrawUtil.LineHighlighterBuilder(myEditor, editorLine, editorLine + 1, diffType)
          .withLayerPriority(DiffDrawUtil.LAYER_PRIORITY_MAX)
          .withIgnored(false)
          .withHideStripeMarkers(true)
          .done());
    }
    else {
      myHighlighters.addAll(
        new DiffDrawUtil.LineHighlighterBuilder(myEditor, editorLine, editorLine + 1, TextDiffType.MODIFIED)
          .withLayerPriority(DiffDrawUtil.LAYER_PRIORITY_MAX)
          .withIgnored(true)
          .withHideStripeMarkers(true)
          .done());

      List<InnerChange> currentChanges = adjustChangesToCurrent(currentContent, contentAfter, changes);
      for (InnerChange change : currentChanges) {
        int start = lineStartOffset + change.startOffset;
        int end = lineStartOffset + change.endOffset;
        TextDiffType diffType = getDiffType(change.type);
        LOG.assertTrue(start <= end && end <= lineEndOffset, String.format("Range: [%s, %s), Line range: [%s, %s)",
                                                                           start, end, lineStartOffset, lineEndOffset));
        myHighlighters.addAll(new DiffDrawUtil.InlineHighlighterBuilder(myEditor, start, end, diffType)
                                .withLayerPriority(DiffDrawUtil.LAYER_PRIORITY_MAX)
                                .done());
      }
    }
  }

  /**
   * Handle cases when expected and actual lines do not match. Ex: if whitespaces were ignored while computing annotations.
   */
  private static @NotNull List<InnerChange> adjustChangesToCurrent(@NotNull String currentContent,
                                                          @NotNull String contentAfter,
                                                          @NotNull List<InnerChange> changes) {
    if (contentAfter.equals(currentContent)) return changes;

    List<DiffFragment> delta = ByWord.compare(contentAfter, currentContent, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE);
    DiffIterable iterable = DiffIterableUtil.createFragments(delta, contentAfter.length(), currentContent.length());

    LineNumberConvertor convertor = LineNumberConvertor.fromIterable(iterable);

    return ContainerUtil.map(changes, change -> {
      int start = convertor.convertApproximate(change.startOffset);
      int end = convertor.convertApproximate(change.endOffset);
      return new InnerChange(start, end, change.type);
    });
  }

  private static @NotNull TextDiffType getDiffType(@NotNull InnerChangeType changeType) {
    return switch (changeType) {
      case DELETED -> TextDiffType.DELETED;
      case INSERTED -> TextDiffType.INSERTED;
      case MODIFIED -> TextDiffType.MODIFIED;
    };
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
}