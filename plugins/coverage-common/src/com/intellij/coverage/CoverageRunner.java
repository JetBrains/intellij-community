/*
 * User: anna
 * Date: 13-Feb-2008
 */
package com.intellij.coverage;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.rt.coverage.data.ProjectData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class CoverageRunner {
  public static final ExtensionPointName<CoverageRunner> EP_NAME = ExtensionPointName.create("com.intellij.coverageRunner");

  public abstract ProjectData loadCoverageData(@NotNull final File sessionDataFile, @Nullable final CoverageSuite baseCoverageSuite);

  public abstract String getPresentableName();

  @NonNls
  public abstract String getId();

  @NonNls
  public abstract String getDataFileExtension();

  public abstract boolean acceptsCoverageEngine(@NotNull final CoverageEngine engine);

  public static <T extends CoverageRunner> T getInstance(@NotNull Class<T> coverageRunnerClass) {
    for (CoverageRunner coverageRunner : Extensions.getExtensions(EP_NAME)) {
      if (coverageRunnerClass.isInstance(coverageRunner)) {
        return coverageRunnerClass.cast(coverageRunner);
      }
    }
    assert false;
    return null;
  }

  public boolean isCoverageByTestApplicable() {
    return false;
  }
}