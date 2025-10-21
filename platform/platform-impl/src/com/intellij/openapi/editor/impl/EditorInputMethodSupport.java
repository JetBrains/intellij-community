// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import org.jetbrains.annotations.NotNull;

import java.awt.event.InputMethodListener;
import java.awt.im.InputMethodRequests;

public final class EditorInputMethodSupport {

  private final InputMethodRequests myRequests;
  private final InputMethodListener myListener;
  private final EditorInputMethodHandleSwingThreadWrapper myInputMethodRequestsSwingWrapper;

  public EditorInputMethodSupport(@NotNull InputMethodRequests requests, @NotNull InputMethodListener listener) {
    myRequests = requests;
    myListener = listener;
    myInputMethodRequestsSwingWrapper = new EditorInputMethodHandleSwingThreadWrapper(requests);
  }

  @NotNull InputMethodRequests getRequests() {
    return myRequests;
  }

  @NotNull InputMethodListener getListener() {
    return myListener;
  }

  @NotNull EditorInputMethodHandleSwingThreadWrapper getInputMethodRequestsSwingWrapper() {
    return myInputMethodRequestsSwingWrapper;
  }
}
