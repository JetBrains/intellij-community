// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.rules.ReadAccessFilteringRule;
import com.intellij.usages.impl.rules.WriteAccessFilteringRule;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class UsageFilteringRuleProviderImpl implements UsageFilteringRuleProvider {
  private final ReadWriteState myReadWriteState = new ReadWriteState();

  @Override
  public UsageFilteringRule @NotNull [] getActiveRules(@NotNull Project project) {
    List<UsageFilteringRule> rules = new ArrayList<>();

    if (!myReadWriteState.isShowReadAccess()) {
      rules.add(new ReadAccessFilteringRule());
    }
    if (!myReadWriteState.isShowWriteAccess()) {
      rules.add(new WriteAccessFilteringRule());
    }
    return rules.toArray(UsageFilteringRule.EMPTY_ARRAY);
  }

  @Override
  public AnAction @NotNull [] createFilteringActions(@NotNull UsageView view) {
    if (!view.getPresentation().isCodeUsages()) {
      return AnAction.EMPTY_ARRAY;
    }
    JComponent component = view.getComponent();

    UsageViewImpl impl = (UsageViewImpl)view;
    ShowReadAccessUsagesAction read = new ShowReadAccessUsagesAction();
    read.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK)), component, impl);

    ShowWriteAccessUsagesAction write = new ShowWriteAccessUsagesAction();
    write.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK)), component, impl);
    return new AnAction[] {read, write};
  }

  private static final class ReadWriteState {
    private boolean myShowReadAccess = true;
    private boolean myShowWriteAccess = true;

    boolean isShowReadAccess() {
      return myShowReadAccess;
    }

    void setShowReadAccess(boolean showReadAccess) {
      myShowReadAccess = showReadAccess;
      if (!showReadAccess) {
        myShowWriteAccess = true;
      }
    }

    boolean isShowWriteAccess() {
      return myShowWriteAccess;
    }

    void setShowWriteAccess(boolean showWriteAccess) {
      myShowWriteAccess = showWriteAccess;
      if (!showWriteAccess) {
        myShowReadAccess = true;
      }
    }
  }

  private final class ShowReadAccessUsagesAction extends ToggleAction implements DumbAware {
    private ShowReadAccessUsagesAction() {
      super(UsageViewBundle.messagePointer("action.show.read.access"),
            UsageViewBundle.messagePointer("action.show.read.access.description"), AllIcons.Actions.ShowReadAccess);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myReadWriteState.isShowReadAccess();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myReadWriteState.setShowReadAccess(state);
      Project project = e.getProject();
      if (project == null) return;
      project.getMessageBus().syncPublisher(RULES_CHANGED).run();
    }
  }

  private final class ShowWriteAccessUsagesAction extends ToggleAction implements DumbAware {
    private ShowWriteAccessUsagesAction() {
      super(UsageViewBundle.messagePointer("action.show.write.access"),
            UsageViewBundle.messagePointer("action.show.write.access.description"), AllIcons.Actions.ShowWriteAccess);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myReadWriteState.isShowWriteAccess();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myReadWriteState.setShowWriteAccess(state);
      Project project = e.getProject();
      if (project == null) return;
      project.getMessageBus().syncPublisher(RULES_CHANGED).run();
    }
  }
}
