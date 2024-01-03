// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to modify the action's text (title), before it's shown in the UI.
 */
@ApiStatus.Internal
public abstract class ActionPresentationDecorator {
  private static final Logger LOG = Logger.getInstance(ActionPresentationDecorator.class);

  private static volatile @Nullable ActionPresentationDecorator ourInstance;

  public abstract @NotNull @ActionText String decorateText(@NotNull AnAction action, @NotNull @ActionText String text);

  @RequiresEdt
  public static void setInstance(@Nullable ActionPresentationDecorator decorator) {
    LOG.info("ActionPresentationDecorator is set to " + decorator);
    ourInstance = decorator;
  }

  public static @Nullable ActionPresentationDecorator getInstance() {
    return ourInstance;
  }

  @RequiresEdt
  @Contract("_,!null->!null;_,null->null")
  public static @ActionText String decorateTextIfNeeded(@NotNull AnAction action, @ActionText String text) {
    ActionPresentationDecorator instance = ourInstance;
    return instance == null || text == null ? text : instance.decorateText(action, text);
  }
}
