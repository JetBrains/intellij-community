// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.coverage.filters.ModifiedFilesFilter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Roman.Chernyatchik
 */
public interface CoverageAnnotator {
  /**
   * One must override at least one of <code>getDirCoverageInformationString</code> methods.
   *
   * @param psiDirectory {@link PsiDirectory} to obtain coverage information for
   * @return human-readable coverage information
   */
  default @Nullable @Nls String getDirCoverageInformationString(@NotNull PsiDirectory psiDirectory, @NotNull CoverageSuitesBundle currentSuite,
                                                 @NotNull CoverageDataManager manager) {
    return getDirCoverageInformationString(psiDirectory.getProject(), psiDirectory.getVirtualFile(), currentSuite, manager);
  }

  /**
   * One must override at least one of <code>getDirCoverageInformationString</code> methods.
   *
   * @param directory {@link VirtualFile} to obtain coverage information for
   * @return human-readable coverage information
   */
  default @Nullable @Nls String getDirCoverageInformationString(@NotNull Project project, @NotNull VirtualFile directory,
                                                 @NotNull CoverageSuitesBundle currentSuite, @NotNull CoverageDataManager manager) {
    PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(directory);
    if (psiDirectory == null) return null;
    return getDirCoverageInformationString(psiDirectory, currentSuite, manager);
  }

  /**
   * One must override at least one of <code>getFileCoverageInformationString</code> methods.
   *
   * @param psiFile {@link PsiFile} to obtain coverage information for
   * @return human-readable coverage information
   */
  default @Nullable @Nls String getFileCoverageInformationString(@NotNull PsiFile psiFile, @NotNull CoverageSuitesBundle currentSuite,
                                                  @NotNull CoverageDataManager manager) {
    VirtualFile file = psiFile.getVirtualFile();
    VirtualFile virtualFile = Objects.requireNonNullElse(file.getCanonicalFile(), file);
    return getFileCoverageInformationString(psiFile.getProject(), virtualFile, currentSuite, manager);
  }

  /**
   * One must override at least one of <code>getFileCoverageInformationString</code> methods.
   *
   * @param file {@link VirtualFile} to obtain coverage information for
   * @return human-readable coverage information
   */
  default @Nullable @Nls String getFileCoverageInformationString(@NotNull Project project, @NotNull VirtualFile file,
                                                  @NotNull CoverageSuitesBundle currentSuite, @NotNull CoverageDataManager manager) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) return null;
    return getFileCoverageInformationString(psiFile, currentSuite, manager);
  }

  void onSuiteChosen(@Nullable CoverageSuitesBundle newSuite);

  void renewCoverageData(@NotNull CoverageSuitesBundle suite, @NotNull CoverageDataManager dataManager);

  @ApiStatus.Internal
  default @Nullable ModifiedFilesFilter getModifiedFilesFilter() {
    return null;
  }
}
