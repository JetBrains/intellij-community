// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionGroupWrapper;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author gregsh
 */
@ApiStatus.Internal
public class EditorMousePopupActionGroup extends ActionGroupWrapper {

  private final EditorMouseEvent myEvent;

  public EditorMousePopupActionGroup(@NotNull List<AnAction> actions, @NotNull EditorMouseEvent event) {
    super(new DefaultActionGroup(actions));
    myEvent = event;
  }

  public EditorMousePopupActionGroup(@NotNull ActionGroup group, @NotNull EditorMouseEvent event) {
    super(group);
    myEvent = event;
  }

  public @NotNull EditorMouseEvent getEvent() {
    return myEvent;
  }
}