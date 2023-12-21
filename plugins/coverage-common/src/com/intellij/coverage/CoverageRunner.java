// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Represents coverage framework inside IntelliJ.
 */
public abstract class CoverageRunner {
  public static final ExtensionPointName<CoverageRunner> EP_NAME = ExtensionPointName.create("com.intellij.coverageRunner");

  /**
   * Loads coverage data from {@code sessionDataFile} into IntelliJ presentation, {@link ProjectData}.
   * 
   * @param baseCoverageSuite suite where coverage would be loaded. 
   *                          Can be used to retrieve additional information about configuration which was run with coverage.
   */
  @Nullable
  public abstract ProjectData loadCoverageData(@NotNull final File sessionDataFile, @Nullable final CoverageSuite baseCoverageSuite);

  /**
   * When multiple coverage runners are available for one {@link CoverageEngine}, 
   * {@code getPresentableName()} is used to render coverage runner in UI.
   */
  @NotNull
  @NonNls
  public abstract String getPresentableName();

  /**
   * @return unique id to serialize/deserialize used coverage runner.
   */
  @NotNull
  @NonNls
  public abstract String getId();

  /**
   * Used to compose file name where coverage framework should save coverage data.
   * It is also used to check if runner can load data from disk without actual loading.
   * 
   * @return file extension of the file where coverage framework stores coverage data.
   */
  @NotNull
  @NonNls
  public abstract String getDataFileExtension();

  @NonNls
  public String @NotNull [] getDataFileExtensions() {
    return new String[]{getDataFileExtension()};
  }

  /**
   * Checks whether a file is supported by the runner.
   */
  public boolean canBeLoaded(@NotNull File candidate) {
    return true;
  }

  /**
   * @return {@link ThreeState#YES}, if hits are always calculated; <br/>
   * {@link ThreeState#UNSURE}, if it depends on settings; <br/>
   * {@link ThreeState#NO}, if never calculated
   */
  public ThreeState calculateExactHits() {
    return ThreeState.NO;
  }

  /**
   * @return true if coverage runner works with the languages which corresponds to {@link CoverageEngine}.
   */
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

  /**
   * @return true if coverage framework can collect coverage information per test. 
   *         Then IntelliJ would allow e.g., seeing what tests cover selected line.
   */
  public boolean isCoverageByTestApplicable() {
    return false;
  }

  public static @Nullable CoverageRunner getInstanceById(@NotNull String id) {
    for (CoverageRunner coverageRunner : EP_NAME.getExtensionList()) {
      if (coverageRunner.getId().equals(id)) {
        return coverageRunner;
      }
    }
    return null;
  }
}