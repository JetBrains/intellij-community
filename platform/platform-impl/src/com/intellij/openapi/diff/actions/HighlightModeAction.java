/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.processing.HighlightMode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class HighlightModeAction extends ComboBoxAction implements DumbAware {
  private final Map<HighlightMode, AnAction> myActions = new HashMap<HighlightMode, AnAction>();
  private static final HighlightMode[] ourActionOrder =
    new HighlightMode[]{HighlightMode.BY_WORD, HighlightMode.BY_LINE, HighlightMode.NO_HIGHLIGHTING};

  public HighlightModeAction() {
    myActions.put(HighlightMode.BY_WORD,
                  new SetHighlightModeAction(DiffBundle.message("diff.acton.highlight.mode.action.by.word"), HighlightMode.BY_WORD));
    myActions.put(HighlightMode.BY_LINE,
                  new SetHighlightModeAction(DiffBundle.message("diff.acton.highlight.mode.action.by.line"), HighlightMode.BY_LINE));
    myActions.put(HighlightMode.NO_HIGHLIGHTING,
                  new SetHighlightModeAction(DiffBundle.message("diff.acton.highlight.mode.action.no.highlighting"),
                                             HighlightMode.NO_HIGHLIGHTING));
  }

  @Override
  public JComponent createCustomComponent(final Presentation presentation) {
    JPanel panel = new JPanel(new BorderLayout());
    final JLabel label = new JLabel(DiffBundle.message("diff.acton.highlight.mode.action.name"));
    label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
    panel.add(label, BorderLayout.WEST);
    panel.add(super.createCustomComponent(presentation), BorderLayout.CENTER);
    return panel;
  }

  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (HighlightMode comparisonPolicy : ourActionOrder) {
      actionGroup.add(myActions.get(comparisonPolicy));
    }
    return actionGroup;
  }

  public void update(AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    DiffPanelEx diffPanel = DiffPanelImpl.fromDataContext(e.getDataContext());
    if (diffPanel != null && diffPanel.getComponent().isDisplayable()) {
      AnAction action = myActions.get(diffPanel.getHighlightMode());
      Presentation templatePresentation = action.getTemplatePresentation();
      presentation.setIcon(templatePresentation.getIcon());
      presentation.setText(templatePresentation.getText());
      presentation.setEnabled(true);
    }
    else {
      presentation.setIcon(null);
      presentation.setText(DiffBundle.message("diff.acton.highlight.mode.not.available.action.name"));
      presentation.setEnabled(false);
    }
  }

  private static class SetHighlightModeAction extends DumbAwareAction {
    private final HighlightMode myHighlightMode;

    public SetHighlightModeAction(String text, HighlightMode mode) {
      super(text);
      myHighlightMode = mode;
    }

    public void actionPerformed(AnActionEvent e) {
      final DiffPanelImpl diffPanel = DiffPanelImpl.fromDataContext(e.getDataContext());
      if (diffPanel != null) {
        diffPanel.setHighlightMode(myHighlightMode);
      }
    }
  }
}
