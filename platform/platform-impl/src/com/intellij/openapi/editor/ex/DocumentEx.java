/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.editor.ex;


import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface DocumentEx extends Document {
  /**
   * @return true if stripping was completed successfully, false if the document prevented stripping by e.g. caret being in the way
   */
  boolean stripTrailingSpaces(boolean inChangedLinesOnly);
  void setStripTrailingSpacesEnabled(boolean isEnabled);

  @NotNull LineIterator createLineIterator();

  void setModificationStamp(long modificationStamp);

  void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener);

  void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener);

  void replaceText(@NotNull CharSequence chars, long newModificationStamp);

  int getListenersCount();

  void suppressGuardedExceptions();
  void unSuppressGuardedExceptions();

  boolean isInEventsHandling();

  void clearLineModificationFlags();

  boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker);
  void addRangeMarker(@NotNull RangeMarkerEx rangeMarker);

  boolean isInBulkUpdate();

  void setInBulkUpdate(boolean value);

  @NotNull
  List<RangeMarker> getGuardedBlocks();

  boolean processRangeMarkers(@NotNull Processor<RangeMarker> processor);
  boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<RangeMarker> processor);
  boolean processRangeMarkersOverlappingWith(int offset, @NotNull Processor<RangeMarker> processor);
}



