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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

/**
 * @author yole
 */
public class OpenOrAttachDialog extends DialogWrapper {
  private JRadioButton myAttachButton;
  private JRadioButton myReplaceButton;
  private JRadioButton myOpenInNewWindowLabel;
  private JPanel myMainPanel;

  protected OpenOrAttachDialog(Project project) {
    super(project);
    setTitle("Open Project");
    myAttachButton.setText("Attach to '" + project.getName() + "' in current window");
    myAttachButton.setMnemonic('A');
    myReplaceButton.setText("Replace '" + project.getName() + "' in current window");
    myReplaceButton.setMnemonic('R');
    init();
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
}
