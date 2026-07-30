// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.coverage.filters.ModifiedFilesFilter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public abstract class BaseCoverageAnnotator implements CoverageAnnotator {

  private final Project myProject;
  private ModifiedFilesFilter myModifiedFilesFilter;

  protected abstract @Nullable Runnable createRenewRequest(final @NotNull CoverageSuitesBundle suite, final @NotNull CoverageDataManager dataManager);

  public BaseCoverageAnnotator(final Project project) {
    myProject = project;
  }

  @Override
  public void onSuiteChosen(@Nullable CoverageSuitesBundle newSuite) {
    myModifiedFilesFilter = null;
  }

  @ApiStatus.Internal
  @Override
  public final void renewCoverageData(final @NotNull CoverageSuitesBundle suite, final @NotNull CoverageDataManager dataManager) {
    final Runnable request = createRenewRequest(suite, dataManager);
    if (request != null) {
      if (myProject.isDisposed()) return;
      final Project project = myProject;
      CoverageBackgroundProgressKt.launchCoverageDataRenewal(
        project,
        () -> {
          myModifiedFilesFilter = ModifiedFilesFilter.create(project);
          request.run();
        },
        () -> dataManager.coverageDataCalculated(suite),
        () -> dataManager.closeSuitesBundle(suite)
      );
    }
  }

  public Project getProject() {
    return myProject;
  }

  @ApiStatus.Internal
  @Override
  public @Nullable ModifiedFilesFilter getModifiedFilesFilter() {
    return myModifiedFilesFilter;
  }

  public static class FileCoverageInfo {
    public int totalLineCount;
    public int coveredLineCount;
  }

  public static class DirCoverageInfo extends FileCoverageInfo {
    public int totalFilesCount;
    public int coveredFilesCount;
  }
}
