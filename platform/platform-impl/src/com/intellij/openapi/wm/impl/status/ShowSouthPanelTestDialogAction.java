/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OptionAction;
import com.intellij.ui.components.panels.Wrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class ShowSouthPanelTestDialogAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    new MyDialogWrapper(e.getProject()).show();
  }

  private static class MyDialogWrapper extends DialogWrapper {
    private final boolean ORIGINAL_ALLOW_MERGE_BUTTONS;

    private final Wrapper mySouthPanel = new Wrapper();

    private final JButton myRefresh = new JButton("Refresh");
    private final JCheckBox myHasOKAction = new JCheckBox("OK action", true);
    private final JCheckBox myHasCancelAction = new JCheckBox("Cancel action", true);
    private final JCheckBox myHasHelpAction = new JCheckBox("Help action", true);
    private final JCheckBox myHasOptionAction = new JCheckBox("Option action", false);
    private final JCheckBox myHasLeftAction = new JCheckBox("Left action", true);
    private final JCheckBox myHasDoNotShowCheckbox = new JCheckBox("'Do not show' checkbox", true);
    private final JCheckBox myAllowMergeButtons = new JCheckBox("Allow merge buttons", false);
    private final JCheckBox myCompact = new JCheckBox("Compact style", false);
    private final JCheckBox myErrorText = new JCheckBox("Error text", false);
    private final JCheckBox myMoveErrorTextToButtons = new JCheckBox("Move error text to the buttons", false);
    private final JCheckBox myCenterButtons = new JCheckBox("Center buttons", false);

    public MyDialogWrapper(Project project) {
      super(project);
      ORIGINAL_ALLOW_MERGE_BUTTONS = UISettings.getShadowInstance().getAllowMergeButtons();
      init();
    }

    @Override
    protected void dispose() {
      UISettings.getShadowInstance().setAllowMergeButtons(ORIGINAL_ALLOW_MERGE_BUTTONS);
      super.dispose();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

      myRefresh.addActionListener(e -> refreshSouthPanel());

      myAllowMergeButtons.setSelected(ORIGINAL_ALLOW_MERGE_BUTTONS);
      myAllowMergeButtons.addActionListener(e -> UISettings.getShadowInstance().setAllowMergeButtons(myAllowMergeButtons.isSelected()));

      myErrorText.addActionListener(e -> setErrorText(myErrorText.isSelected() ? "Error text" : null, myErrorText));
      myCenterButtons.addActionListener(e -> setButtonsAlignment(myCenterButtons.isSelected() ? SwingUtilities.CENTER : SwingUtilities.RIGHT));

      panel.add(myRefresh);
      panel.add(myHasOKAction);
      panel.add(myHasCancelAction);
      panel.add(myHasHelpAction);
      panel.add(myHasOptionAction);
      panel.add(myHasLeftAction);
      panel.add(myHasDoNotShowCheckbox);
      panel.add(myAllowMergeButtons);
      panel.add(myCompact);
      panel.add(myErrorText);
      panel.add(myMoveErrorTextToButtons);
      panel.add(myCenterButtons);

      return panel;
    }

    @Override
    protected JComponent createSouthPanel() {
      refreshSouthPanel();
      return mySouthPanel;
    }

    private void refreshSouthPanel() {
      mySouthPanel.setContent(super.createSouthPanel());
    }

    @Nullable
    @Override
    protected String getHelpId() {
      return "help.id";
    }

    @NotNull
    @Override
    protected Action[] createActions() {
      return new Action[]{
        myHasOKAction.isSelected() ? getOKAction() : null,
        myHasCancelAction.isSelected() ? getCancelAction() : null,
        myHasHelpAction.isSelected() ? getHelpAction() : null,
        myHasOptionAction.isSelected() ? new MyOptionAction() : null,
      };
    }

    @NotNull
    @Override
    protected Action[] createLeftSideActions() {
      return myHasLeftAction.isSelected() ? new Action[]{new MyAction("Left")} : new Action[0];
    }

    @NotNull
    @Override
    protected DialogStyle getStyle() {
      return myCompact.isSelected() ? DialogStyle.COMPACT : DialogStyle.NO_STYLE;
    }

    @Nullable
    @Override
    protected JComponent createDoNotAskCheckbox() {
      return myHasDoNotShowCheckbox.isSelected() ? new JCheckBox("Do not show again") : null;
    }

    @Override
    protected boolean shouldAddErrorNearButtons() {
      return myMoveErrorTextToButtons.isSelected();
    }

    private static class MyOptionAction extends MyAction implements OptionAction {
      private final Action[] myActions;

      public MyOptionAction() {
        super("Option Button");
        myActions = new Action[]{
          new MyAction("Option #1"),
          new MyAction("Option #2"),
          new MyAction("Option #3")
        };
      }

      @NotNull
      @Override
      public Action[] getOptions() {
        return myActions;
      }
    }

    private static class MyAction extends AbstractAction {
      public MyAction(String name) {
        super(name);
      }

      @Override
      public void actionPerformed(ActionEvent e) {
      }
    }
  }
}
