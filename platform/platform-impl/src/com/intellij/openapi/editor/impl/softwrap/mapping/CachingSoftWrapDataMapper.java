/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.TextChangeImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapsStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CachingSoftWrapDataMapper implements SoftWrapAwareDocumentParsingListener, Dumpable {
  private static final Logger LOG = Logger.getInstance(CachingSoftWrapDataMapper.class);

  private final List<SoftWrapImpl> myAffectedByUpdateSoftWraps = new ArrayList<>();
  private final EditorEx myEditor;
  private final SoftWrapsStorage myStorage;

  public CachingSoftWrapDataMapper(@NotNull EditorEx editor, @NotNull SoftWrapsStorage storage)
  {
    myEditor = editor;
    myStorage = storage;
  }

  boolean matchesOldSoftWrap(SoftWrap newSoftWrap, int lengthDiff) {
    return Collections.binarySearch(myAffectedByUpdateSoftWraps, new SoftWrapImpl(new TextChangeImpl(newSoftWrap.getText(),
                                                                                                     newSoftWrap.getStart() - lengthDiff,
                                                                                                     newSoftWrap.getEnd() - lengthDiff),
                                                                                  newSoftWrap.getIndentInColumns(),
                                                                                  newSoftWrap.getIndentInPixels()),
                                    (o1, o2) -> {
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
                                    }) >= 0;
  }

  @Override
  public void recalculationEnds() {
  }

  @Override
  public void onCacheUpdateStart(@NotNull IncrementalCacheUpdateEvent event) {
    int startOffset = event.getStartOffset();

    myAffectedByUpdateSoftWraps.clear();
    myAffectedByUpdateSoftWraps.addAll(myStorage.removeStartingFrom(startOffset + 1));
  }

  @Override
  public void onRecalculationEnd(@NotNull IncrementalCacheUpdateEvent event) {
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
    int softWrappedLinesDiff = myStorage.getNumberOfSoftWrapsInRange(event.getStartOffset() + 1, myEditor.getDocument().getTextLength());
    boolean softWrapsChanged = softWrappedLinesDiff > 0;
    for (int i = 0; i < myAffectedByUpdateSoftWraps.size(); i++) {
      SoftWrapImpl softWrap = myAffectedByUpdateSoftWraps.get(i);
      if (firstIndex < 0) {
        if (softWrap.getStart() > recalcEndOffsetTranslated) {
          firstIndex = i;
          if (lengthDiff == 0) {
            break;
          }
        } else {
          softWrappedLinesDiff--;
          softWrapsChanged = true;
        }
      } 
      if (firstIndex >= 0 && i >= firstIndex) {
        softWrap.advance(lengthDiff);
      }
    }
    if (firstIndex >= 0) {
      List<SoftWrapImpl> updated = myAffectedByUpdateSoftWraps.subList(firstIndex, myAffectedByUpdateSoftWraps.size());
      SoftWrapImpl lastSoftWrap = getLastSoftWrap();
      if (lastSoftWrap != null && lastSoftWrap.getStart() >= updated.get(0).getStart()) {
        LOG.error("Invalid soft wrap recalculation", new Attachment("state.txt", myEditor.getSoftWrapModel().toString()));
      }
      myStorage.addAll(updated);
    }
    myAffectedByUpdateSoftWraps.clear();
    if (softWrapsChanged) {
      myStorage.notifyListenersAboutChange();
    }
  }

  @Nullable
  SoftWrapImpl getLastSoftWrap() {
    List<SoftWrapImpl> softWraps = myStorage.getSoftWraps();
    return softWraps.isEmpty() ? null : softWraps.get(softWraps.size() - 1);
  }
  
  @NotNull
  @Override
  public String dumpState() {
    return "Soft wraps affected by current update: " + myAffectedByUpdateSoftWraps;
  }

  @Override
  public String toString() {
    return dumpState();
  }
}