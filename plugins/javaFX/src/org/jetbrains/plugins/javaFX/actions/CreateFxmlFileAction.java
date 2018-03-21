/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

import java.util.*;

/**
 * @author pdolgov
 */
public class CreateFxmlFileAction extends CreateFromTemplateActionBase {
  private static final String INTERNAL_TEMPLATE_NAME = "FxmlFile.fxml";

  public CreateFxmlFileAction() {
    super("FXML File", "Create New FXML File", AllIcons.FileTypes.Xml);
  }

  @Override
  protected FileTemplate getTemplate(Project project, PsiDirectory dir) {
    return FileTemplateManager.getInstance(project).getInternalTemplate(INTERNAL_TEMPLATE_NAME);
  }

  @Nullable
  @Override
  protected Map<String, String> getLiveTemplateDefaults(DataContext dataContext, @NotNull PsiFile file) {
    String packageName = ReadAction.compute(() -> {
      PsiDirectory psiDirectory = file.getContainingDirectory();
      if (psiDirectory != null) {
        VirtualFile vDirectory = psiDirectory.getVirtualFile();
        ProjectFileIndex index = ProjectRootManager.getInstance(file.getProject()).getFileIndex();
        if (index.isInSourceContent(vDirectory)) {
          return index.getPackageNameByDirectory(vDirectory);
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
  public void update(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();

    presentation.setEnabledAndVisible(isAvailable(dataContext));
  }

  private static boolean isAvailable(DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || view == null) {
      return false;
    }
    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0) {
      return false;
    }

    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    return Arrays.stream(directories)
                 .map(PsiDirectory::getVirtualFile)
                 .anyMatch(virtualFile -> index.isUnderSourceRootOfType(virtualFile, JavaModuleSourceRootTypes.PRODUCTION));
  }
}
