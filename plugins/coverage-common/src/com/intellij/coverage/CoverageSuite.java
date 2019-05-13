package com.intellij.coverage;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.rt.coverage.data.ProjectData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public interface CoverageSuite extends JDOMExternalizable {
  boolean isValid();

  @NotNull
  String getCoverageDataFileName();

  String getPresentableName();

  long getLastCoverageTimeStamp();

  @NotNull
  CoverageFileProvider getCoverageDataFileProvider();

  boolean isCoverageByTestApplicable();

  boolean isCoverageByTestEnabled();

  @Nullable
  ProjectData getCoverageData(CoverageDataManager coverageDataManager);

  void setCoverageData(final ProjectData projectData);

  void restoreCoverageData();

  boolean isTrackTestFolders();

  boolean isTracingEnabled();

  CoverageRunner getRunner();

  @NotNull
  CoverageEngine getCoverageEngine();

  Project getProject();
}
