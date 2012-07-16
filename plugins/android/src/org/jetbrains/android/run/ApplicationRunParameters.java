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

package org.jetbrains.android.run;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.ProjectScope;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 27, 2009
 * Time: 4:15:59 PM
 * To change this template use File | Settings | File Templates.
 */
class ApplicationRunParameters implements ConfigurationSpecificEditor<AndroidRunConfiguration> {
  private EditorTextFieldWithBrowseButton myActivityField;
  private JRadioButton myLaunchDefaultButton;
  private JRadioButton myLaunchCustomButton;
  private JPanel myPanel;
  private JRadioButton myDoNothingButton;
  private JCheckBox myDeployAndInstallCheckBox;
  private final Project myProject;
  private final ConfigurationModuleSelector myModuleSelector;

  ApplicationRunParameters(final Project project, final ConfigurationModuleSelector moduleSelector) {
    myProject = project;
    myModuleSelector = moduleSelector;

    myActivityField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (!project.isInitialized()) {
          return;
        }
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass activityBaseClass = facade.findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, ProjectScope.getAllScope(project));
        if (activityBaseClass == null) {
          Messages.showErrorDialog(myPanel, AndroidBundle.message("cant.find.activity.class.error"));
          return;
        }
        Module module = moduleSelector.getModule();
        if (module == null) {
          Messages.showErrorDialog(myPanel, ExecutionBundle.message("module.not.specified.error.text"));
          return;
        }
        PsiClass initialSelection = facade.findClass(myActivityField.getText(), module.getModuleWithDependenciesScope());
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
          .createInheritanceClassChooser("Select activity class", module.getModuleWithDependenciesScope(), activityBaseClass,
                                         initialSelection, null);
        chooser.showDialog();
        PsiClass selClass = chooser.getSelected();
        if (selClass != null) {
          myActivityField.setText(selClass.getQualifiedName());
        }
      }
    });
    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myActivityField.setEnabled(myLaunchCustomButton.isSelected());
      }
    };
    myLaunchCustomButton.addActionListener(listener);
    myLaunchDefaultButton.addActionListener(listener);
    myDoNothingButton.addActionListener(listener);
  }

  public void resetFrom(AndroidRunConfiguration configuration) {
    boolean launchSpecificActivity = configuration.MODE.equals(AndroidRunConfiguration.LAUNCH_SPECIFIC_ACTIVITY);
    if (configuration.MODE.equals(AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY)) {
      myLaunchDefaultButton.setSelected(true);
    }
    else if (launchSpecificActivity) {
      myLaunchCustomButton.setSelected(true);
    }
    else {
      myDoNothingButton.setSelected(true);
    }
    myActivityField.setEnabled(launchSpecificActivity);
    myActivityField.setText(configuration.ACTIVITY_CLASS);
    myDeployAndInstallCheckBox.setSelected(configuration.DEPLOY);
  }

  public Component getComponent() {
    return myPanel;
  }

  public void applyTo(AndroidRunConfiguration configuration) {
    configuration.ACTIVITY_CLASS = myActivityField.getText();
    if (myLaunchDefaultButton.isSelected()) {
      configuration.MODE = AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY;
    }
    else if (myLaunchCustomButton.isSelected()) {
      configuration.MODE = AndroidRunConfiguration.LAUNCH_SPECIFIC_ACTIVITY;
    }
    else {
      configuration.MODE = AndroidRunConfiguration.DO_NOTHING;
    }
    configuration.DEPLOY = myDeployAndInstallCheckBox.isSelected();
  }

  @Override
  public JComponent getAnchor() {
    return null;
  }

  @Override
  public void setAnchor(JComponent anchor) {
  }

  private void createUIComponents() {
    myActivityField = new EditorTextFieldWithBrowseButton(myProject, true,
                                                          new AndroidClassVisibilityChecker(myProject, myModuleSelector,
                                                                                            AndroidUtils.ACTIVITY_BASE_CLASS_NAME));
  }
}
