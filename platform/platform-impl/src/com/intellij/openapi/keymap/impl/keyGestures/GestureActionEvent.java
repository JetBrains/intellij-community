// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.keyGestures;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionEventVisitor;
import org.jetbrains.annotations.NotNull;

public class GestureActionEvent extends AnActionEvent {
  public GestureActionEvent(KeyboardGestureProcessor processor) {
    super(processor.myContext.dataContext,
          processor.myContext.actionPresentation,
          processor.myContext.actionPlace,
          ActionUiKind.NONE,
          processor.myContext.actionKey, 0,
          ActionManager.getInstance());
  }

  public static final class Init extends GestureActionEvent {
    public Init(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    @Override
    public void accept(final @NotNull AnActionEventVisitor visitor) {
      visitor.visitGestureInitEvent(this);
    }
  }

  public static final class PerformAction extends GestureActionEvent {
    public PerformAction(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    @Override
    public void accept(final @NotNull AnActionEventVisitor visitor) {
      visitor.visitGesturePerformedEvent(this);
    }
  }

  public static final class Finish extends GestureActionEvent {
    public Finish(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    @Override
    public void accept(final @NotNull AnActionEventVisitor visitor) {
      visitor.visitGestureFinishEvent(this);
    }
  }
}