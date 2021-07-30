// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewSettings;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

/**
 * @author Eugene Zhuravlev
 */
@ApiStatus.Internal
public abstract class RuleAction extends ToggleAction implements DumbAware {
  protected RuleAction(@ActionText @NotNull String text, @NotNull Icon icon) {
    this(() -> text, icon);
  }

  protected RuleAction(@NotNull Supplier<@ActionText @NotNull String> text, @NotNull Icon icon) {
    super(text, icon);
  }

  protected abstract boolean getOptionValue(@NotNull AnActionEvent e);

  protected abstract void setOptionValue(@NotNull AnActionEvent e, boolean value);

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return getOptionValue(e);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getUsageViewSettingsOrNull(e) != null);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    setOptionValue(e, state);

    Project project = e.getProject();
    if (project != null) {
      project.getMessageBus().syncPublisher(UsageFilteringRuleProvider.RULES_CHANGED).run();
    }
  }

  protected @NotNull UsageViewSettings getUsageViewSettings(@NotNull AnActionEvent e) {
    UsageViewSettings settings = getUsageViewSettingsOrNull(e);
    return settings != null ? settings : UsageViewSettings.getInstance();
  }

  private static @Nullable UsageViewSettings getUsageViewSettingsOrNull(@NotNull AnActionEvent e) {
    UsageViewSettings settings = e.getData(UsageView.USAGE_VIEW_SETTINGS_KEY);
    if (settings != null) {
      return settings;
    }
    UsageViewImpl plainView = getUsageViewImpl(e);
    if (plainView != null) {
      return plainView.getUsageViewSettings();
    }
    return null;
  }

  protected static @Nullable UsageViewImpl getUsageViewImpl(@NotNull AnActionEvent e) {
    UsageView plainView = e.getData(UsageView.USAGE_VIEW_KEY);
    if (plainView instanceof UsageViewImpl) {
      return (UsageViewImpl)plainView;
    }
    return null;
  }
}
