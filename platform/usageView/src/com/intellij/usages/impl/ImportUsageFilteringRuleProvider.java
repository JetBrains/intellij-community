/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.ImportFilteringRule;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import com.intellij.util.containers.ContainerUtil;
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
      ContainerUtil.addAll(rules, Extensions.getExtensions(ImportFilteringRule.EP_NAME));
    }
    return rules.toArray(new UsageFilteringRule[rules.size()]);
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
