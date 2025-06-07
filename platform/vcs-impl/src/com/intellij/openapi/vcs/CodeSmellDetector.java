// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.codeInsight.CodeSmellInfo;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;


public abstract class CodeSmellDetector {
  public static CodeSmellDetector getInstance(Project project) {
    return project.getService(CodeSmellDetector.class);
  }

  /**
   * Performs pre-checkin code analysis on the specified files.
   *
   * @param files the files to analyze.
   * @return the list of problems found during the analysis.
   * @throws ProcessCanceledException if the analysis was cancelled by the user.
   */
  public abstract @NotNull List<CodeSmellInfo> findCodeSmells(@NotNull List<? extends VirtualFile> files) throws ProcessCanceledException;

  /**
   * Shows the specified list of problems found during pre-checkin code analysis in a Messages pane.
   *
   * @param smells the problems to show.
   */
  public abstract void showCodeSmellErrors(@NotNull @Unmodifiable List<? extends CodeSmellInfo> smells);

}