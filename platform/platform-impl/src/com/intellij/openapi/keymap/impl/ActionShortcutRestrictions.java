// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public abstract class ActionShortcutRestrictions {
  public static ActionShortcutRestrictions getInstance() {
    return ApplicationManager.getApplication().getService(ActionShortcutRestrictions.class);
  }

  @NotNull
  public abstract ShortcutRestrictions getForActionId(String actionId);
}
