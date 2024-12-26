// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.editor;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class DocumentChangeInactivityDetector implements DocumentListener {
  private static final int CHECK_DELAY = 500; // ms

  private final    ScheduledExecutorService myExecutorService;

  private static final Logger LOG = Logger.getInstance(DocumentChangeInactivityDetector.class);

  private volatile long     myLastChangeTime;
  private volatile long     myLastDocStamp;
  private final    Document myDocument;

  private final List<InactivityListener> myListeners = new ArrayList<>();

  DocumentChangeInactivityDetector(@NotNull Document document) {
    myDocument = document;
    myExecutorService = AppExecutorUtil.createBoundedScheduledExecutorService("DocumentChangeInactivityDetector", 1);
  }

  void addListener(@NotNull InactivityListener listener) {
    myListeners.add(listener);
  }

  void removeListener(@NotNull InactivityListener listener) {
    myListeners.remove(listener);
  }

  void start() {
    myLastChangeTime = System.currentTimeMillis();
    myExecutorService.scheduleWithFixedDelay(() -> checkLastUpdate(), CHECK_DELAY, CHECK_DELAY, TimeUnit.MILLISECONDS);
  }

  void stop() {
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

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    myLastChangeTime = System.currentTimeMillis();
    myLastDocStamp = event.getOldTimeStamp();
  }

  interface InactivityListener {
    void onInactivity();
  }
}
