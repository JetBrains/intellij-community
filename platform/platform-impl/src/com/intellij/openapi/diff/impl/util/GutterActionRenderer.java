// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.util;

import com.intellij.codeInsight.daemon.NonHideableIconGutterMark;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Used in TC plugin.
 */
@SuppressWarnings("unused")
public class GutterActionRenderer extends GutterIconRenderer implements DumbAware, NonHideableIconGutterMark {
  private final AnAction myAction;

  public GutterActionRenderer(@NotNull AnAction action) {
    myAction = action;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return myAction.getTemplatePresentation().getIcon();
  }

  @Override
  public AnAction getClickAction() {
    return myAction;
  }

  @Override
  public String getTooltipText() {
    return myAction.getTemplatePresentation().getText();
  }

  @Override
  public boolean isNavigateAction() {
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GutterActionRenderer that = (GutterActionRenderer)o;

    if (!myAction.equals(that.myAction)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myAction.hashCode();
  }
}
