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

/**
 * @author max
 */
public class UsageFilteringRuleProviderImpl implements UsageFilteringRuleProvider {
  private final ReadWriteState myReadWriteState = new ReadWriteState();

  @Override
  @NotNull
  public UsageFilteringRule[] getActiveRules(@NotNull Project project) {
    final List<UsageFilteringRule> rules = new ArrayList<UsageFilteringRule>();

    if (!myReadWriteState.isShowReadAccess()) {
      rules.add(new ReadAccessFilteringRule());
    }
    if (!myReadWriteState.isShowWriteAccess()) {
      rules.add(new WriteAccessFilteringRule());
    }
    return rules.toArray(new UsageFilteringRule[rules.size()]);
  }

  @Override
  @NotNull
  public AnAction[] createFilteringActions(@NotNull UsageView view) {
    final UsageViewImpl impl = (UsageViewImpl)view;
    if (!view.getPresentation().isCodeUsages()) {
      return AnAction.EMPTY_ARRAY;
    }
    final JComponent component = view.getComponent();

    final ShowReadAccessUsagesAction read = new ShowReadAccessUsagesAction();
    read.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK)), component, impl);

    final ShowWriteAccessUsagesAction write = new ShowWriteAccessUsagesAction();
    write.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK)), component, impl);
    return new AnAction[] {read, write};
  }

  private static final class ReadWriteState {
    private boolean myShowReadAccess = true;
    private boolean myShowWriteAccess = true;

    public boolean isShowReadAccess() {
      return myShowReadAccess;
    }

    public void setShowReadAccess(final boolean showReadAccess) {
      myShowReadAccess = showReadAccess;
      if (!showReadAccess) {
        myShowWriteAccess = true;
      }
    }

    public boolean isShowWriteAccess() {
      return myShowWriteAccess;
    }

    public void setShowWriteAccess(final boolean showWriteAccess) {
      myShowWriteAccess = showWriteAccess;
      if (!showWriteAccess) {
        myShowReadAccess = true;
      }
    }
  }

  private class ShowReadAccessUsagesAction extends ToggleAction implements DumbAware {
    private ShowReadAccessUsagesAction() {
      super(UsageViewBundle.message("action.show.read.access"), null, AllIcons.Actions.ShowReadAccess);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myReadWriteState.isShowReadAccess();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myReadWriteState.setShowReadAccess(state);
      Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
      if (project == null) return;
      project.getMessageBus().syncPublisher(RULES_CHANGED).run();
    }
  }

  private class ShowWriteAccessUsagesAction extends ToggleAction implements DumbAware {
    private ShowWriteAccessUsagesAction() {
      super(UsageViewBundle.message("action.show.write.access"), null, AllIcons.Actions.ShowWriteAccess);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myReadWriteState.isShowWriteAccess();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myReadWriteState.setShowWriteAccess(state);
      Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
      if (project == null) return;
      project.getMessageBus().syncPublisher(RULES_CHANGED).run();
    }
  }
}
