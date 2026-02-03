// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public abstract class ActionShortcutRestrictions {
  public static ActionShortcutRestrictions getInstance() {
    return ApplicationManager.getApplication().getService(ActionShortcutRestrictions.class);
  }

  public abstract @NotNull ShortcutRestrictions getForActionId(String actionId);
}
