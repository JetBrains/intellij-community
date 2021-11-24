// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityInvokator;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

final class ModalityInvokatorImpl implements ModalityInvokator {
  ModalityInvokatorImpl() { }

  @Override
  public @NotNull ActionCallback invokeLater(@NotNull Runnable runnable) {
    return invokeLater(runnable, ApplicationManager.getApplication().getDisposed());
  }

  @Override
  public @NotNull ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull Condition<?> expired) {
    return LaterInvocator.invokeLater(runnable, expired);
  }

  @Override
  public @NotNull ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition<?> expired) {
    return LaterInvocator.invokeLater(state, expired, runnable);
  }

  @Override
  public @NotNull ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state) {
    return invokeLater(runnable, state, ApplicationManager.getApplication().getDisposed());
  }
}