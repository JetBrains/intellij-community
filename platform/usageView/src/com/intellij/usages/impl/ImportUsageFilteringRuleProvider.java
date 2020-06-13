// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.Project;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.actions.RuleAction;
import com.intellij.usages.rules.ImportFilteringRule;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public final class ImportUsageFilteringRuleProvider implements UsageFilteringRuleProvider {
  @Override
  public UsageFilteringRule @NotNull [] getActiveRules(@NotNull final Project project) {
    final List<UsageFilteringRule> rules = new ArrayList<>();
    if (!ImportFilteringUsageViewSetting.getInstance().SHOW_IMPORTS) {
      rules.addAll(ImportFilteringRule.EP_NAME.getExtensionList());
    }
    return rules.toArray(UsageFilteringRule.EMPTY_ARRAY);
  }

  @Override
  public AnAction @NotNull [] createFilteringActions(@NotNull UsageView view) {
    if (view.getPresentation().isCodeUsages()) {
      JComponent component = view.getComponent();
      UsageViewImpl impl = (UsageViewImpl)view;
      ShowImportsAction showImportsAction = new ShowImportsAction();
      CustomShortcutSet shortcutSet = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK));
      showImportsAction.registerCustomShortcutSet(shortcutSet, component, impl);
      return new AnAction[]{showImportsAction};
    }
    else {
      return AnAction.EMPTY_ARRAY;
    }
  }
}

final class ShowImportsAction extends RuleAction {
  ShowImportsAction() {
    super(UsageViewBundle.messagePointer("action.show.import.statements"), AllIcons.Actions.ShowImportStatements);
  }

  @Override
  protected boolean getOptionValue(AnActionEvent e) {
    return ImportFilteringUsageViewSetting.getInstance().SHOW_IMPORTS;
  }

  @Override
  protected void setOptionValue(AnActionEvent e, boolean value) {
    ImportFilteringUsageViewSetting.getInstance().SHOW_IMPORTS = value;
  }
}
