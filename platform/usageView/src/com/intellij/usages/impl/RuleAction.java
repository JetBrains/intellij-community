/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages.impl;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Supplier;

/**
 * @author Eugene Zhuravlev
 */
abstract class RuleAction extends ToggleAction implements DumbAware {
  protected final UsageViewImpl myView;
  RuleAction(@NotNull UsageView view, @NotNull String text, @NotNull Icon icon) {
    this(view, () -> text, icon);
  }

  RuleAction(@NotNull UsageView view, Supplier<String> text, @NotNull Icon icon) {
    super(text, icon);
    myView = (UsageViewImpl)view;
  }

  protected abstract boolean getOptionValue();

  protected abstract void setOptionValue(boolean value);

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return getOptionValue();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    setOptionValue(state);

    Project project = e.getProject();
    if (project != null) {
      project.getMessageBus().syncPublisher(UsageFilteringRuleProvider.RULES_CHANGED).run();
    }

    myView.select();
  }
}
