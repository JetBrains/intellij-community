// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.coverage.actions.ExternalReportImportManager;
import com.intellij.coverage.actions.ExternalReportImportManagerKt;
import com.intellij.icons.AllIcons;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.openapi.fileTypes.INativeFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

final class JaCoCoCoverageFileType implements INativeFileType, FileTypeIdentifiableByVirtualFile {
  public static final JaCoCoCoverageFileType INSTANCE = new JaCoCoCoverageFileType();

  private JaCoCoCoverageFileType() {
  }

  @Override
  public @NonNls @NotNull String getName() {
    return "JaCoCoCoverageReport";
  }

  @Override
  public @NotNull @NlsContexts.Label String getDescription() {
    return JavaCoverageBundle.message("filetype.jacoco.coverage.report.description");
  }

  @Override
  public @NlsSafe @NotNull String getDefaultExtension() {
    return "exec";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Toolwindows.ToolWindowCoverage;
  }

  @Override
  public boolean isBinary() {
    return true;
  }

  @Override
  public boolean useNativeIcon() {
    return false;
  }

  @Override
  public boolean openFileInAssociatedApplication(Project project, @NotNull VirtualFile file) {
    return ExternalReportImportManager.getInstance(project).openSuiteFromFile(file, ExternalReportImportManager.Source.FILE_OPEN);
  }

  @Override
  public boolean isMyFileType(@NotNull VirtualFile file) {
    var coverageRunner = ExternalReportImportManagerKt.getCoverageRunner(file);
    if (coverageRunner instanceof JaCoCoCoverageRunner jaCoCoCoverageRunner) {
      return jaCoCoCoverageRunner.canBeLoaded(VfsUtilCore.virtualToIoFile(file));
    }
    return false;
  }
}
