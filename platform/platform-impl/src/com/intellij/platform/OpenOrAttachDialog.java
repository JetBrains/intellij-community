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

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author yole
 */
public class OpenOrAttachDialog extends DialogWrapper {
  private JRadioButton myAttachButton;
  private JRadioButton myReplaceButton;
  private JRadioButton myOpenInNewWindowButton;
  private JPanel myMainPanel;
  
  private static final String MODE_PROPERTY = "OpenOrAttachDialog.OpenMode";
  private static final String MODE_ATTACH = "attach";
  private static final String MODE_REPLACE = "replace";
  private static final String MODE_NEW = "new";

  protected OpenOrAttachDialog(Project project) {
    super(project);
    setTitle("Open Project");
    myAttachButton.setText("Attach to '" + project.getName() + "' in current window");
    myAttachButton.setMnemonic('A');
    init();
    MouseAdapter listener = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
          doOKAction();
        }
      }
    };
    myAttachButton.addMouseListener(listener);
    myReplaceButton.addMouseListener(listener);
    myOpenInNewWindowButton.addMouseListener(listener);

    final String mode = PropertiesComponent.getInstance().getValue(MODE_PROPERTY);
    if (MODE_NEW.equals(mode)) {
      myOpenInNewWindowButton.setSelected(true);
    }
    else if (MODE_REPLACE.equals(mode)) {
      myReplaceButton.setSelected(true);
    }
    else {
      myAttachButton.setSelected(true);      
    }
  }
  
  public boolean isReplace() {
    return myReplaceButton.isSelected();
  }
  
  public boolean isAttach() {
    return myAttachButton.isSelected();
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
