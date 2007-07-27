package org.jetbrains.idea.eclipse.action;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.ProjectImportWizardStep;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class EclipseWorkspaceRootStep extends ProjectImportWizardStep {

  private JPanel myPanel;
  private JCheckBox myLinkCheckBox;
  private JRadioButton rbModulesColocated;
  private JRadioButton rbModulesDedicated;
  private JTextField myTestSourcesMask;
  private TextFieldWithBrowseButton myDirComponent;
  private TextFieldWithBrowseButton myWorkspaceRootComponent;

  private EclipseProjectWizardContext myContext;
  private EclipseImportBuilder.Parameters myParameters;

  public EclipseWorkspaceRootStep(final WizardContext context) {
    super(context);
    myWorkspaceRootComponent.addBrowseFolderListener(EclipseBundle.message("eclipse.import.label.select.workspace"), "", null,
                                                     new FileChooserDescriptor(false, true, false, false, false, false));

    myDirComponent.addBrowseFolderListener(EclipseBundle.message("eclipse.import.title.module.dir"), "", null,
                                           new FileChooserDescriptor(false, true, false, false, false, false));

    ActionListener listener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean dedicated = rbModulesDedicated.isSelected();
        myDirComponent.setEnabled(dedicated);
        if (dedicated && myDirComponent.getText().length() == 0) {
          myDirComponent.setText(FileUtil.toSystemDependentName(myWorkspaceRootComponent.getText()));
        }
      }
    };

    rbModulesColocated.addActionListener(listener);
    rbModulesDedicated.addActionListener(listener);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public boolean validate() throws ConfigurationException {
    return super.validate() && getContext().setRootDirectory(myWorkspaceRootComponent.getText());
  }

  public void updateDataModel() {
    final String projectFilesDir = myDirComponent.isEnabled() ? myDirComponent.getText() : null;
    suggestProjectNameAndPath(projectFilesDir, myWorkspaceRootComponent.getText());
    getParameters().converterOptions.commonModulesDirectory = projectFilesDir;
    getParameters().converterOptions.testPattern = wildcardToRegexp(myTestSourcesMask.getText());
    getParameters().linkConverted = myLinkCheckBox.isSelected();
  }

  public void updateStep() {
    String path = getContext().getRootDirectory();
    if (path == null) {
      path = getWizardContext().getProjectFileDirectory();
    }
    myWorkspaceRootComponent.setText(path.replace('/', File.separatorChar));
    myWorkspaceRootComponent.getTextField().selectAll();

    final boolean colocated = StringUtil.isEmptyOrSpaces(getParameters().converterOptions.commonModulesDirectory);
    rbModulesColocated.setSelected(colocated);
    rbModulesDedicated.setSelected(!colocated);
    myDirComponent.setEnabled(!colocated);
    myDirComponent.setText(getParameters().converterOptions.commonModulesDirectory);

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
    if (myContext == null) {
      myContext = (EclipseProjectWizardContext)getBuilder();
    }
    return myContext;
  }

  public EclipseImportBuilder.Parameters getParameters() {
    if (myParameters == null) {
      myParameters = ((EclipseImportBuilder)getBuilder()).getParameters();
    }
    return myParameters;
  }
}
