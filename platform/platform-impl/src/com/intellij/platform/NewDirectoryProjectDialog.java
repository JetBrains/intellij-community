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
package com.intellij.platform;

import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * @author yole
 */
public class NewDirectoryProjectDialog extends DialogWrapper {
  private JTextField myProjectNameTextField;
  private TextFieldWithBrowseButton myLocationField;
  private JPanel myRootPane;
  private JComboBox myProjectTypeComboBox;
  private JPanel myProjectTypePanel;
  private String myBaseDir;

  private static final Object EMPTY_PROJECT_GENERATOR = new Object();

  protected NewDirectoryProjectDialog(Project project) {
    super(project, true);
    setTitle("Create New Project");
    init();

    myBaseDir = getBaseDir();
    File projectName = FileUtil.findSequentNonexistentFile(new File(myBaseDir), "untitled", "");
    myLocationField.setText(projectName.toString());
    myProjectNameTextField.setText(projectName.getName());

    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> listener =
      new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>("Select Location for Project Directory", "", myLocationField,
                                                                           project,
                                                                           descriptor,
                                                                           TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {

        protected void onFileChoosen(VirtualFile chosenFile) {
          super.onFileChoosen(chosenFile);
          myBaseDir = chosenFile.getPath();
          myLocationField.setText(new File(chosenFile.getPath(), myProjectNameTextField.getText()).toString());
        }
      };
    myLocationField.addActionListener(listener);

    myProjectNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        myLocationField.setText(new File(myBaseDir, myProjectNameTextField.getText()).getPath());
      }
    });

    myProjectNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {

      }
    });
    myProjectNameTextField.selectAll();

    final DirectoryProjectGenerator[] generators = Extensions.getExtensions(DirectoryProjectGenerator.EP_NAME);
    if (generators.length == 0) {
      myProjectTypePanel.setVisible(false);
    }
    else {
      DefaultComboBoxModel model = new DefaultComboBoxModel();
      model.addElement(EMPTY_PROJECT_GENERATOR);
      for (DirectoryProjectGenerator generator : generators) {
        model.addElement(generator);
      }
      myProjectTypeComboBox.setModel(model);
      myProjectTypeComboBox.setRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(final JList list,
                                             final Object value,
                                             final int index,
                                             final boolean selected,
                                             final boolean hasFocus) {
          if (value == null) return;
          if (value == EMPTY_PROJECT_GENERATOR) {
            append("Empty project", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          else {
            DirectoryProjectGenerator generator = (DirectoryProjectGenerator)value;
            append(generator.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        }
      });
    }

    registerValidators(new FacetValidatorsManager() {
      public void registerValidator(FacetEditorValidator validator, JComponent... componentsToWatch) {
      }

      public void validate() {
        doValidate();
      }
    });
  }

  private void doValidate() {
    String projectName = myProjectNameTextField.getText();
    if (projectName.trim().isEmpty()) {
      setOKActionEnabled(false);
      setErrorText("Project name can't be empty");
      return;
    }
    DirectoryProjectGenerator generator = getProjectGenerator();
    if (generator != null) {
      String baseDirPath = myLocationField.getTextField().getText();
      ValidationResult validationResult = generator.validate(baseDirPath);
      if (!validationResult.isOk()) {
        setOKActionEnabled(false);
        setErrorText(validationResult.getErrorMessage());
        return;
      }
    }
    setOKActionEnabled(true);
    setErrorText(null);
  }

  private void registerValidators(final FacetValidatorsManager validatorsManager) {
    validateOnTextChange(validatorsManager, myLocationField.getTextField());
    validateOnSelectionChange(validatorsManager, myProjectTypeComboBox);
  }

  private static void validateOnSelectionChange(final FacetValidatorsManager validatorsManager, final JComboBox projectNameTextField) {
    projectNameTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        validatorsManager.validate();
      }
    });
  }

  private static void validateOnTextChange(final FacetValidatorsManager validatorsManager, final JTextField textField) {
    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        validatorsManager.validate();
      }
    });
  }

  public static String getBaseDir() {
    final String lastProjectLocation = GeneralSettings.getInstance().getLastProjectLocation();
    if (lastProjectLocation != null) {
      return lastProjectLocation.replace('/', File.separatorChar);
    }
    final String userHome = SystemProperties.getUserHome();
    //noinspection HardCodedStringLiteral
    return userHome.replace('/', File.separatorChar) + File.separator + ApplicationNamesInfo.getInstance().getLowercaseProductName() +
           "Projects";
  }

  protected JComponent createCenterPanel() {
    return myRootPane;
  }

  public String getNewProjectLocation() {
    return myLocationField.getText();
  }

  public String getNewProjectName() {
    return myProjectNameTextField.getText();
  }

  @Nullable
  public DirectoryProjectGenerator getProjectGenerator() {
    final Object selItem = myProjectTypeComboBox.getSelectedItem();
    if (selItem == EMPTY_PROJECT_GENERATOR) return null;
    return (DirectoryProjectGenerator)selItem;
  }

  public JComponent getPreferredFocusedComponent() {
    return myProjectNameTextField;
  }

  @Override
  protected String getHelpId() {
    return "create_new_project_dialog";
  }
}
