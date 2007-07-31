/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.*;

public abstract class NewActionBase extends CreateElementActionBase {

  public NewActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @NotNull
  protected final PsiElement[] invokeDialog(final Project project, final PsiDirectory directory) {
    MyInputValidator validator = new MyInputValidator(project, directory);
    Messages.showInputDialog(project, getDialogPrompt(), getDialogTitle(), Messages.getQuestionIcon(), "", validator);

    return validator.getCreatedElements();
  }

  protected abstract String getDialogPrompt();

  protected abstract String getDialogTitle();

  public final void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();

    super.update(e);

    if (!presentation.isEnabled() || !isUnderSourceRoots(e))
      return;

    if (!isGroovyConigured(e)) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
  }

  private boolean isGroovyConigured(AnActionEvent e) {
    Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);
    if (project != null) {
      PsiPackage groovyPackage = PsiManager.getInstance(project).findPackage("groovy.lang");
      if (groovyPackage != null) return true;
    }
    return false;
  }

  public static boolean isUnderSourceRoots(final AnActionEvent e) {
    final DataContext context = e.getDataContext();
    Module module = (Module) context.getData(DataConstants.MODULE);
    if (module == null) {
      return false;
    }
    final IdeView view = (IdeView) context.getData(DataConstants.IDE_VIEW);
    final Project project = (Project) context.getData(DataConstants.PROJECT);
    if (view != null && project != null) {
      ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      PsiDirectory[] dirs = view.getDirectories();
      for (PsiDirectory dir : dirs) {
        if (projectFileIndex.isInSourceContent(dir.getVirtualFile()) && dir.getPackage() != null) {
          return true;
        }
      }
    }

    return false;
  }

  @NotNull
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    return doCreate(newName, directory);
  }

  @NotNull
  protected abstract PsiElement[] doCreate(String newName, PsiDirectory directory) throws Exception;

  protected static PsiFile createClassFromTemplate(final PsiDirectory directory, String className, String templateName,
                                                   @NonNls String... parameters) throws IncorrectOperationException {
    return GroovyTemplatesFactory.createFromTemplate(directory, className, className + ".groovy", templateName, parameters);
  }


  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return GroovyBundle.message("newclass.progress.text", newName);
  }

  protected final void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException {
    directory.checkCreateClass(newName);
  }
}
