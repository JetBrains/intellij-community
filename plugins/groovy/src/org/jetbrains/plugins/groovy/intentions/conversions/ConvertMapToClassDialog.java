/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.ui.RefactoringDialog;

import javax.swing.*;

public class ConvertMapToClassDialog extends RefactoringDialog {
  private JPanel contentPane;
  private PackageNameReferenceEditorCombo myPackageNameReferenceEditorCombo;
  private JButton buttonOK;

  public ConvertMapToClassDialog(Project project) {
    super(project, true);
  }

  @Override
  protected void doAction() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  private void createUIComponents() {
//    myPackageNameReferenceEditorCombo = new PackageNameReferenceEditorCombo("", getProject(), PackageNameReferenceEditorCombo.);
  }
}
