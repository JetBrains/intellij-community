package com.intellij.openapi.editor.ex;


import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface DocumentEx extends Document {
  void stripTrailingSpaces(boolean inChangedLinesOnly);
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


  void removeRangeMarker(@NotNull RangeMarkerEx rangeMarker);
  void addRangeMarker(@NotNull RangeMarkerEx rangeMarker);

  boolean isInBulkUpdate();

  void setInBulkUpdate(boolean value);

  @NotNull
  List<RangeMarker> getGuardedBlocks();
}



