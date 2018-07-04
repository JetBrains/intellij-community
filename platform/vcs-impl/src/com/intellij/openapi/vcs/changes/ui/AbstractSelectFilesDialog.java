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

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * @author yole
 */
public abstract class AbstractSelectFilesDialog<T> extends DialogWrapper {
  protected JCheckBox myDoNotShowCheckbox;
  protected final VcsShowConfirmationOption myConfirmationOption;
  private final String myPrompt;
  private final boolean myShowDoNotAskOption;

  public AbstractSelectFilesDialog(Project project, boolean canBeParent, final VcsShowConfirmationOption confirmationOption,
                                   final String prompt, boolean showDoNotAskOption) {
    super(project, canBeParent);
    myConfirmationOption = confirmationOption;
    myPrompt = prompt;
    myShowDoNotAskOption = showDoNotAskOption;
  }

  @NotNull
  protected abstract ChangesTree getFileList();

  @Nullable
  private JLabel createPromptLabel() {
    if (myPrompt != null) {
      final JLabel label = new JLabel(myPrompt);
      label.setUI(new MultiLineLabelUI());
      label.setBorder(new EmptyBorder(5, 1, 5, 1));
      return label;
    }
    return null;
  }

  @Override
  protected JComponent createNorthPanel() {
    return createPromptLabel();
  }

  protected void doOKAction() {
  if (myDoNotShowCheckbox != null && myDoNotShowCheckbox.isSelected()) {
      myConfirmationOption.setValue(VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);
    }
    super.doOKAction();
  }

  @Override
  public void doCancelAction() {
    if (myDoNotShowCheckbox != null && myDoNotShowCheckbox.isSelected()) {
        myConfirmationOption.setValue(VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY);
      }
    super.doCancelAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return getFileList();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    DefaultActionGroup group = createToolbarActions();
    group.add(Separator.getInstance());
    group.add(ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP));
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("VcsSelectFilesDialog", group, true);

    TreeActionsToolbarPanel toolbarPanel = new TreeActionsToolbarPanel(toolbar, getFileList());

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(toolbarPanel, BorderLayout.NORTH);
    panel.add(ScrollPaneFactory.createScrollPane(getFileList()), BorderLayout.CENTER);

    if (myShowDoNotAskOption) {
      myDoNotShowCheckbox = new JCheckBox(CommonBundle.message("dialog.options.do.not.ask"));
      panel.add(myDoNotShowCheckbox, BorderLayout.SOUTH);
    }
    return panel;
  }

  @NotNull
  protected DefaultActionGroup createToolbarActions() {
    return new DefaultActionGroup();
  }
}
