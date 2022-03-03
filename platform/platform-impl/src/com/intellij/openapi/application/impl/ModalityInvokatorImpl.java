// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityInvokator;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated see {@link ModalityInvokator} notice
 */
@Deprecated
final class ModalityInvokatorImpl implements ModalityInvokator {
  ModalityInvokatorImpl() { }

  @Override
  public @NotNull ActionCallback invokeLater(@NotNull Runnable runnable) {
    return invokeLater(runnable, ApplicationManager.getApplication().getDisposed());
  }

  @Override
  public @NotNull ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull Condition<?> expired) {
    return invokeLater(runnable, ModalityState.defaultModalityState(), expired);
  }

  @Override
  public @NotNull ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state) {
    return invokeLater(runnable, state, ApplicationManager.getApplication().getDisposed());
  }

  @Override
  public @NotNull ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition<?> expired) {
    if (expired.value(null)) {
      return ActionCallback.REJECTED;
    }
    ActionCallback callback = new ActionCallback();
    LaterInvocator.invokeLater(state, Conditions.alwaysFalse(), () -> {
      if (!expired.value(null)) {
        runnable.run();
      }
      callback.setDone();
    });
    return callback;
  }
}
