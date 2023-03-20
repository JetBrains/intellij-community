// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.plugins.javaFX.JavaFXBundle;

import java.util.Set;

import static org.jetbrains.plugins.javaFX.actions.CreateFxmlFileAction.isJavaFxTemplateAvailable;

public final class CreateJavaFxApplicationAction extends CreateFromTemplateActionBase {

  private static final String FILE_TEMPLATE_NAME = "JavaFXApplication.java";

  public CreateJavaFxApplicationAction() {
    super(JavaFXBundle.message("javafx.create.new.application.title"), JavaFXBundle.message("javafx.create.new.application.description"),
          AllIcons.Nodes.Class);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  protected FileTemplate getTemplate(Project project, PsiDirectory dir) {
    return FileTemplateManager.getInstance(project).getTemplate(FILE_TEMPLATE_NAME);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isJavaFxTemplateAvailable(e.getDataContext(), Set.of(JavaSourceRootType.SOURCE)));
  }
}
