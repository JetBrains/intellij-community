// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author gregsh
 */
@ApiStatus.Internal
public class EditorMousePopupActionGroup extends DefaultActionGroup {

  private final EditorMouseEvent myEvent;
  private final boolean myResetChildPopupFlag;

  public EditorMousePopupActionGroup(@NotNull List<AnAction> actions, @NotNull EditorMouseEvent event) {
    addAll(actions);
    myEvent = event;
    myResetChildPopupFlag = false;
  }

  public EditorMousePopupActionGroup(@NotNull ActionGroup group, @NotNull EditorMouseEvent event) {
    add(group);
    myEvent = event;
    myResetChildPopupFlag = true;
  }

  public @NotNull EditorMouseEvent getEvent() {
    return myEvent;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    AnAction[] children = super.getChildren(e);
    if (e != null && myResetChildPopupFlag) {
      Presentation presentation = e.getUpdateSession().presentation(children[0]);
      presentation.setPopupGroup(false);
    }
    return children;
  }
}