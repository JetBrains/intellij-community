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
package org.jetbrains.android.actions;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;

/**
 * @author Eugene.Kudelevsky
 */
public class NewAndroidComponentAction extends AnAction {

  protected NewAndroidComponentAction() {
    super(AndroidBundle.message("android.new.component.action.title"), AndroidBundle.message("android.new.component.action.description"),
          AndroidIcons.Android);
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(isAvailable(e.getDataContext()));
  }

  private static boolean isAvailable(DataContext dataContext) {
    final Module module = LangDataKeys.MODULE.getData(dataContext);
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);

    if (module == null ||
        view == null ||
        view.getDirectories().length == 0 ||
        AndroidFacet.getInstance(module) == null) {
      return false;
    }
    final ProjectFileIndex projectIndex = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
    final JavaDirectoryService dirService = JavaDirectoryService.getInstance();

    for (PsiDirectory dir : view.getDirectories()) {
      if (projectIndex.isInSourceContent(dir.getVirtualFile()) &&
          dirService.getPackage(dir) != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }

    final Module module = LangDataKeys.MODULE.getData(dataContext);

    if (module == null) return;

    final PsiDirectory dir = view.getOrChooseDirectory();
    if (dir == null) return;

    NewAndroidComponentDialog dialog = new NewAndroidComponentDialog(module, dir);
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
    final PsiElement[] createdElements = dialog.getCreatedElements();

    for (PsiElement createdElement : createdElements) {
      view.selectElement(createdElement);
    }
  }
}
