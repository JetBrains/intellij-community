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
package org.jetbrains.plugins.groovy.config.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.event.KeyEvent;

public class CreateLibraryDialog extends DialogWrapper {
  private JPanel contentPane;
  private JRadioButton myInProject;
  private JRadioButton myGlobal;

  public CreateLibraryDialog(Project project, final String title, final String inProjectText, final String isGlobalText) {
    super(project, true);
    setModal(true);
    setTitle(title);
    myInProject.setSelected(true);
    myInProject.setMnemonic(KeyEvent.VK_P);
    myGlobal.setMnemonic(KeyEvent.VK_G);

    myInProject.setText(inProjectText);
    myGlobal.setText(isGlobalText);
    init();
  }

  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public boolean isInProject() {
    return myInProject.isSelected();
  }

}
