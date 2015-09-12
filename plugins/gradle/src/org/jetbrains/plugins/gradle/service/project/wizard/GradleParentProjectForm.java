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
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.ui.ExternalProjectPathField;
import com.intellij.openapi.externalSystem.service.ui.SelectExternalProjectDialog;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.EmptyConsumer;
import com.intellij.util.NullableConsumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

import static org.jetbrains.plugins.gradle.service.project.wizard.GradleModuleWizardStep.isGradleModuleExist;

public class GradleParentProjectForm {

  private static final String EMPTY_PARENT = "<none>";

  @Nullable
  private Project myProjectOrNull;
  @Nullable
  private ProjectData myParent;
  @NotNull
  private final Consumer<ProjectData> myConsumer;

  private final boolean myIsVisible;

  private JPanel myPanel;
  private JButton mySelectParent;
  private EditorTextField myParentPathField;

  public GradleParentProjectForm(WizardContext context, @Nullable NullableConsumer<ProjectData> consumer) {
    myProjectOrNull = context.getProject();
    myConsumer = consumer == null ? EmptyConsumer.<ProjectData>getInstance() : consumer;
    myIsVisible = !context.isCreatingNewProject() && myProjectOrNull != null && isGradleModuleExist(context);
    initComponents();
  }

  private void createUIComponents() {
    myParentPathField = new TextViewer("", getProject());
  }

  private void initComponents() {
    myPanel.setVisible(myIsVisible);
    if (!myIsVisible) return;
    mySelectParent.setIcon(AllIcons.Actions.Module);
    mySelectParent.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myParent = doSelectProject(myParent);
        myConsumer.consume(myParent);
      }
    });
  }

  public JPanel getComponent() {
    return myPanel;
  }

  @Nullable
  public ProjectData getParentProject() {
    return myParent;
  }

  public boolean isVisible() {
    return myIsVisible;
  }

  public void updateComponents() {
    myParentPathField.setText(myParent == null ? EMPTY_PARENT : myParent.getLinkedExternalProjectPath());
    collapseIfPossible(myParentPathField, GradleConstants.SYSTEM_ID, getProject());
  }

  private ProjectData doSelectProject(ProjectData current) {
    assert myProjectOrNull != null : "must not be called when creating a new project";

    SelectExternalProjectDialog d = new SelectExternalProjectDialog(GradleConstants.SYSTEM_ID, myProjectOrNull, current);
    if (!d.showAndGet()) {
      return current;
    }

    return d.getResult();
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
}
