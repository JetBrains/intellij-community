// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsActions.ActionText;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to modify the action's text (title), before it's shown in the UI.
 */
@ApiStatus.Internal
public abstract class ActionPresentationDecorator {
  private static final Logger LOG = Logger.getInstance(ActionPresentationDecorator.class);

  private static ActionPresentationDecorator OUR_INSTANCE;

  public abstract @NotNull @ActionText String decorateText(@NotNull AnAction action, @NotNull @ActionText String text);

  public static synchronized void setInstance(@Nullable ActionPresentationDecorator instance) {
    LOG.info("Action presentation decorator is set to " + instance);
    OUR_INSTANCE = instance;
  }

  public static synchronized @Nullable ActionPresentationDecorator getInstance() {
    return OUR_INSTANCE;
  }

  public static synchronized @ActionText String decorateTextIfNeeded(AnAction action, @ActionText String text) {
    return OUR_INSTANCE == null || action == null || text == null ? text : OUR_INSTANCE.decorateText(action, text);
  }
}
