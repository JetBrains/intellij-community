// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.TextChangeImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapChangeNotifier;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapEx;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapsStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@ApiStatus.Internal
public final class CachingSoftWrapDataMapper implements SoftWrapParsingListener, Dumpable {
  private static final Logger LOG = Logger.getInstance(CachingSoftWrapDataMapper.class);

  private final List<SoftWrapEx> myAffectedByUpdateSoftWraps = new ArrayList<>();
  private final EditorEx myEditor;
  private final SoftWrapsStorage myStorage;
  private final @NotNull SoftWrapChangeNotifier mySoftWrapChangeNotifier;
  private boolean myPreserveStartOffsetSoftWrap = true;

  private static final Comparator<SoftWrapEx> SOFT_WRAP_COMPARATOR = (o1, o2) -> {
    int offsetDiff = o1.getStart() - o2.getStart();
    if (offsetDiff != 0) {
      return offsetDiff;
    }
    int textDiff = o1.getText().toString().compareTo(o2.getText().toString());
    if (textDiff != 0) {
      return textDiff;
    }
    int colIndentDiff = o1.getIndentInColumns() - o2.getIndentInColumns();
    if (colIndentDiff != 0) {
      return colIndentDiff;
    }
    return o1.getIndentInPixels() - o2.getIndentInPixels();
  };

  public CachingSoftWrapDataMapper(@NotNull EditorEx editor,
                                   @NotNull SoftWrapsStorage storage,
                                   @NotNull SoftWrapChangeNotifier softWrapChangeNotifier)
  {
    myEditor = editor;
    myStorage = storage;
    mySoftWrapChangeNotifier = softWrapChangeNotifier;
  }

  public boolean matchesOldSoftWrap(SoftWrap newSoftWrap, int lengthDiff) {
    // There is never more than one soft-wrap at a single offset.
    var index = Collections.binarySearch(
      myAffectedByUpdateSoftWraps,
      new SoftWrapImpl(new TextChangeImpl(newSoftWrap.getText(),
                                          newSoftWrap.getStart() -
                                          lengthDiff,
                                          newSoftWrap.getEnd() -
                                          lengthDiff),
                       newSoftWrap.getIndentInColumns(),
                       newSoftWrap.getIndentInPixels()),
      SOFT_WRAP_COMPARATOR);
    if (index < 0) {
      return false;
    }
    var wrap = myAffectedByUpdateSoftWraps.get(index);
    // If not, it is a custom-wrap.
    // It is possible that this custom-wrap replaced a matching soft-wrap from a previous run.
    // We don't consider this a match and keep computing,
    // relying on matching with some later soft-wrap.
    return wrap instanceof SoftWrapImpl;
  }

  @Override
  public void onRegionReparseStart(@NotNull IncrementalCacheUpdateEvent event) {
    int startOffset = event.getStartOffset();

    myAffectedByUpdateSoftWraps.clear();
    myAffectedByUpdateSoftWraps.addAll(myStorage.removeStartingFrom(startOffset + (myPreserveStartOffsetSoftWrap ? 1 : 0)));
  }

  @Override
  public void onRegionReparseEnd(@NotNull IncrementalCacheUpdateEvent event) {
    advanceSoftWrapOffsets(event);
  }

  @Override
  public void reset() {
    myAffectedByUpdateSoftWraps.clear();
  }

  /**
   * Determines which soft wraps were not affected by recalculation, and shifts them to their new offsets.
   */
  private void advanceSoftWrapOffsets(@NotNull IncrementalCacheUpdateEvent event) {
    int lengthDiff = event.getLengthDiff();
    int recalcEndOffsetTranslated = event.getActualEndOffset() - lengthDiff;

    int firstIndex = -1;
    boolean softWrapsChanged =
      myStorage.hasSoftWrapsInRange((myPreserveStartOffsetSoftWrap ? 1 : 0), myEditor.getDocument().getTextLength());
    for (int i = 0; i < myAffectedByUpdateSoftWraps.size(); i++) {
      SoftWrapEx softWrap = myAffectedByUpdateSoftWraps.get(i);
      if (firstIndex < 0) {
        if (softWrap.getStart() > recalcEndOffsetTranslated) {
          firstIndex = i;
          if (lengthDiff == 0) {
            break;
          }
        } else {
          softWrapsChanged = true;
        }
      }
      if (firstIndex >= 0 && i >= firstIndex) {
        softWrap.advance(lengthDiff);
      }
    }
    if (firstIndex >= 0) {
      List<SoftWrapEx> updated = myAffectedByUpdateSoftWraps.subList(firstIndex, myAffectedByUpdateSoftWraps.size());
      SoftWrapEx lastSoftWrap = myStorage.getLast();
      if (lastSoftWrap != null && lastSoftWrap.getStart() >= updated.getFirst().getStart()) {
        LOG.error("Invalid soft wrap recalculation", new Attachment("state.txt", myEditor.getSoftWrapModel().toString()));
      }
      myStorage.addAll(updated);
    }
    myAffectedByUpdateSoftWraps.clear();
    if (softWrapsChanged) {
      mySoftWrapChangeNotifier.notifySoftWrapsChanged();
    }
  }

  @Override
  public @NotNull String dumpState() {
    return "Soft wraps affected by current update: " + myAffectedByUpdateSoftWraps;
  }

  @Override
  public String toString() {
    return dumpState();
  }

  /**
   * Soft-wrapping enabled recalculations events start either at a logical line start or a visual line start.
   * In the visual line case, the soft wrap at the start offset serves as an anchor for the reparse and should be preserved.
   * <p>
   * Custom-wrap-only recalculations do not need to measure line width, so they do not ensure start at line start.
   * Instead, the start offset of the event points exactly at the start of the affecting change,
   * so any soft wrap at that offset needs to be reconsidered too by the recalculation.
   */
  public void setPreserveStartOffsetSoftWrap(boolean preserve) {
    myPreserveStartOffsetSoftWrap = preserve;
  }
}