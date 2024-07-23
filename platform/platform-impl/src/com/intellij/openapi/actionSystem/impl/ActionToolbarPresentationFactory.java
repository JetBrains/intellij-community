// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

@ApiStatus.Internal
public class ActionToolbarPresentationFactory extends PresentationFactory {
  public static final Key<Integer> ID_KEY = Key.create("internal.ActionToolbarPresentationFactory.id");

  private static final AtomicInteger ourIdCounter = new AtomicInteger();

  private final int myId = ourIdCounter.incrementAndGet();
  private final ActionToolbar myToolbar;

  public ActionToolbarPresentationFactory(@Nullable ActionToolbar toolbar) {
    myToolbar = toolbar;
  }

  @Override
  protected void processPresentation(@NotNull AnAction action, @NotNull Presentation presentation) {
    presentation.putClientProperty(ID_KEY, myId);
  }

  /**
   * Get an integer which allows to distinguish this toolbar factory from any other one.
   * Each {@link Presentation} emitted by this factory knows its ID.
   * @see ActionToolbarPresentationFactory#ID_KEY
   */
  public int getId() {
    return myId;
  }

  @Override
  public void postProcessPresentation(@NotNull AnAction action, @NotNull Presentation presentation) {
    Container parent = myToolbar.getComponent().getParent();
    if (parent instanceof ActionToolbarImpl o &&
        o.getPresentationFactory() instanceof ActionToolbarPresentationFactory oo) {
      oo.postProcessPresentation(action, presentation);
    }
  }
}
