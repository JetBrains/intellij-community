/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.project.wizard;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.wizard.ExternalModuleSettingsStep;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Vladislav.Soroka
 * @since 4/15/2015
 */
public class GradleModuleWizardStep extends ModuleWizardStep {
  private static final Icon WIZARD_ICON = null;

  private static final String INHERIT_GROUP_ID_KEY = "GradleModuleWizard.inheritGroupId";
  private static final String INHERIT_VERSION_KEY = "GradleModuleWizard.inheritVersion";

  @Nullable
  private final Project myProjectOrNull;
  @NotNull
  private final GradleModuleBuilder myBuilder;
  @NotNull
  private final WizardContext myContext;
  @Nullable
  private ProjectData myParent;

  private String myInheritedGroupId;
  private String myInheritedVersion;

  private JPanel myMainPanel;

  private JLabel myParentNameLabel;
  private JButton mySelectParent;

  private JTextField myGroupIdField;
  private JCheckBox myInheritGroupIdCheckBox;
  private JTextField myArtifactIdField;
  private JTextField myVersionField;
  private JCheckBox myInheritVersionCheckBox;

  private JPanel myAddToPanel;


  public GradleModuleWizardStep(@NotNull GradleModuleBuilder builder, @NotNull WizardContext context) {
    myProjectOrNull = context.getProject();
    myBuilder = builder;
    myContext = context;
    initComponents();
    loadSettings();
  }

