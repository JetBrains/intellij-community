// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.OutOfSourcesChecker;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author oleg
 */
public class CheckinHandlerUtil {
  public static List<VirtualFile> filterOutGeneratedAndExcludedFiles(@NotNull Collection<VirtualFile> files, @NotNull Project project) {
    ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    List<VirtualFile> result = new ArrayList<>(files.size());
    for (VirtualFile file : files) {
      if (!fileIndex.isExcluded(file) && !GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project)) {
        result.add(file);
      }
    }
    return result;
  }

  public static PsiFile[] getPsiFiles(final Project project, final Collection<VirtualFile> selectedFiles) {
    ArrayList<PsiFile> result = new ArrayList<>();
    PsiManager psiManager = PsiManager.getInstance(project);

    IProjectStore projectStore = ProjectKt.getStateStore(project);
    for (VirtualFile file : selectedFiles) {
      if (file.isValid()) {
        if (projectStore.isProjectFile(file) || !isFileUnderSourceRoot(project, file)
            || isOutOfSources(project, file)) {
          continue;
        }
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null) result.add(psiFile);
      }
    }
    return PsiUtilCore.toPsiFileArray(result);
  }

  private static boolean isFileUnderSourceRoot(@NotNull Project project, @NotNull VirtualFile file) {
    ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    return index.isInContent(file) && !index.isInLibrarySource(file);
  }

  private static boolean isOutOfSources(@NotNull Project project, @NotNull VirtualFile file) {
    for (OutOfSourcesChecker checker : OutOfSourcesChecker.EP_NAME.getExtensions()) {
      if (checker.getFileType() == file.getFileType()
          && checker.isOutOfSources(project, file)) {
        return true;
      }
    }
    return false;
  }

  public static void disableWhenDumb(@NotNull Project project, @NotNull JCheckBox checkBox, @NotNull String tooltip) {
    boolean dumb = DumbService.isDumb(project);
    checkBox.setEnabled(!dumb);
    checkBox.setToolTipText(dumb ? tooltip : "");
  }
}
