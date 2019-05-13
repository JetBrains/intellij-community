/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityInvokator;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
class ModalityInvokatorImpl implements ModalityInvokator {
  ModalityInvokatorImpl() { }

  @NotNull
  @Override
  public ActionCallback invokeLater(@NotNull Runnable runnable) {
    return invokeLater(runnable, ApplicationManager.getApplication().getDisposed());
  }

  @NotNull
  @Override
  public ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull Condition expired) {
    return LaterInvocator.invokeLater(runnable, expired);
  }

  @NotNull
  @Override
  public ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition expired) {
    return LaterInvocator.invokeLater(runnable, state, expired);
  }

  @NotNull
  @Override
  public ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state) {
    return invokeLater(runnable, state, ApplicationManager.getApplication().getDisposed());
  }  
}