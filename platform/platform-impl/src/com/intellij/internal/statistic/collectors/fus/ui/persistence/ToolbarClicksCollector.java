// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui.persistence;

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;

/**
 * @author Konstantin Bulenkov
 */
public class ToolbarClicksCollector {

  public static void record(@NotNull AnAction action, String place, @NotNull InputEvent inputEvent, @NotNull DataContext dataContext) {
    AnActionEvent event = AnActionEvent.createFromInputEvent(
      inputEvent, place, null, dataContext, false, true);
    ActionsCollectorImpl.record("toolbar", event.getProject(), action, event, null);
  }
}