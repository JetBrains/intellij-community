package com.intellij.coverage;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.rt.coverage.data.ProjectData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

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

  /**
   * @return true if engine can provide means to remove coverage data
   */
  default boolean canRemove() {
    CoverageFileProvider provider = getCoverageDataFileProvider();
    return provider instanceof DefaultCoverageFileProvider && Comparing.strEqual(((DefaultCoverageFileProvider)provider).getSourceProvider(),
                                                                                 DefaultCoverageFileProvider.class.getName());
  }

  /**
   * Called to cleanup gathered coverage on explicit user's action in settings dialog or e.g. during rerun of the same configuration
   */
  default void deleteCachedCoverageData() {
    final String fileName = getCoverageDataFileName();
    if (!FileUtil.isAncestor(PathManager.getSystemPath(), fileName, false)) {
      String message = "Would you like to delete file \'" + fileName + "\' " + "on disk?";
      if (Messages.showYesNoDialog(getProject(), message, "Delete File", Messages.getWarningIcon()) != Messages.YES) {
        return;
      }
    }
    File file = new File(fileName);
    if (file.exists()) {
      FileUtil.delete(file);
    }
    getCoverageEngine().deleteAssociatedTraces(this);
  }
}
