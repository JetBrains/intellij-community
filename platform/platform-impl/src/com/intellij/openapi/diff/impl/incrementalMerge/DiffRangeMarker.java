/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

class DiffRangeMarker implements RangeMarker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.DiffRangeMarker");
  private final RangeMarker myRangeMarker;
  private RangeInvalidListener myListener;

  DiffRangeMarker(@NotNull Document document, @NotNull TextRange range, RangeInvalidListener listener) {
    myRangeMarker = document.createRangeMarker(range.getStartOffset(), range.getEndOffset());
    myListener = listener;
    if (listener != null) {
      final InvalidRangeDispatcher notifier = InvalidRangeDispatcher.addClient(document);
      document.addDocumentListener(new DocumentAdapter() {
        @Override
        public void beforeDocumentChange(DocumentEvent e) {
          if (myListener != null) {
            notifier.notify(new RangeInvalidListener() {
              @Override
              public void onRangeInvalidated() {
                if (!isValid() && myListener != null) myListener.onRangeInvalidated();
              }
            });
          }
        }
      });
    }
  }

  public void removeListener(RangeInvalidListener listener) {
    LOG.assertTrue(myListener == listener || myListener == null);
    myListener = null;
    InvalidRangeDispatcher.removeClient(getDocument());
  }

  interface RangeInvalidListener {
    void onRangeInvalidated();
  }

  private static class InvalidRangeDispatcher extends DocumentAdapter {
    private static final Key<InvalidRangeDispatcher> KEY = Key.create("deferedNotifier");
    private final ArrayList<RangeInvalidListener> myDeferedNotifications = new ArrayList<RangeInvalidListener>();
    private int myClientCount = 0;

    @Override
    public void documentChanged(DocumentEvent e) {
      if (myDeferedNotifications.isEmpty()) return;
      RangeInvalidListener[] notifications = myDeferedNotifications.toArray(new RangeInvalidListener[myDeferedNotifications.size()]);
      myDeferedNotifications.clear();
      for (RangeInvalidListener notification : notifications) {
        notification.onRangeInvalidated();
      }
    }

    public void notify(@NotNull RangeInvalidListener listener) {
      myDeferedNotifications.add(listener);
    }

    private static InvalidRangeDispatcher addClient(@NotNull Document document) {
      InvalidRangeDispatcher notifier = document.getUserData(KEY);
      if (notifier == null) {
        notifier = new InvalidRangeDispatcher();
        document.putUserData(KEY, notifier);
        document.addDocumentListener(notifier);
      }
      notifier.myClientCount++;
      return notifier;
    }

    private static void removeClient(Document document) {
      InvalidRangeDispatcher notifier = document.getUserData(KEY);
      notifier.onClientRemoved(document);
    }

    private void onClientRemoved(Document document) {
      myClientCount--;
      LOG.assertTrue(myClientCount >= 0);
      if (myClientCount == 0) {
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
