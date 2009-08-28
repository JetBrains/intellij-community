package com.intellij.openapi.diff.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashMap;

import java.util.Iterator;

public class Rediffers {
  private final HashMap<EditorSource, Rediff> myRediffers = new HashMap<EditorSource, Rediff>();
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
    for (Iterator<Rediff> iterator = myRediffers.values().iterator(); iterator.hasNext();) {
      Rediff rediff = iterator.next();
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
      int newLines = StringUtil.getLineBreakCount(event.getNewFragment());
      int oldLines = StringUtil.getLineBreakCount(event.getOldFragment());
      if (newLines != oldLines) myPanel.invalidateDiff();
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
