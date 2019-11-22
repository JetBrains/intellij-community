// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.Project;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.ImportFilteringRule;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class ImportUsageFilteringRuleProvider implements UsageFilteringRuleProvider {
  @Override
  @NotNull
  public UsageFilteringRule[] getActiveRules(@NotNull final Project project) {
    final List<UsageFilteringRule> rules = new ArrayList<>();
    if (!ImportFilteringUsageViewSetting.getInstance().SHOW_IMPORTS) {
      rules.addAll(ImportFilteringRule.EP_NAME.getExtensionList());
    }
    return rules.toArray(UsageFilteringRule.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public AnAction[] createFilteringActions(@NotNull final UsageView view) {
    final UsageViewImpl impl = (UsageViewImpl)view;
    if (view.getPresentation().isCodeUsages()) {
      final JComponent component = view.getComponent();
      final ShowImportsAction showImportsAction = new ShowImportsAction(impl);
      showImportsAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK)), component, view);
      return new AnAction[] { showImportsAction };
    }
    else {
      return AnAction.EMPTY_ARRAY;
    }
  }

  private static class ShowImportsAction extends RuleAction {
    private ShowImportsAction(UsageViewImpl view) {
      super(view, UsageViewBundle.message("action.show.import.statements"), AllIcons.Actions.ShowImportStatements);
    }

    @Override
    protected boolean getOptionValue() {
      return ImportFilteringUsageViewSetting.getInstance().SHOW_IMPORTS;
    }

    @Override
    protected void setOptionValue(boolean value) {
      ImportFilteringUsageViewSetting.getInstance().SHOW_IMPORTS = value;
    }
  }
}
