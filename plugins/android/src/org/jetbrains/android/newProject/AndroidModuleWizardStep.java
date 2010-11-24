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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidPlatformChooser;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 26, 2009
 * Time: 7:43:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidModuleWizardStep extends ModuleWizardStep {
  private final AndroidPlatformChooser myPlatformChooser;

  private final AndroidAppPropertiesEditor myAppPropertiesEditor;

  @Nullable
  private final AndroidTestPropertiesEditor myTestPropertiesEditor;

  private final AndroidModuleBuilder myModuleBuilder;
  private LibraryTable.ModifiableModel myModel;

  private JPanel myPanel;
  private JPanel mySdkPanel;
  private JRadioButton myApplicationProjectButton;
  private JRadioButton myLibProjectButton;
  private JRadioButton myTestProjectButton;
  private JPanel myPropertiesPanel;

  public AndroidModuleWizardStep(@NotNull AndroidModuleBuilder moduleBuilder, Project project) {
    super();
    myModel = LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel();
    myPlatformChooser = new AndroidPlatformChooser(myModel, null);
    List<Library> platformLibraries = myPlatformChooser.rebuildPlatforms();
    String defaultPlatformName = PropertiesComponent.getInstance().getValue(AndroidSdkUtils.DEFAULT_PLATFORM_NAME_PROPERTY);
    if (defaultPlatformName != null) {
      for (Library library : platformLibraries) {
        if (defaultPlatformName.equals(library.getName())) {
          myPlatformChooser.setSelectedPlatform(library);
          break;
        }
      }
    }
    mySdkPanel.setLayout(new BorderLayout(1, 1));
    mySdkPanel.add(myPlatformChooser.getComponent());

    myApplicationProjectButton.setSelected(true);

    myAppPropertiesEditor = new AndroidAppPropertiesEditor(moduleBuilder.getName());
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
    return myPanel;
  }

  @Override
  public boolean validate() throws ConfigurationException {
    if (myPlatformChooser.getSelectedPlatform() == null) {
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
    AndroidPlatform selectedPlatform = myPlatformChooser.getSelectedPlatform();
    assert selectedPlatform != null;
    myModuleBuilder.setPlatform(selectedPlatform);
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

    myPlatformChooser.apply();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myModel.commit();
      }
    });
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.new.project.fromScratch.android";
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myPlatformChooser);
  }
}
