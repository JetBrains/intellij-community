/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author yole
 */
public class NewDirectoryProjectDialog extends DialogWrapper {
  private static final Object EMPTY_PROJECT_GENERATOR = new Object();

  private JPanel myRootPane;
  private JTextField myProjectNameTextField;
  private JLabel myLocationLabel;
  private TextFieldWithBrowseButton myLocationField;
  private JComboBox myProjectTypeComboBox;

  @SuppressWarnings("unchecked")
  protected NewDirectoryProjectDialog(@Nullable Project project) {
    super(project, true);
    setTitle(IdeBundle.message("new.dir.project.title"));
    init();

    myLocationLabel.setLabelFor(myLocationField.getChildComponent());

    String title = IdeBundle.message("new.dir.project.chooser.title");
    new LocationNameFieldsBinding(project, myLocationField, myProjectNameTextField, ProjectUtil.getBaseDir(), title);

    DefaultComboBoxModel model = new DefaultComboBoxModel();
    model.addElement(EMPTY_PROJECT_GENERATOR);

    DirectoryProjectGenerator[] generators = Extensions.getExtensions(DirectoryProjectGenerator.EP_NAME);
    if (generators.length > 0) {
      List<DirectoryProjectGenerator> primaryGenerators = ContainerUtil.newArrayList();
      List<DirectoryProjectGenerator> otherGenerators = ContainerUtil.newArrayList();
      for (DirectoryProjectGenerator generator : generators) {
        if (generator instanceof HideableProjectGenerator && ((HideableProjectGenerator)generator).isHidden()) {
          continue;
        }
        if (generator instanceof WebProjectGenerator && ((WebProjectGenerator)generator).isPrimaryGenerator()) {
          primaryGenerators.add(generator);
        }
        else {
          otherGenerators.add(generator);
        }
      }
      for (DirectoryProjectGenerator generator : primaryGenerators) model.addElement(generator);
      for (DirectoryProjectGenerator generator : otherGenerators) model.addElement(generator);
    }
    myProjectTypeComboBox.setModel(model);
    myProjectTypeComboBox.setRenderer(createProjectTypeListCellRenderer());

    myLocationField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        checkValid();
      }
    });
    myProjectTypeComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        checkValid();
      }
    });
  }

  @NotNull
  private static ListCellRendererWrapper<Object> createProjectTypeListCellRenderer() {
    return new ListCellRendererWrapper<Object>() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean cellHasFocus) {
        if (value == EMPTY_PROJECT_GENERATOR) {
          setText(IdeBundle.message("new.dir.project.default.generator"));
        }
        else if (value != null) {
          setText(((DirectoryProjectGenerator)value).getName());
        }
      }
    };
  }

  private void checkValid() {
    String projectName = myProjectNameTextField.getText();

    if (projectName.trim().isEmpty()) {
      setOKActionEnabled(false);
      setErrorText(IdeBundle.message("new.dir.project.error.empty"));
      return;
    }

    if (myLocationField.getText().indexOf('$') >= 0) {
      setOKActionEnabled(false);
      setErrorText(IdeBundle.message("new.dir.project.error.buck"));
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

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myProjectNameTextField;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myRootPane;
  }

  @Override
  protected String getHelpId() {
    return "create_new_project_dialog";
  }

  public String getNewProjectLocation() {
    return myLocationField.getText();
  }

  @Nullable
  public DirectoryProjectGenerator getProjectGenerator() {
    Object item = myProjectTypeComboBox.getSelectedItem();
    return item == EMPTY_PROJECT_GENERATOR ? null : (DirectoryProjectGenerator)item;
  }
}
