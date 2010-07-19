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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * @author yole
 */
public abstract class AbstractSelectFilesDialog<T> extends DialogWrapper {
  protected ChangesTreeList<T> myFileList;
  protected JPanel myPanel;
  protected JCheckBox myDoNotShowCheckbox;
  protected final VcsShowConfirmationOption myConfirmationOption;

  public AbstractSelectFilesDialog(Project project, boolean canBeParent, final VcsShowConfirmationOption confirmationOption,
                                   final String prompt) {
    super(project, canBeParent);
    myConfirmationOption = confirmationOption;

    myPanel = new JPanel(new BorderLayout());

    if (prompt != null) {
      final JLabel label = new JLabel(prompt);
      label.setUI(new MultiLineLabelUI());
      label.setBorder(new EmptyBorder(5, 1, 5, 1));
      myPanel.add(label, BorderLayout.NORTH);
    }

    myDoNotShowCheckbox = new JCheckBox(CommonBundle.message("dialog.options.do.not.show"));
    myPanel.add(myDoNotShowCheckbox, BorderLayout.SOUTH);
  }

  @Override
  protected JComponent createNorthPanel() {
    DefaultActionGroup group = new DefaultActionGroup();
    final AnAction[] actions = myFileList.getTreeActions();
    for(AnAction action: actions) {
      group.add(action);
    }
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    return toolbar.getComponent();
  }

  protected void doOKAction() {
    if (myDoNotShowCheckbox.isSelected()) {
      myConfirmationOption.setValue(VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);
    }
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFileList;
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
