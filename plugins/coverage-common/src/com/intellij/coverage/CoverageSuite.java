// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.rt.coverage.data.ProjectData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Represents coverage data collected by {@link CoverageRunner}.
 *
 * @see BaseCoverageSuite
 */
public interface CoverageSuite extends JDOMExternalizable {
  /**
   * @return false e.g. if underlying file is deleted.
   */
  boolean isValid();

  @NotNull
  String getCoverageDataFileName();

  @NlsSafe
  String getPresentableName();

  Project getProject();

  @NotNull
  CoverageEngine getCoverageEngine();

  CoverageRunner getRunner();

  @NotNull
  CoverageFileProvider getCoverageDataFileProvider();

  long getLastCoverageTimeStamp();

  boolean isTrackTestFolders();

  boolean isBranchCoverage();

  @ApiStatus.Internal
  boolean isCoverageByTestEnabled();

  /**
   * Get coverage data, of load it if it has not been loaded
   */
  @Nullable
  ProjectData getCoverageData(CoverageDataManager coverageDataManager);

  /**
   * Caches loaded coverage data on soft reference.
   */
  void setCoverageData(final ProjectData projectData);

  /**
   * Reinit coverage data cache with {@link CoverageRunner#loadCoverageDataWithReporting(File, CoverageSuite)}.
   */
  @ApiStatus.Internal
  default void restoreCoverageData() {
  }

  /**
   * @return true if engine can provide the means to remove coverage data.
   */
  default boolean canRemove() {
    CoverageFileProvider provider = getCoverageDataFileProvider();
    return provider instanceof DefaultCoverageFileProvider defaultProvider
           && Comparing.strEqual(defaultProvider.getSourceProvider(), DefaultCoverageFileProvider.DEFAULT_LOCAL_PROVIDER_KEY);
  }

  /**
   * Cleans gathered coverage on explicit user's action in the settings dialog or e.g., during rerun of the same configuration.
   */
  default void deleteCachedCoverageData() {
    final String fileName = getCoverageDataFileName();
    if (!FileUtil.isAncestor(PathManager.getSystemPath(), fileName, false)) {
      String message = CoverageBundle.message("dialog.message.would.you.like.to.delete.file.on.disk", fileName);
      if (Messages.showYesNoDialog(getProject(), message, CoverageBundle.message("delete.file"), Messages.getWarningIcon()) != Messages.YES) {
        return;
      }
    }
    File file = new File(fileName);
    if (file.exists()) {
      FileUtil.delete(file);
    }
    getCoverageEngine().deleteAssociatedTraces(this);
  }

  @ApiStatus.Internal
  default boolean isCoverageByTestApplicable() {
    CoverageRunner runner = getRunner();
    if (runner == null) return false;
    return runner.isCoverageByTestApplicable();
  }
}
