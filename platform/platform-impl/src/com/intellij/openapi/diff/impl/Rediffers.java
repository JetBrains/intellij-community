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
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashMap;

public class Rediffers {
  private final HashMap<EditorSource, Rediff> myRediffers = new HashMap<>();
  private final DiffPanelImpl myPanel;
  private final Alarm myAlarm = new Alarm();
  private final Runnable myUpdateRequest = new Runnable() {
        public void run() {
          if (myDisposed) return;
          updateNow();
        }
      };
  private boolean myDisposed = false;

  public Rediffers(DiffPanelImpl panel) {
    myPanel = panel;
  }

  public void dispose() {
    for (Rediff rediff : myRediffers.values()) {
      rediff.stopListen();
    }
    myRediffers.clear();
    myAlarm.cancelAllRequests();
    myDisposed = true;
  }

  public void contentRemoved(EditorSource source) {
    Rediff rediff = myRediffers.remove(source);
    if (rediff != null) rediff.stopListen();
  }

  public void contentAdded(final EditorSource source) {
    Editor editor = source.getEditor();
    Rediff rediff = new Rediff(editor.getDocument());
    myRediffers.put(source, rediff);
    rediff.startListen();
    source.addDisposable(new Disposable() {
      public void dispose() {
        contentRemoved(source);
      }
    });
  }

  public void updateNow() {
    myPanel.rediff();
    myAlarm.cancelAllRequests();
  }

  private void requestRediff() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(myUpdateRequest, 300);
  }

  private class Rediff implements DocumentListener, Disposable {
    private final Document myDocument;
    private boolean myLinstening = false;

    public Rediff(Document document) {
      myDocument = document;
    }

    public void beforeDocumentChange(DocumentEvent event) {}

    public void documentChanged(DocumentEvent event) {
      if (event.getOldLength() != event.getNewLength()) myPanel.invalidateDiff();
      requestRediff();
    }

    public void stopListen() {
      if (myLinstening) myDocument.removeDocumentListener(this);
      myLinstening = false;
    }

    public void startListen() {
      if (!myLinstening) myDocument.addDocumentListener(this);
      myLinstening = true;
    }

    public void dispose() {
      stopListen();
    }
  }
}
