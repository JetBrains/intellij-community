// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.language.psi.EditorConfigPsiFile;
import org.editorconfig.language.services.EditorConfigFileHierarchyService;
import org.editorconfig.language.services.EditorConfigServiceLoaded;
import org.editorconfig.language.services.EditorConfigServiceResult;
import org.editorconfig.language.util.EditorConfigPresentationUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class EditorConfigNavigationActionsFactory {
  @NotNull
  public static List<DumbAwareAction> getNavigationActions(@NotNull final PsiFile file) {
    final List<DumbAwareAction> actions = ContainerUtil.newArrayList();
    final List<EditorConfigPsiFile> parentFiles = getParentFiles(file.getVirtualFile(), file.getProject());
    for (EditorConfigPsiFile parentFile : parentFiles) {
      final NavigatablePsiElement target = parentFile.findRelevantNavigatable();
      actions.add(DumbAwareAction.create(
        getActionName(parentFile, parentFiles.size() > 1),
        event -> target.navigate(true)));
    }
    return actions;
  }

  private static String getActionName(@NotNull EditorConfigPsiFile file, boolean withFolder) {
    return EditorConfigBundle.message("action.open.file", EditorConfigPresentationUtil.getFileName(file, withFolder));
  }

  @NotNull
  static List<EditorConfigPsiFile> getParentFiles(@NotNull final VirtualFile virtualFile, @NotNull final Project project) {
    final EditorConfigFileHierarchyService hierarchyService = EditorConfigFileHierarchyService.getInstance(project);
    final EditorConfigServiceResult serviceResult = hierarchyService.getParentEditorConfigFiles(virtualFile);
    return serviceResult instanceof EditorConfigServiceLoaded ?
           ((EditorConfigServiceLoaded)serviceResult).getList() : Collections.emptyList();
  }
}
