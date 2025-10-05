// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a storage of sticky lines.
 * In general, sticky lines come from BreadcrumbsProvider by highlighting pass which puts them into the model.
 * On the other hand, the editor gets sticky lines from the model painting them on the sticky lines panel.
 * The current implementation relies on MarkupModel, but that may be changed in the future.
 */
@ApiStatus.Internal
public interface StickyLinesModel {

  static @Nullable StickyLinesModel getModel(@NotNull Project project, @NotNull Document document) {
    return StickyLinesModelImpl.getModel(project, document);
  }

  static @NotNull StickyLinesModel getModel(@NotNull MarkupModel markupModel) {
    return StickyLinesModelImpl.getModel(markupModel);
  }

  default @NotNull StickyLine addStickyLine(int startOffset, int endOffset, @Nullable String debugText) {
    return addStickyLine(SourceID.IJ, startOffset, endOffset, debugText);
  }

  @NotNull StickyLine addStickyLine(@NotNull SourceID source, int startOffset, int endOffset, @Nullable String debugText);

  void removeStickyLine(@NotNull StickyLine stickyLine);

  void processStickyLines(int startOffset, int endOffset, @NotNull Processor<? super @NotNull StickyLine> processor);

  void processStickyLines(@NotNull SourceID source, @NotNull Processor<? super @NotNull StickyLine> processor);

  @NotNull List<@NotNull StickyLine> getAllStickyLines();

  void removeAllStickyLines(@Nullable Project project);

  void addListener(@NotNull Listener listener);

  void removeListener(@NotNull Listener listener);

  void notifyLinesUpdate();

  /**
   * Marker associated with sticky line to distinguish the highlighting daemon which produced the line.
   * Source id allows us to have two separated producers(daemon) of sticky lines.
   * That's needed because it is impossible to implement BreadcrumbsProvider from Rider's side as it does the other clients.
   * In this way, one editor can paint sticky lines from both sources at the same time.
   */
  enum SourceID {
    /**
     * Indicates that the sticky line is produced by IJ's highlighting pass.
     */
    IJ,
    /**
     * Indicates that the sticky line is produced by Rider's highlighting pass.
     */
    RIDER,
  }

  interface Listener {
    /**
     * Called when a batch of sticky lines is added or removed
     */
    void linesUpdated();
  }
}
