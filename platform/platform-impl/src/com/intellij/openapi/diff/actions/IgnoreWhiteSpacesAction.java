/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.diff.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class IgnoreWhiteSpacesAction extends ComboBoxAction implements DumbAware {
  private final Map<ComparisonPolicy, AnAction> myActions = new HashMap<ComparisonPolicy, AnAction>();
  private static final ComparisonPolicy[] ourActionOrder = new ComparisonPolicy[]{
    ComparisonPolicy.DEFAULT,
    ComparisonPolicy.TRIM_SPACE,
    ComparisonPolicy.IGNORE_SPACE};

  public IgnoreWhiteSpacesAction() {
    myActions.put(ComparisonPolicy.DEFAULT, new IgnoringPolicyAction(DiffBundle.message("diff.acton.ignore.whitespace.policy.do.not.ignore"), ComparisonPolicy.DEFAULT));
    myActions.put(ComparisonPolicy.TRIM_SPACE, new IgnoringPolicyAction(DiffBundle.message("diff.acton.ignore.whitespace.policy.leading.and.trailing"), ComparisonPolicy.TRIM_SPACE));
    myActions.put(ComparisonPolicy.IGNORE_SPACE, new IgnoringPolicyAction(DiffBundle.message("diff.acton.ignore.whitespace.policy.all"), ComparisonPolicy.IGNORE_SPACE));
  }

  @Override
  public JComponent createCustomComponent(final Presentation presentation) {
    JPanel panel = new JPanel(new BorderLayout());
    final JLabel label = new JLabel(CommonBundle.message("comparison.ignore.whitespace.acton.name"));
    label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
    panel.add(label, BorderLayout.WEST);
    panel.add(super.createCustomComponent(presentation), BorderLayout.CENTER);
    return panel;
  }

  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (ComparisonPolicy comparisonPolicy : ourActionOrder) {
      actionGroup.add(myActions.get(comparisonPolicy));
    }
    return actionGroup;
  }

  public void update(AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    DiffPanelEx diffPanel = DiffPanelImpl.fromDataContext(e.getDataContext());
    if (diffPanel != null && diffPanel.getComponent().isDisplayable()) {
      AnAction action = myActions.get(diffPanel.getComparisonPolicy());
      Presentation templatePresentation = action.getTemplatePresentation();
      presentation.setIcon(templatePresentation.getIcon());
      presentation.setText(templatePresentation.getText());
      presentation.setEnabled(true);
    } else {
      presentation.setIcon(null);
      presentation.setText(DiffBundle.message("ignore.whitespace.action.not.available.action.name"));
      presentation.setEnabled(false);
    }
  }

  private static class IgnoringPolicyAction extends AnAction {
    private final ComparisonPolicy myPolicy;

    public IgnoringPolicyAction(String text, ComparisonPolicy policy) {
      super(text);
      myPolicy = policy;
    }

    public void actionPerformed(AnActionEvent e) {
      final DiffPanelImpl diffPanel = DiffPanelImpl.fromDataContext(e.getDataContext());
      if (diffPanel != null) {
        diffPanel.setComparisonPolicy(myPolicy);
      }
    }
  }
}
