/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public abstract class GeneratePluginClassAction extends CreateElementActionBase implements DescriptorUtil.Patcher {
  protected final Set<XmlFile> myFilesToPatch = new HashSet<>();

  public GeneratePluginClassAction(String text, String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @NotNull
  protected final PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    try {
      final PsiElement[] psiElements = invokeDialogImpl(project, directory);
      return psiElements == DevkitActionsUtil.CANCELED ? PsiElement.EMPTY_ARRAY : psiElements;
    }
    finally {
      myFilesToPatch.clear();
    }
  }

  protected abstract PsiElement[] invokeDialogImpl(Project project, PsiDirectory directory);

  public void update(final AnActionEvent e) {
    super.update(e);

    final Presentation presentation = e.getPresentation();
    if (presentation.isEnabled()) {
      final Project project = e.getProject();
      final Module module = e.getData(LangDataKeys.MODULE);
      if (project != null && module != null &&
          PsiUtil.isPluginModule(module)) {
        final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
        if (view != null) {
          // from com.intellij.ide.actions.CreateClassAction.update()
          ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
          PsiDirectory[] dirs = view.getDirectories();
          for (PsiDirectory dir : dirs) {
            if (projectFileIndex.isUnderSourceRootOfType(dir.getVirtualFile(), JavaModuleSourceRootTypes.SOURCES) &&
                JavaDirectoryService.getInstance().getPackage(dir) != null) {
              return;
            }
          }
        }
      }

      presentation.setEnabledAndVisible(false);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    PsiClass[] createdClass =
      DevkitActionsUtil.createSinglePluginClass(newName, getClassTemplateName(), directory, myFilesToPatch, getTemplatePresentation());

    DescriptorUtil.patchPluginXml(this, createdClass[0], myFilesToPatch.toArray(new XmlFile[myFilesToPatch.size()]));
    return createdClass;
  }

  @NonNls
  protected abstract String getClassTemplateName();
}
