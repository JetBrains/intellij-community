// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Some utility methods for highlighters which should be rendered on the error stripe.
 */
final class ErrorStripeMarkersModel {
  private final @NotNull EditorImpl myEditor;
  private final List<ErrorStripeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  ErrorStripeMarkersModel(@NotNull EditorImpl editor) {
    myEditor = editor;
  }

  void fireErrorMarkerClicked(@NotNull RangeHighlighter highlighter, @NotNull MouseEvent e) {
    ThreadingAssertions.assertEventDispatchThread();
    ErrorStripeEvent event = new ErrorStripeEvent(myEditor, e, highlighter);
    logMarkerClicked(event);
    myListeners.forEach(listener -> listener.errorMarkerClicked(event));
  }
  void addErrorMarkerClickListener(@NotNull Disposable parent, @NotNull ErrorStripeListener listener) {
    ContainerUtil.add(listener, myListeners, parent);
  }

  static boolean isErrorStripeHighlighter(@NotNull RangeHighlighterEx highlighter, @NotNull EditorImpl editor) {
    return highlighter.getEditorFilter().avaliableIn(editor) &&
           editor.isHighlighterAvailable(highlighter) &&
           highlighter.getErrorStripeMarkColor(editor.getColorsScheme()) != null;
  }

  private void logMarkerClicked(@NotNull ErrorStripeEvent event) {
    Project project = event.getEditor().getProject();
    if (project != null) {
      HighlightInfo info = HighlightInfo.fromRangeHighlighter(event.getHighlighter());
      int severity = info != null ? info.getSeverity().myVal : -1;
      VirtualFile vFile = event.getEditor().getVirtualFile();
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        int totalMarkersInFile = ReadAction.compute(()-> countErrorStripeMarkers());
        FileType fileType = vFile != null && vFile.isValid() ? vFile.getFileType() : null;
        UIEventLogger.ErrorStripeNavigate.log(project, severity, totalMarkersInFile, fileType);
      });
    }
  }

  private int countErrorStripeMarkers() {
    if (myEditor.isDisposed()) {
      return 0;
    }
    return countErrorStripeMarkers(DocumentMarkupModel.forDocument(myEditor.getDocument(), myEditor.getProject(), true))
      + countErrorStripeMarkers(myEditor.getMarkupModel());
  }

  private int countErrorStripeMarkers(@NotNull MarkupModel model) {
    AtomicInteger c = new AtomicInteger();
    try (MarkupIterator<RangeHighlighterEx> iterator =
      ((MarkupModelEx)model).overlappingErrorStripeIterator(0, model.getDocument().getTextLength())) {
      ContainerUtil.process(iterator, __ -> c.getAndIncrement() >= 0);
    }
    return c.get();
  }
}