  private void initComponents() {
    mySelectParent.setIcon(AllIcons.Actions.Module);
    mySelectParent.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myParent = doSelectProject(myParent);
        updateComponents();
      }
    });

    ActionListener updatingListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateComponents();
      }
    };
    myInheritGroupIdCheckBox.addActionListener(updatingListener);
    myInheritVersionCheckBox.addActionListener(updatingListener);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGroupIdField;
  }

  private ProjectData doSelectProject(ProjectData current) {
    assert myProjectOrNull != null : "must not be called when creating a new project";

    SelectExternalProjectDialog d = new SelectExternalProjectDialog(myProjectOrNull, current);
    if (!d.showAndGet()) {
      return current;
    }
    return d.getResult();
  }

  @Override
  public void onStepLeaving() {
    saveSettings();
  }

  private void loadSettings() {
    myBuilder.setInheritGroupId(getSavedValue(INHERIT_GROUP_ID_KEY, true));
    myBuilder.setInheritVersion(getSavedValue(INHERIT_VERSION_KEY, true));
  }

  private void saveSettings() {
    saveValue(INHERIT_GROUP_ID_KEY, myInheritGroupIdCheckBox.isSelected());
    saveValue(INHERIT_VERSION_KEY, myInheritVersionCheckBox.isSelected());
  }

  private static boolean getSavedValue(String key, boolean defaultValue) {
    return getSavedValue(key, String.valueOf(defaultValue)).equals(String.valueOf(true));
  }

  private static String getSavedValue(String key, String defaultValue) {
    String value = PropertiesComponent.getInstance().getValue(key);
    return value == null ? defaultValue : value;
  }

  private static void saveValue(String key, boolean value) {
    saveValue(key, String.valueOf(value));
  }

  private static void saveValue(String key, String value) {
    PropertiesComponent props = PropertiesComponent.getInstance();
    props.setValue(key, value);
  }

  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public boolean validate() throws ConfigurationException {
    if (StringUtil.isEmptyOrSpaces(myArtifactIdField.getText())) {
      throw new ConfigurationException("Please, specify artifactId");
    }

    return true;
  }

  @Nullable
  public ProjectData findPotentialParentProject(@Nullable Project project) {
    if (project == null) return null;

    final ExternalProjectInfo projectInfo =
      ProjectDataManager.getInstance().getExternalProjectData(project, GradleConstants.SYSTEM_ID, myContext.getProjectFileDirectory());
    return projectInfo != null && projectInfo.getExternalProjectStructure() != null
           ? projectInfo.getExternalProjectStructure().getData()
           : null;
  }

  private static void setTestIfEmpty(@NotNull JTextField artifactIdField, @Nullable String text) {
    if (StringUtil.isEmpty(artifactIdField.getText())) {
      artifactIdField.setText(StringUtil.notNullize(text));
    }
  }

  @Override
  public void updateStep() {
    myParent = findPotentialParentProject(myProjectOrNull);

    ProjectId projectId = myBuilder.getProjectId();

    if (projectId == null) {
      setTestIfEmpty(myArtifactIdField, myBuilder.getName());
      setTestIfEmpty(myGroupIdField, myParent == null ? myBuilder.getName() : myParent.getGroup());
      setTestIfEmpty(myVersionField, myParent == null ? "1.0-SNAPSHOT" : myParent.getVersion());
    }
    else {
      setTestIfEmpty(myArtifactIdField, projectId.getArtifactId());
      setTestIfEmpty(myGroupIdField, projectId.getGroupId());
      setTestIfEmpty(myVersionField, projectId.getVersion());
    }

    myInheritGroupIdCheckBox.setSelected(myBuilder.isInheritGroupId());
    myInheritVersionCheckBox.setSelected(myBuilder.isInheritVersion());

    updateComponents();
  }


  private void updateComponents() {
    boolean isAddToVisible = !myContext.isCreatingNewProject() && myProjectOrNull != null && isGradleModuleExist();

    myAddToPanel.setVisible(isAddToVisible);
    myInheritGroupIdCheckBox.setVisible(isAddToVisible);
    myInheritVersionCheckBox.setVisible(isAddToVisible);

    myParentNameLabel.setText(formatProjectString(myParent));

    if (myParent == null) {
      myContext.putUserData(ExternalModuleSettingsStep.SKIP_STEP_KEY, Boolean.FALSE);
      myGroupIdField.setEnabled(true);
      myVersionField.setEnabled(true);
      myInheritGroupIdCheckBox.setEnabled(false);
      myInheritVersionCheckBox.setEnabled(false);
    }
    else {
      myContext.putUserData(ExternalModuleSettingsStep.SKIP_STEP_KEY, Boolean.TRUE);
      myGroupIdField.setEnabled(!myInheritGroupIdCheckBox.isSelected());
      myVersionField.setEnabled(!myInheritVersionCheckBox.isSelected());

      if (myInheritGroupIdCheckBox.isSelected()
          || myGroupIdField.getText().equals(myInheritedGroupId)) {
        myGroupIdField.setText(myParent.getGroup());
      }
      if (myInheritVersionCheckBox.isSelected()
          || myVersionField.getText().equals(myInheritedVersion)) {
        myVersionField.setText(myParent.getVersion());
      }
      myInheritedGroupId = myGroupIdField.getText();
      myInheritedVersion = myVersionField.getText();

      myInheritGroupIdCheckBox.setEnabled(true);
      myInheritVersionCheckBox.setEnabled(true);
    }
  }

  private boolean isGradleModuleExist() {
    for (Module module : myContext.getModulesProvider().getModules()) {
      if (ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return true;
    }
    return false;
  }

  private static String formatProjectString(ProjectData moduleData) {
    if (moduleData == null) return "<none>";
    return moduleData.toString();
  }

  @Override
  public void updateDataModel() {
    myContext.setProjectBuilder(myBuilder);
    myBuilder.setParentProject(myParent);

    myBuilder.setProjectId(new ProjectId(myGroupIdField.getText(),
                                         myArtifactIdField.getText(),
                                         myVersionField.getText()));
    myBuilder.setInheritGroupId(myInheritGroupIdCheckBox.isSelected());
    myBuilder.setInheritVersion(myInheritVersionCheckBox.isSelected());

    if (StringUtil.isNotEmpty(myBuilder.getProjectId().getArtifactId())) {
      myContext.setProjectName(myBuilder.getProjectId().getArtifactId());
    }
    if (myParent != null) {
      myContext.setProjectFileDirectory(myParent.getLinkedExternalProjectPath() + '/' + myContext.getProjectName());
    }
    else {
      if (myProjectOrNull != null) {
        myContext.setProjectFileDirectory(myProjectOrNull.getBaseDir().getPath() + '/' + myContext.getProjectName());
      }
    }
  }

  @Override
  public Icon getIcon() {
    return WIZARD_ICON;
  }
}

