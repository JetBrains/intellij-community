// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;

class DiffRangeMarker implements RangeMarker {
  private final RangeMarker myRangeMarker;

  DiffRangeMarker(@NotNull Document document, @NotNull TextRange range, RangeInvalidListener listener) {
    myRangeMarker = document.createRangeMarker(range.getStartOffset(), range.getEndOffset());
    if (listener != null) {
      InvalidRangeDispatcher.addClient(document, this, listener);
    }
  }

  public void removeListener(@NotNull RangeInvalidListener listener) {
    InvalidRangeDispatcher.removeClient(getDocument(), this, listener);
  }

  interface RangeInvalidListener {
    void onRangeInvalidated();
  }

  private static class InvalidRangeDispatcher implements DocumentListener {
    private static final Key<InvalidRangeDispatcher> KEY = Key.create("deferedNotifier");
    private final Map<DiffRangeMarker, RangeInvalidListener> myDiffRangeMarkers = ContainerUtil.newConcurrentMap();

    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
      for (Iterator<Map.Entry<DiffRangeMarker, RangeInvalidListener>> iterator = myDiffRangeMarkers.entrySet().iterator();
           iterator.hasNext(); ) {
        Map.Entry<DiffRangeMarker, RangeInvalidListener> entry = iterator.next();
        DiffRangeMarker diffRangeMarker = entry.getKey();
        RangeInvalidListener listener = entry.getValue();
        if (!diffRangeMarker.isValid() && listener != null) {
          listener.onRangeInvalidated();
          iterator.remove();
        }
      }
    }

    private static void addClient(@NotNull Document document,
                                  @NotNull DiffRangeMarker marker,
                                  @NotNull RangeInvalidListener listener) {
      InvalidRangeDispatcher notifier = document.getUserData(KEY);
      if (notifier == null) {
        notifier = new InvalidRangeDispatcher();
        document.putUserData(KEY, notifier);
        document.addDocumentListener(notifier);
      }
      assert !notifier.myDiffRangeMarkers.containsKey(marker);
      notifier.myDiffRangeMarkers.put(marker, listener);
    }

    private static void removeClient(@NotNull Document document,
                                     @NotNull DiffRangeMarker marker,
                                     @NotNull RangeInvalidListener listener) {
      InvalidRangeDispatcher notifier = document.getUserData(KEY);
      assert notifier != null;
      notifier.onClientRemoved(document, marker, listener);
    }

    private void onClientRemoved(@NotNull Document document, @NotNull DiffRangeMarker marker, @NotNull RangeInvalidListener listener) {
      if (myDiffRangeMarkers.remove(marker) == listener && myDiffRangeMarkers.isEmpty()) {
        document.putUserData(KEY, null);
        document.removeDocumentListener(this);
      }
    }
  }

  /// delegates

  @Override
  @NotNull
  public Document getDocument() {
    return myRangeMarker.getDocument();
  }

  @Override
  public int getStartOffset() {
    return myRangeMarker.getStartOffset();
  }

  @Override
  public int getEndOffset() {
    return myRangeMarker.getEndOffset();
  }

  @Override
  public boolean isValid() {
    return myRangeMarker.isValid();
  }

  @Override
  public void setGreedyToLeft(boolean greedy) {
    myRangeMarker.setGreedyToLeft(greedy);
  }

  @Override
  public void setGreedyToRight(boolean greedy) {
    myRangeMarker.setGreedyToRight(greedy);
  }

  @Override
  public boolean isGreedyToRight() {
    return myRangeMarker.isGreedyToRight();
  }

  @Override
  public boolean isGreedyToLeft() {
    return myRangeMarker.isGreedyToLeft();
  }

  @Override
  public void dispose() {
    myRangeMarker.dispose();
  }

  @Override
  @Nullable
  public <T> T getUserData(@NotNull Key<T> key) {
    return myRangeMarker.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myRangeMarker.putUserData(key, value);
  }
}
