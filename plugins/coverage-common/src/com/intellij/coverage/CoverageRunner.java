// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.rt.coverage.data.ProjectData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class CoverageRunner {
  public static final ExtensionPointName<CoverageRunner> EP_NAME = ExtensionPointName.create("com.intellij.coverageRunner");

  @Nullable
  public abstract ProjectData loadCoverageData(@NotNull final File sessionDataFile, @Nullable final CoverageSuite baseCoverageSuite);

  @NotNull
  public abstract String getPresentableName();

  @NotNull
  public abstract String getId();

  @NotNull
  @NonNls
  public abstract String getDataFileExtension();

  @NotNull
  @NonNls
  public String[] getDataFileExtensions() {
    return new String[]{getDataFileExtension()};
  }

  public abstract boolean acceptsCoverageEngine(@NotNull final CoverageEngine engine);

  public static <T extends CoverageRunner> T getInstance(@NotNull Class<T> coverageRunnerClass) {
    for (CoverageRunner coverageRunner : EP_NAME.getExtensionList()) {
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