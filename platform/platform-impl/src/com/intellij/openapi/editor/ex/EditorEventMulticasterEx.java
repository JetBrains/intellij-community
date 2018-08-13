// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;

public interface EditorEventMulticasterEx extends EditorEventMulticaster{
  void addErrorStripeListener(@NotNull ErrorStripeListener listener, @NotNull Disposable parentDisposable);

  void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener, @NotNull Disposable parentDisposable);

  void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable parentDisposable);

  void addFocusChangeListener(@NotNull FocusChangeListener listener, @NotNull Disposable parentDisposable);
}
