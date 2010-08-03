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
package com.intellij.execution.testframework.export;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class ExportTestResultsDialog extends DialogWrapper {
  private final Project myProject;
  private final ExportTestResultsForm myForm;

  public ExportTestResultsDialog(Project project, ExportTestResultsConfiguration config, String defaultFileName) {
    super(project);
    myProject = project;
    String defaultFolder = StringUtil.isNotEmpty(config.getOutputFolder()) ?
                           FileUtil.toSystemDependentName(config.getOutputFolder()) : FileUtil.toSystemDependentName(project.getLocation());
    myForm = new ExportTestResultsForm(config, defaultFileName, defaultFolder);
    myForm.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        revalidate();

      }
    });

    setSize(600, 400);
    setTitle(ExecutionBundle.message("export.test.results.dialog.title"));
    init();

    revalidate();
  }

  @Override
  protected void doOKAction() {
    myForm.apply(ExportTestResultsConfiguration.getInstance(myProject));
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myForm.getPreferredFocusedComponent();
  }

  private void revalidate() {
    String message = myForm.validate();
    myForm.showMessage(message);
    setOKActionEnabled(message == null);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myForm.getContentPane();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "export.test.results";
  }

  @Override
  protected String getHelpId() {
    return "reference.settings.ide.settings.export.test.results";
  }

  public String getFileName() {
    return myForm.getFileName();
  }
}
