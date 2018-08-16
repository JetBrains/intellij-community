// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class Rediffers {
  private final HashMap<EditorSource, Rediff> myRediffers = new HashMap<>();
  private final DiffPanelImpl myPanel;
  private final Alarm myAlarm = new Alarm();
  private final Runnable myUpdateRequest = new Runnable() {
        @Override
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
      @Override
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

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
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

    @Override
    public void dispose() {
      stopListen();
    }
  }
}
