// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

/**
 * Takes up the space of one icon in a toolbar.
 *
 * @author Bas Leijdekkers
 */
public class Spacer extends AnAction {

  public Spacer() {
    super(EmptyIcon.ICON_16);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(false);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {}
}
