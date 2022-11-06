// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class CheckinHandlerUtil {
  public static boolean isGeneratedOrExcluded(@NotNull Project project, @NotNull VirtualFile file) {
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
    return fileIndex.isExcluded(file) || GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project);
  }

  @NotNull
  public static List<VirtualFile> filterOutGeneratedAndExcludedFiles(@NotNull Collection<? extends VirtualFile> files,
                                                                     @NotNull Project project) {
    return ContainerUtil.filter(files, file -> !isGeneratedOrExcluded(project, file));
  }

  public static PsiFile[] getPsiFiles(final Project project, final Collection<? extends VirtualFile> selectedFiles) {
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
    return OutOfSourcesChecker.EP_NAME.getExtensionList().stream()
      .anyMatch(checker -> FileTypeRegistry.getInstance().isFileOfType(file, checker.getFileType()) && checker.isOutOfSources(project, file));
  }

  public static void disableWhenDumb(@NotNull Project project, @NotNull JCheckBox checkBox, @NotNull @Nls String tooltip) {
    boolean dumb = DumbService.isDumb(project);
    checkBox.setEnabled(!dumb);
    checkBox.setToolTipText(dumb ? tooltip : "");
  }
}
