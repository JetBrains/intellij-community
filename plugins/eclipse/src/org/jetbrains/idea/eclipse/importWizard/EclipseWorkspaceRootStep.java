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
package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.ProjectFormatPanel;
import com.intellij.projectImport.ProjectImportWizardStep;
import org.jetbrains.idea.eclipse.EclipseBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class EclipseWorkspaceRootStep extends ProjectImportWizardStep {
  private static final String _ECLIPSE_PROJECT_DIR = "eclipse.project.dir";

  private JPanel myPanel;
  private JCheckBox myLinkCheckBox;
  private JRadioButton rbModulesColocated;
  private JRadioButton rbModulesDedicated;
  private JTextField myTestSourcesMask;
  private TextFieldWithBrowseButton myDirComponent;
  private TextFieldWithBrowseButton myWorkspaceRootComponent;
  private ProjectFormatPanel myProjectFormatPanel;
  private JPanel myFormatPanel;

  private EclipseImportBuilder.Parameters myParameters;


  public EclipseWorkspaceRootStep(final WizardContext context) {
    super(context);
    myWorkspaceRootComponent.addBrowseFolderListener(EclipseBundle.message("eclipse.import.title.select.workspace"), "", null,
                                                     FileChooserDescriptorFactory.createSingleFolderDescriptor());

    myDirComponent.addBrowseFolderListener(EclipseBundle.message("eclipse.import.title.module.dir"), "", null,
                                           FileChooserDescriptorFactory.createSingleFolderDescriptor());

    ActionListener listener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean dedicated = rbModulesDedicated.isSelected();
        myDirComponent.setEnabled(dedicated);
        if (dedicated && myDirComponent.getText().length() == 0) {
          final String remoteStorage = Options.getProjectStorageDir(context.getProject());
          myDirComponent.setText(remoteStorage != null ? remoteStorage : FileUtil.toSystemDependentName(myWorkspaceRootComponent.getText()));
        }
      }
    };

    rbModulesColocated.addActionListener(listener);
    rbModulesDedicated.addActionListener(listener);

    if (context.isCreatingNewProject()) {
      myProjectFormatPanel = new ProjectFormatPanel();
      myFormatPanel.add(myProjectFormatPanel.getPanel(), BorderLayout.WEST);
    }
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public boolean validate() throws ConfigurationException {
    return super.validate() && getContext().setRootDirectory(myWorkspaceRootComponent.getText());
  }

  @Override
  public String getName() {
    return "Eclipse Projects Root";
  }

  public void updateDataModel() {
    final String projectFilesDir;
    if (myDirComponent.isEnabled()) {
      projectFilesDir = myDirComponent.getText();
    }
    else {
      projectFilesDir = null;
    }
    suggestProjectNameAndPath(projectFilesDir, myWorkspaceRootComponent.getText());
    getParameters().converterOptions.commonModulesDirectory = projectFilesDir;
    getParameters().converterOptions.testPattern = wildcardToRegexp(myTestSourcesMask.getText());
    getParameters().linkConverted = myLinkCheckBox.isSelected();
    getParameters().projectsToConvert = null;
    if (getWizardContext().isCreatingNewProject()) {
      myProjectFormatPanel.updateData(getWizardContext());
    }
    PropertiesComponent.getInstance().setValue(_ECLIPSE_PROJECT_DIR, myWorkspaceRootComponent.getText());
    Options.saveProjectStorageDir(getParameters().converterOptions.commonModulesDirectory);
  }

  public void updateStep() {
    String path = getBuilder().getFileToImport();
    if (path == null) {
      if (getWizardContext().isProjectFileDirectorySet() || !PropertiesComponent.getInstance().isValueSet(_ECLIPSE_PROJECT_DIR)) {
        path = getWizardContext().getProjectFileDirectory();
      }
      else {
        path = PropertiesComponent.getInstance().getValue(_ECLIPSE_PROJECT_DIR);
      }
    }
    myWorkspaceRootComponent.setText(path.replace('/', File.separatorChar));
    myWorkspaceRootComponent.getTextField().selectAll();

    final String storageDir = Options.getProjectStorageDir(getWizardContext().getProject());
    final boolean colocated = StringUtil.isEmptyOrSpaces(getParameters().converterOptions.commonModulesDirectory) && StringUtil.isEmptyOrSpaces(storageDir);
    rbModulesColocated.setSelected(colocated);
    rbModulesDedicated.setSelected(!colocated);
    myDirComponent.setEnabled(!colocated);
    if (StringUtil.isEmptyOrSpaces(getParameters().converterOptions.commonModulesDirectory)) {
      myDirComponent.setText(storageDir);
    }
    else {
      myDirComponent.setText(getParameters().converterOptions.commonModulesDirectory);
    }

    myTestSourcesMask.setText(regexpToWildcard(getParameters().converterOptions.testPattern));

    myLinkCheckBox.setSelected(getParameters().linkConverted);
  }

  private static String wildcardToRegexp(String string) {
    return string == null ? null : string.replaceAll("\\.", "\\.") // "." -> "\."
      .replaceAll("\\*", ".*") // "*" -> ".*"
      .replaceAll("\\?", ".") // "?" -> "."
      .replaceAll(",\\s*", "|"); // "," possible followed by whitespace -> "|"
  }

  private static String regexpToWildcard(String string) {
    return string == null ? null : string.replaceAll("\\.\\*", "*") // ".*" -> "*"
      .replaceAll("\\.", "?") // "." -> "?"
      .replaceAll("\\\\\\?", ".") // "\?" -> "."
      .replaceAll("\\|", ", "); // "|" -> ",";
  }

  public JComponent getPreferredFocusedComponent() {
    return myWorkspaceRootComponent.getTextField();
  }

  public String getHelpId() {
    return "reference.dialogs.new.project.import.eclipse.page1";
  }

  public EclipseProjectWizardContext getContext() {
    return (EclipseProjectWizardContext)getBuilder();
  }

  public EclipseImportBuilder.Parameters getParameters() {
    if (myParameters == null) {
      myParameters = ((EclipseImportBuilder)getBuilder()).getParameters();
    }
    return myParameters;
  }
}
