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

package org.jetbrains.android.newProject;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 26, 2009
 * Time: 7:43:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidModuleWizardStep extends ModuleWizardStep {

  private final AndroidAppPropertiesEditor myAppPropertiesEditor;

  @Nullable
  private final AndroidTestPropertiesEditor myTestPropertiesEditor;

  private final AndroidModuleBuilder myModuleBuilder;

  private JPanel myPanel;
  private JRadioButton myApplicationProjectButton;
  private JRadioButton myLibProjectButton;
  private JRadioButton myTestProjectButton;
  private JPanel myPropertiesPanel;

  private AndroidSdkComboBoxWithBrowseButton mySdkComboBoxWithBrowseButton;

  private final WizardContext myWizardContext;

  public AndroidModuleWizardStep(@NotNull AndroidModuleBuilder moduleBuilder, WizardContext context) {
    super();

    myWizardContext = context;
    myApplicationProjectButton.setSelected(true);

    myAppPropertiesEditor = new AndroidAppPropertiesEditor(moduleBuilder.getName());
    Project project = context.getProject();
    myTestPropertiesEditor = project != null ? new AndroidTestPropertiesEditor(project) : null;
    myPropertiesPanel.setLayout(new OverlayLayout(myPropertiesPanel));
    if (myTestPropertiesEditor != null) {
      myPropertiesPanel.add(myTestPropertiesEditor.getContentPanel());
      myTestPropertiesEditor.getContentPanel().setVisible(false);
    }
    else {
      myTestProjectButton.setVisible(false);
    }
    myPropertiesPanel.add(myAppPropertiesEditor.getContentPanel());

    myModuleBuilder = moduleBuilder;

    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myApplicationProjectButton.isSelected() || myLibProjectButton.isSelected()) {
          myAppPropertiesEditor.getContentPanel().setVisible(true);
          if (myTestPropertiesEditor != null) {
            myTestPropertiesEditor.getContentPanel().setVisible(false);
          }
          boolean app = myApplicationProjectButton.isSelected();
          myAppPropertiesEditor.getApplicationNameField().setEnabled(app);
          myAppPropertiesEditor.getHelloAndroidCheckBox().setEnabled(app);
          if (app) {
            myAppPropertiesEditor.updateActivityPanel();
          }
          else {
            UIUtil.setEnabled(myAppPropertiesEditor.getActivtiyPanel(), app, true);
          }
        }
        else {
          myAppPropertiesEditor.getContentPanel().setVisible(false);
          assert myTestPropertiesEditor != null;
          myTestPropertiesEditor.getContentPanel().setVisible(true);
        }
      }
    };
    myApplicationProjectButton.addActionListener(listener);
    myLibProjectButton.addActionListener(listener);
    myTestProjectButton.addActionListener(listener);
  }

  public JComponent getComponent() {
    myAppPropertiesEditor.getApplicationNameField().setText(myModuleBuilder.getName());

    Sdk selectedSdk = mySdkComboBoxWithBrowseButton.getSelectedSdk();
    if (selectedSdk == null) {
      String defaultPlatformName = PropertiesComponent.getInstance().getValue(AndroidSdkUtils.DEFAULT_PLATFORM_NAME_PROPERTY);
      if (defaultPlatformName != null) {
        Sdk sdk = ProjectJdkTable.getInstance().findJdk(defaultPlatformName);
        if (sdk != null && sdk.getSdkType().equals(AndroidSdkType.getInstance())) {
          selectedSdk = sdk;
        }
      }
    }
    mySdkComboBoxWithBrowseButton.rebuildSdksListAndSelectSdk(selectedSdk);

    return myPanel;
  }

  @Override
  public boolean validate() throws ConfigurationException {
    if (mySdkComboBoxWithBrowseButton.getSelectedSdk() == null) {
      throw new ConfigurationException(AndroidBundle.message("select.platform.error"));
    }

    if (myApplicationProjectButton.isSelected() || myLibProjectButton.isSelected()) {
      myAppPropertiesEditor.validate(myTestProjectButton.isSelected());
    }
    else {
      assert myTestPropertiesEditor != null;
      myTestPropertiesEditor.validate();
    }

    return true;
  }

  public void updateDataModel() {
    Sdk selectedSdk = mySdkComboBoxWithBrowseButton.getSelectedSdk();
    assert selectedSdk != null;

    PropertiesComponent.getInstance().setValue(AndroidSdkUtils.DEFAULT_PLATFORM_NAME_PROPERTY, selectedSdk.getName());
    myWizardContext.setProjectJdk(selectedSdk);

    if (myApplicationProjectButton.isSelected() || myLibProjectButton.isSelected()) {
      myModuleBuilder.setProjectType(myApplicationProjectButton.isSelected() ? ProjectType.APPLICATION : ProjectType.LIBRARY);
      myModuleBuilder.setActivityName(myAppPropertiesEditor.getActivityName());
      myModuleBuilder.setPackageName(myAppPropertiesEditor.getPackageName());
      myModuleBuilder.setApplicationName(myAppPropertiesEditor.getApplicationName());
    }
    else {
      myModuleBuilder.setProjectType(ProjectType.TEST);
      assert myTestPropertiesEditor != null;
      myModuleBuilder.setTestedModule(myTestPropertiesEditor.getModule());
    }
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.new.project.fromScratch.android";
  }
}
