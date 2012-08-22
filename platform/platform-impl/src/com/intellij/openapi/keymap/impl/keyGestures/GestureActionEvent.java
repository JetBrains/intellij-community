/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.keymap.impl.keyGestures;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionEventVisitor;
import com.intellij.openapi.actionSystem.ActionManager;
import org.jetbrains.annotations.NotNull;

public class GestureActionEvent extends AnActionEvent {
  public GestureActionEvent(KeyboardGestureProcessor processor) {
    super(processor.myContext.actionKey,
          processor.myContext.dataContext,
          processor.myContext.actionPlace,
          processor.myContext.actionPresentation, ActionManager.getInstance(),
          0);
  }

  public static class Init extends GestureActionEvent {
    public Init(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    @Override
    public void accept(@NotNull final AnActionEventVisitor visitor) {
      visitor.visitGestureInitEvent(this);
    }
  }

  public static class PerformAction extends GestureActionEvent {
    public PerformAction(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    @Override
    public void accept(@NotNull final AnActionEventVisitor visitor) {
      visitor.visitGesturePerformedEvent(this);
    }
  }

  public static class Finish extends GestureActionEvent {
    public Finish(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    @Override
    public void accept(@NotNull final AnActionEventVisitor visitor) {
      visitor.visitGestureFinishEvent(this);
    }
  }
}