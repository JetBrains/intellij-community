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
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.RangeMarkerImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;

class DiffRangeMarker extends RangeMarkerImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.DiffRangeMarker");
  private RangeInvalidListener myListener;

  DiffRangeMarker(DocumentEx document, TextRange range, RangeInvalidListener listener) {
    super(document, range.getStartOffset(), range.getEndOffset());
    registerInDocument();
    myListener = listener;
    if (myListener != null) InvalidRangeDispatcher.addClient(document);
  }

  @Override
  protected void changedUpdateImpl(DocumentEvent e) {
    super.changedUpdateImpl(e);
    if (!isValid() && myListener != null) InvalidRangeDispatcher.notify(e.getDocument(), myListener);
  }

  public void removeListener(RangeInvalidListener listener) {
    LOG.assertTrue(myListener == listener || myListener == null);
    myListener = null;
    InvalidRangeDispatcher.removeClient(getDocument());
  }

  public interface RangeInvalidListener {
    void onRangeInvalidated();
  }

  private static class InvalidRangeDispatcher extends DocumentAdapter {
    private static final Key<InvalidRangeDispatcher> KEY = Key.create("deferedNotifier");
    private final ArrayList<RangeInvalidListener> myDeferedNotifications = new ArrayList<RangeInvalidListener>();
    private int myClientCount = 0;

    public void documentChanged(DocumentEvent e) {
      if (myDeferedNotifications.isEmpty()) return;
      RangeInvalidListener[] notifications = myDeferedNotifications.toArray(new RangeInvalidListener[myDeferedNotifications.size()]);
      myDeferedNotifications.clear();
      for (RangeInvalidListener notification : notifications) {
        notification.onRangeInvalidated();
      }
    }

    public static void notify(Document document, RangeInvalidListener listener) {
      InvalidRangeDispatcher notifier = document.getUserData(KEY);
      notifier.myDeferedNotifications.add(listener);
    }

    public static void addClient(Document document) {
      InvalidRangeDispatcher notifier = document.getUserData(KEY);
      if (notifier == null) {
        notifier = new InvalidRangeDispatcher();
        document.putUserData(KEY, notifier);
        document.addDocumentListener(notifier);
      }
      notifier.myClientCount++;
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
}
