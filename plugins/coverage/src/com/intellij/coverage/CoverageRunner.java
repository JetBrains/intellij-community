/*
 * User: anna
 * Date: 13-Feb-2008
 */
package com.intellij.coverage;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.rt.coverage.data.ProjectData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class CoverageRunner {
  public static final ExtensionPointName<CoverageRunner> EP_NAME = ExtensionPointName.create("com.intellij.coverageRunner");

  public abstract ProjectData loadCoverageData(File sessionDataFile);

  public abstract void appendCoverageArgument(final String sessionDataFilePath, @Nullable final String[] patterns, final JavaParameters parameters,
                                              final boolean collectLineInfo, final boolean isSampling);

  public abstract String getPresentableName();

  @NonNls
  public abstract String getId();

  @NonNls
  public abstract String getDataFileExtension();

  public static CoverageRunner getInstance(Class<? extends CoverageRunner> coverageRunnerClass) {
    for(CoverageRunner coverageRunner: Extensions.getExtensions(EP_NAME)) {
      if (coverageRunnerClass.isInstance(coverageRunner)) {
        return coverageRunner;
      }
    }
    assert false;
    return null;
  }

  public boolean isCoverageByTestApplicable() {
    return false;
  }

  public void generateReport(final Project project, final String coverageDataFileName, final String outputDir, boolean openInBrowser){}

  public boolean isHTMLReportSupported() {
    return false;
  }
}