// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.coverage;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

public abstract class CoverageDataManager {

  CoverageDataManager() {}

  public static CoverageDataManager getInstance(@NotNull Project project) {
    return project.getService(CoverageDataManagerImpl.class);
  }

  /**
   * TeamCity compatibility
   * <p>
   * List coverage suite for presentation from IDEA
   *
   * @param name                  presentable name of a suite
   * @param filters               configured filters for this suite
   * @param lastCoverageTimeStamp when this coverage data was gathered
   * @param suiteToMergeWith      null remove coverage pack from prev run and get from new
   */
  @SuppressWarnings("unused")
  public abstract CoverageSuite addCoverageSuite(String name,
                                                 @NotNull CoverageFileProvider fileProvider,
                                                 String[] filters,
                                                 long lastCoverageTimeStamp,
                                                 @Nullable String suiteToMergeWith,
                                                 @NotNull CoverageRunner coverageRunner,
                                                 boolean coverageByTestEnabled, boolean branchCoverage);

  /**
   * @deprecated Use {@link CoverageDataManager#addExternalCoverageSuite(File, CoverageRunner)}
   */
  @Deprecated(forRemoval = true)
  public abstract CoverageSuite addExternalCoverageSuite(@NotNull String selectedFileName,
                                                         long timeStamp,
                                                         @NotNull CoverageRunner coverageRunner,
                                                         @NotNull CoverageFileProvider fileProvider);

  public final CoverageSuite addExternalCoverageSuite(@NotNull File file, @NotNull CoverageRunner coverageRunner) {
    return addExternalCoverageSuite(file.getName(), file.lastModified(), coverageRunner, new DefaultCoverageFileProvider(file.getAbsolutePath()));
  }


  public abstract CoverageSuite addCoverageSuite(CoverageEnabledConfiguration config);


  /**
   * Suites that are tracked by the coverage manager.
   * @return registered suites
   * @see com.intellij.coverage.actions.CoverageSuiteChooserDialog
   */
  public abstract CoverageSuite @NotNull [] getSuites();

  /**
   * @return Currently opened suites.
   */
  public abstract Collection<CoverageSuitesBundle> activeSuites();

  /**
   * Currently visible or one of the opened suites if view is not enabled.
   */
  public abstract CoverageSuitesBundle getCurrentSuitesBundle();

  /**
   * Choose active suite. Calling this method triggers updating the presentations in project view, editors etc.
   * @param suite coverage suite to choose. Must not be <code>null</code>. Use <code>closeSuitesBundle</code> to close a suite
   */
  public abstract void chooseSuitesBundle(@NotNull CoverageSuitesBundle suite);

  public abstract void closeSuitesBundle(@NotNull CoverageSuitesBundle suite);

  public abstract void coverageGathered(@NotNull CoverageSuite suite);

  /**
   * Called each time after a coverage suite is completely processed: data is loaded and accumulated
   */
  public void coverageDataCalculated(@NotNull CoverageSuitesBundle suite) {}

  /**
   * Remove suite
   * @param suite coverage suite to remove
   */
  public abstract void removeCoverageSuite(CoverageSuite suite);

  /**
   * Remove suite from the list of tracked suites.
   * <p>
   * In contrast to <code>removeCoverageSuite</code>, this method keeps file on disk.
   * @param suite suite to unregister
   */
  public abstract void unregisterCoverageSuite(CoverageSuite suite);

  /**
   * runs computation in read action, blocking project close till action has been run,
   * and doing nothing in case projectClosing() event has been already broadcasted.
   *  Note that actions must not be long-running not to cause significant pauses on project close.
   * @param computation {@link Computable to be run}
   * @return result of the computation or null if the project is already closing.
   */
  @Nullable
  public abstract <T> T doInReadActionIfProjectOpen(Computable<T> computation);

  @ApiStatus.Internal
  public boolean isSubCoverageActive() {
    return false;
  }

  @ApiStatus.Internal
  public void selectSubCoverage(@NotNull final CoverageSuitesBundle suite, final List<String> methodNames) {
  }

  @ApiStatus.Internal
  public void restoreMergedCoverage(@NotNull final CoverageSuitesBundle suite) {
  }

  public abstract void addSuiteListener(@NotNull CoverageSuiteListener listener, @NotNull Disposable parentDisposable);

  public abstract void triggerPresentationUpdate();

  /**
   * This method attach process listener to process handler. Listener will load coverage information after process termination
   */
  public abstract void attachToProcess(@NotNull final ProcessHandler handler,
                                       @NotNull final RunConfigurationBase<?> configuration, RunnerSettings runnerSettings);

  public abstract void processGatheredCoverage(@NotNull RunConfigurationBase<?> configuration, RunnerSettings runnerSettings);

}
