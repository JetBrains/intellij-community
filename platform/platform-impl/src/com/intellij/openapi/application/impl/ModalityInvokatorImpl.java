/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityInvokator;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

public class ModalityInvokatorImpl implements ModalityInvokator {
  @Override
  public ActionCallback invokeLater(Runnable runnable) {
    return invokeLater(runnable, ApplicationManager.getApplication().getDisposed());
  }

  @Override
  public ActionCallback invokeLater(final Runnable runnable, @NotNull final Condition expired) {
    return LaterInvocator.invokeLater(runnable, expired);
  }

  @Override
  public ActionCallback invokeLater(final Runnable runnable, @NotNull final ModalityState state, @NotNull final Condition expired) {
    return LaterInvocator.invokeLater(runnable, state, expired);
  }

  @Override
  public ActionCallback invokeLater(Runnable runnable, @NotNull ModalityState state) {
    return invokeLater(runnable, state, ApplicationManager.getApplication().getDisposed());
  }  
}