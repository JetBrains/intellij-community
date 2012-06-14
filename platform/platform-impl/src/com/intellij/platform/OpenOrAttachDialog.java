/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.platform;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ClickListener;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.components.JBCheckBox;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

/**
 * @author yole
 */
public class OpenOrAttachDialog extends DialogWrapper {
  private JRadioButton myCurrentWindowButton;
  private JRadioButton myOpenInNewWindowButton;
  private JPanel myMainPanel;
  private JBCheckBox myAddCheckbox;

  private static final String MODE_PROPERTY = "OpenOrAttachDialog.OpenMode";
  private static final String MODE_ATTACH = "attach";
  private static final String MODE_REPLACE = "replace";
  private static final String MODE_NEW = "new";
  private final boolean myHideReplace;

  protected OpenOrAttachDialog(Project project, boolean hideReplace, final String title) {
    super(project);
    myHideReplace = hideReplace;
    setTitle(title);
    init();
    ClickListener listener = new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        doOKAction();
        return true;
      }
    };
    listener.installOn(myCurrentWindowButton);
    listener.installOn(myOpenInNewWindowButton);

    final String mode = PropertiesComponent.getInstance().getValue(MODE_PROPERTY);
    if (MODE_NEW.equals(mode)) {
      myOpenInNewWindowButton.setSelected(true);
    }
    else if (mode == null || MODE_REPLACE.equals(mode) || hideReplace) {
      myCurrentWindowButton.setSelected(true);
      myAddCheckbox.setSelected(false);
    }
    else {
      myCurrentWindowButton.setSelected(true);
      myAddCheckbox.setSelected(true);
    }
    if (hideReplace) {
      myAddCheckbox.setVisible(false);
    }
    myAddCheckbox.setEnabled(myCurrentWindowButton.isSelected());
    final ActionListener listener1 = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myAddCheckbox.setEnabled(myCurrentWindowButton.isSelected());
      }
    };
    myCurrentWindowButton.addActionListener(listener1);
    myOpenInNewWindowButton.addActionListener(listener1);
  }
  
  public boolean isReplace() {
    return myCurrentWindowButton.isSelected() && (!myAddCheckbox.isSelected() || myHideReplace);
  }
  
  public boolean isAttach() {
    return myCurrentWindowButton.isSelected() && myAddCheckbox.isSelected() && !myHideReplace;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  protected void doOKAction() {
    String mode = isAttach() ? MODE_ATTACH : isReplace() ? MODE_REPLACE : MODE_NEW;
    PropertiesComponent.getInstance().setValue(MODE_PROPERTY, mode);
    super.doOKAction();
  }
}
