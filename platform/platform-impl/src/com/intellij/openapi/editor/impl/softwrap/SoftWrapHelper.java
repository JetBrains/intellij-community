// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CustomWrapModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.SoftWrapModel;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.softwrap.mapping.IncrementalCacheUpdateEvent;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapParsingListener;
import com.intellij.openapi.util.Segment;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.DocumentEventUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Holds utility methods for soft wraps-related processing.
 */
@ApiStatus.Internal
public final class SoftWrapHelper {

  private SoftWrapHelper() {
  }

  @FunctionalInterface
  public interface RangeRecalculationAction {
    void recalculate(int startOffset, int endOffset);
  }

  public static void recalculateSegments(@NotNull @Unmodifiable List<? extends Segment> ranges,
                                         @NotNull SoftWrapNotifier softWrapNotifier,
                                         @NotNull RangeRecalculationAction action) {
    ranges = ContainerUtil.sorted(ranges, (o1, o2) -> {
      int startDiff = o1.getStartOffset() - o2.getStartOffset();
      return startDiff == 0 ? o2.getEndOffset() - o1.getEndOffset() : startDiff;
    });
    final int[] lastRecalculatedOffset = {0};
    SoftWrapParsingListener listener = new SoftWrapParsingListener() {
      @Override
      public void onRegionReparseEnd(@NotNull IncrementalCacheUpdateEvent event) {
        lastRecalculatedOffset[0] = event.getActualEndOffset();
      }
    };
    softWrapNotifier.addSoftWrapParsingListener(listener);
    try {
      for (Segment range : ranges) {
        int lastOffset = lastRecalculatedOffset[0];
        if (range.getEndOffset() > lastOffset) {
          action.recalculate(Math.max(range.getStartOffset(), lastOffset), range.getEndOffset());
        }
      }
    }
    finally {
      softWrapNotifier.removeSoftWrapParsingListener(listener);
    }
  }

  public static int getEndOffsetUpperEstimate(@NotNull Editor editor,
                                              @NotNull Document document,
                                              @NotNull IncrementalCacheUpdateEvent event) {
    int endOffsetUpperEstimate = EditorUtil.getNotFoldedLineEndOffset(editor, event.getMandatoryEndOffset());
    int line = document.getLineNumber(endOffsetUpperEstimate);
    if (line < document.getLineCount() - 1) {
      endOffsetUpperEstimate = document.getLineStartOffset(line + 1);
    }
    return endOffsetUpperEstimate;
  }

  /**
   * Every soft wrap implies that multiple visual positions correspond to the same document offset. We can classify
   * such positions by the following criteria:
   * <pre>
   * <ul>
   *   <li>positions from visual line with soft wrap start;</li>
   *   <li>positions from visual line with soft wrap end;</li>
   * </ul>
   * </pre>
   * <p/>
   * This method allows to answer if caret offset of the given editor points to soft wrap and visual caret position
   * belongs to the visual line where soft wrap end is located.
   *
   * @return          {@code true} if caret offset of the given editor points to visual position that belongs to
   *                  visual line where soft wrap end is located
   */
  public static boolean isCaretAfterSoftWrap(Caret caret) {
    if (!caret.isUpToDate()) {
      return false;
    }
    Editor editor = caret.getEditor();
    SoftWrapModel softWrapModel = editor.getSoftWrapModel();
    int offset = caret.getOffset();
    SoftWrap softWrap = softWrapModel.getSoftWrap(offset);
    if (softWrap == null) {
      return false;
    }

    VisualPosition afterWrapPosition = editor.offsetToVisualPosition(offset, false, false);
    VisualPosition caretPosition = caret.getVisualPosition();
    return caretPosition.line == afterWrapPosition.line && caretPosition.column <= afterWrapPosition.column;
  }

  public static int coerceToValidOffset(int offset, Document document) {
    //noinspection MathClampMigration
    return Math.max(Math.min(offset, document.getTextLength() - 1), 0);
  }

  /**
   * After move-insertion and before move-deletion events,
   * custom wraps in {@link CustomWrapModel} and in {@link SoftWrapsStorage} are temporarily out-of-sync.
   * <p>
   * This method removes custom wraps from the moved-from portion of the text,
   * so that other components observe consistent state between the two events of the moveText operation.
   * <p>
   * Must be called in {@link DocumentListener#beforeDocumentChange}
   */
  public static void removeCustomWrapsFromMoveInsertionSource(CustomWrapModel customWrapModel,
                                                              SoftWrapsStorage storage,
                                                              DocumentEvent event) {
    if (customWrapModel.hasWraps() && DocumentEventUtil.isMoveInsertion(event)) {
      int srcOffset = DocumentEventUtil.getMoveOffsetBeforeInsertion(event);
      storage.removeCustomWrapsInRange(srcOffset, srcOffset + event.getNewLength() + /* end-inclusive */ 1);
    }
  }
}
