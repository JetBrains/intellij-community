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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.wizard.ExternalModuleSettingsStep;
import com.intellij.openapi.externalSystem.service.ui.ExternalProjectPathField;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

/**
 * @author Vladislav.Soroka
 * @since 4/15/2015
 */
public class GradleModuleWizardStep extends ModuleWizardStep {
  private static final Icon WIZARD_ICON = null;

  private static final String INHERIT_GROUP_ID_KEY = "GradleModuleWizard.inheritGroupId";
  private static final String INHERIT_VERSION_KEY = "GradleModuleWizard.inheritVersion";
  private static final String EMPTY_PARENT = "<none>";
  private static final String DEFAULT_VERSION = "1.0-SNAPSHOT";

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

  private EditorTextField myParentPathField;
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
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          IdeFocusManager.getInstance(myProjectOrNull).requestFocus(myArtifactIdField, true);
        }
      });
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

  @Override
  public void updateStep() {
    myParent = findPotentialParentProject(myProjectOrNull);

    ProjectId projectId = myBuilder.getProjectId();

    if (projectId == null) {
      setTestIfEmpty(myArtifactIdField, myBuilder.getName());
      setTestIfEmpty(myGroupIdField, myParent == null ? myBuilder.getName() : myParent.getGroup());
      setTestIfEmpty(myVersionField, myParent == null ? DEFAULT_VERSION : myParent.getVersion());
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

    myParentPathField.setText(myParent == null ? EMPTY_PARENT : myParent.getLinkedExternalProjectPath());
    collapseIfPossible(myParentPathField, GradleConstants.SYSTEM_ID, getProject());

    if (myParent == null) {
      myContext.putUserData(ExternalModuleSettingsStep.SKIP_STEP_KEY, Boolean.FALSE);
      myGroupIdField.setEnabled(true);
      myVersionField.setEnabled(true);
      myInheritGroupIdCheckBox.setEnabled(false);
      myInheritVersionCheckBox.setEnabled(false);

      setTestIfEmpty(myArtifactIdField, myBuilder.getName());
      setTestIfEmpty(myGroupIdField, "");
      setTestIfEmpty(myVersionField, DEFAULT_VERSION);
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

  private void createUIComponents() {
    Project project = getProject();
    myParentPathField = new TextViewer("", project);
  }

  @NotNull
  private Project getProject() {
    Project project = myProjectOrNull != null ? myProjectOrNull : ArrayUtil.getFirstElement(ProjectManager.getInstance().getOpenProjects());
    return project == null ? ProjectManager.getInstance().getDefaultProject() : project;
  }

  private static void collapseIfPossible(@NotNull EditorTextField editorTextField,
                                         @NotNull ProjectSystemId systemId,
                                         @NotNull Project project) {
    Editor editor = editorTextField.getEditor();
    if (editor != null) {
      String rawText = editor.getDocument().getText();
      if (StringUtil.isEmpty(rawText)) return;
      if (EMPTY_PARENT.equals(rawText)) {
        editorTextField.setEnabled(false);
        return;
      }
      final Collection<ExternalProjectInfo> projectsData =
        ProjectDataManager.getInstance().getExternalProjectsData(project, systemId);
      for (ExternalProjectInfo projectInfo : projectsData) {
        if (projectInfo.getExternalProjectStructure() != null && projectInfo.getExternalProjectPath().equals(rawText)) {
          editorTextField.setEnabled(true);
          ExternalProjectPathField.collapse(
            editorTextField.getEditor(), projectInfo.getExternalProjectStructure().getData().getExternalName());
          return;
        }
      }
    }
  }

  private static class TextViewer extends EditorTextField {
    private final boolean myEmbeddedIntoDialogWrapper;
    private final boolean myUseSoftWraps;

    public TextViewer(@NotNull String initialText, @NotNull Project project) {
      this(createDocument(initialText), project, true, true);
    }

    public TextViewer(@NotNull Document document, @NotNull Project project, boolean embeddedIntoDialogWrapper, boolean useSoftWraps) {
      super(document, project, FileTypes.PLAIN_TEXT, true, false);
      myEmbeddedIntoDialogWrapper = embeddedIntoDialogWrapper;
      myUseSoftWraps = useSoftWraps;
      setFontInheritedFromLAF(false);
    }

    private static Document createDocument(@NotNull String initialText) {
      return EditorFactory.getInstance().createDocument(initialText);
    }

    @Override
    public void setText(@Nullable String text) {
      super.setText(text != null ? StringUtil.convertLineSeparators(text) : null);
    }

    @Override
    protected EditorEx createEditor() {
      final EditorEx editor = super.createEditor();
      editor.setHorizontalScrollbarVisible(true);
      editor.setCaretEnabled(isEnabled());
      editor.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
      editor.setEmbeddedIntoDialogWrapper(myEmbeddedIntoDialogWrapper);
      editor.setBorder(UIUtil.getTextFieldBorder());
      editor.setOneLineMode(true);
      editor.getComponent().setPreferredSize(null);
      editor.getSettings().setUseSoftWraps(myUseSoftWraps);
      return editor;
    }

    @Override
    protected void setViewerEnabled(boolean enabled) {
      // do not reset com.intellij.ui.EditorTextField.myIsViewer field
    }
  }

  private static void setTestIfEmpty(@NotNull JTextField field, @Nullable String text) {
    if (StringUtil.isEmpty(field.getText())) {
      field.setText(StringUtil.notNullize(text));
    }
  }
}

