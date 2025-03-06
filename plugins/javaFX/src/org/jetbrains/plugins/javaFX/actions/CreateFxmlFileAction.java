// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.actions;

import com.intellij.ide.fileTemplates.actions.CustomCreateFromTemplateAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

import java.util.Collections;
import java.util.Map;

import static org.jetbrains.plugins.javaFX.actions.JavaFxTemplateManager.isJavaFxTemplateAvailable;

final class CreateFxmlFileAction extends CustomCreateFromTemplateAction implements DumbAware {
  private static final String INTERNAL_TEMPLATE_NAME = "FxmlFile.fxml";

  CreateFxmlFileAction() {
    super(INTERNAL_TEMPLATE_NAME);
  }

  @Override
  protected Map<String, String> getLiveTemplateDefaults(@NotNull DataContext dataContext, @NotNull PsiFile file) {
    String packageName = ReadAction.compute(() -> {
      PsiDirectory psiDirectory = file.getContainingDirectory();
      if (psiDirectory != null) {
        VirtualFile vDirectory = psiDirectory.getVirtualFile();
        ProjectFileIndex index = ProjectRootManager.getInstance(file.getProject()).getFileIndex();
        if (index.isInSourceContent(vDirectory)) {
          return PackageIndex.getInstance(file.getProject()).getPackageNameByDirectory(vDirectory);
        }
      }
      return null;
    });
    @NonNls String name = file.getName();
    name = PathUtil.getFileName(name);
    if (JavaFxFileTypeFactory.FXML_EXTENSION.equals(PathUtil.getFileExtension(name))) {
      name = name.substring(0, name.length() - JavaFxFileTypeFactory.FXML_EXTENSION.length() - 1);
    }

    name = toClassName(name);
    name = !StringUtil.isEmpty(packageName) ? packageName + "." + name : name;
    return Collections.singletonMap("CONTROLLER_NAME", name);
  }

  private static String toClassName(String name) {
    int start;
    for (start = 0; start < name.length(); start++) {
      char c = name.charAt(start);
      if (Character.isJavaIdentifierStart(c) && c != '_' && c != '$') {
        break;
      }
    }
    StringBuilder className = new StringBuilder();
    boolean skip = true;

    for (int i = start; i < name.length(); i++) {
      char c = name.charAt(i);
      if (!Character.isJavaIdentifierPart(c) || c == '_' || c == '$') {
        skip = true;
        continue;
      }
      if (skip) {
        skip = false;
        className.append(Character.toUpperCase(c));
      }
      else {
        className.append(c);
      }
    }
    return className.toString();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isJavaFxTemplateAvailable(e.getDataContext(), JavaModuleSourceRootTypes.PRODUCTION));
  }
}
