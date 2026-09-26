// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class DocumentChangeInactivityDetector {
  private static final Logger LOG = Logger.getInstance(DocumentChangeInactivityDetector.class);
  private static final int CHECK_DELAY = 500; // ms
  private final ScheduledExecutorService myExecutorService =
    AppExecutorUtil.createBoundedScheduledExecutorService("DocumentChangeInactivityDetector", 1);
  private volatile long myLastChangeTime;
  private volatile long myLastDocStamp;
  private final Document myDocument;
  private final List<InactivityListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  DocumentChangeInactivityDetector(@NotNull Document document, @NotNull Disposable parentDisposable) {
    myDocument = document;
    Disposer.register(parentDisposable, ()->stop());
    document.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        DocumentChangeInactivityDetector.this.documentChanged(event);
      }
    }, parentDisposable);
    start();
  }

  void addListener(@NotNull InactivityListener listener) {
    myListeners.add(listener);
  }

  private void start() {
    myLastChangeTime = System.currentTimeMillis();
    myExecutorService.scheduleWithFixedDelay(() -> checkLastUpdate(), CHECK_DELAY, CHECK_DELAY, TimeUnit.MILLISECONDS);
  }

  private void stop() {
    myListeners.clear();
    myExecutorService.shutdown();
  }

  private void checkLastUpdate() {
    if (System.currentTimeMillis() - myLastChangeTime > CHECK_DELAY && myDocument.getModificationStamp() != myLastDocStamp) {
      myLastDocStamp = myDocument.getModificationStamp();
      for (InactivityListener listener : myListeners) {
        try {
          listener.onInactivity();
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }

  private void documentChanged(@NotNull DocumentEvent event) {
    myLastChangeTime = System.currentTimeMillis();
    myLastDocStamp = event.getOldTimeStamp();
  }

  interface InactivityListener {
    void onInactivity();
  }
}
