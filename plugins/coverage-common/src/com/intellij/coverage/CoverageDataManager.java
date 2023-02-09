// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.coverage;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class CoverageDataManager {
  public static CoverageDataManager getInstance(@NotNull Project project) {
    return project.getService(CoverageDataManagerImpl.class);
  }

  /**
   * TeamCity compatibility
   *
   * List coverage suite for presentation from IDEA
   *
   * @param name                  presentable name of a suite
   * @param filters               configured filters for this suite
   * @param lastCoverageTimeStamp when this coverage data was gathered
   * @param suiteToMergeWith      null remove coverage pack from prev run and get from new
   */
  public abstract CoverageSuite addCoverageSuite(String name,
                                                 CoverageFileProvider fileProvider,
                                                 String[] filters,
                                                 long lastCoverageTimeStamp,
                                                 @Nullable String suiteToMergeWith, final CoverageRunner coverageRunner,
                                                 final boolean coverageByTestEnabled, final boolean tracingEnabled);

  public abstract CoverageSuite addExternalCoverageSuite(String selectedFileName,
                                                         long timeStamp,
                                                         CoverageRunner coverageRunner, CoverageFileProvider fileProvider);


  public abstract CoverageSuite addCoverageSuite(CoverageEnabledConfiguration config);


  /**
   * @return registered suites
   */
  public abstract CoverageSuite @NotNull [] getSuites();

  /**
   * @return currently active suite
   */
  public abstract CoverageSuitesBundle getCurrentSuitesBundle();

  /**
   * Choose active suite. Calling this method triggers updating the presentations in project view, editors etc.
   * @param suite coverage suite to choose. <b>null</b> means no coverage information should be presented
   */
  public abstract void chooseSuitesBundle(@Nullable CoverageSuitesBundle suite);

  public abstract void coverageGathered(@NotNull CoverageSuite suite);

  /**
   * Remove suite
   * @param suite coverage suite to remove
   */
  public abstract void removeCoverageSuite(CoverageSuite suite);

  /**
   * runs computation in read action, blocking project close till action has been run,
   * and doing nothing in case projectClosing() event has been already broadcasted.
   *  Note that actions must not be long running not to cause significant pauses on project close.
   * @param computation {@link Computable to be run}
   * @return result of the computation or null if the project is already closing.
   */
  @Nullable
  public abstract <T> T doInReadActionIfProjectOpen(Computable<T> computation);

  public abstract boolean isSubCoverageActive();

  public abstract void selectSubCoverage(@NotNull final CoverageSuitesBundle suite, final List<String> methodNames);

  public abstract void restoreMergedCoverage(@NotNull final CoverageSuitesBundle suite);

  public abstract void addSuiteListener(@NotNull CoverageSuiteListener listener, @NotNull Disposable parentDisposable);

  public abstract void triggerPresentationUpdate();

  /**
   * This method attach process listener to process handler. Listener will load coverage information after process termination
   */
  public abstract void attachToProcess(@NotNull final ProcessHandler handler,
                                       @NotNull final RunConfigurationBase configuration, RunnerSettings runnerSettings);

  public abstract void processGatheredCoverage(@NotNull RunConfigurationBase configuration, RunnerSettings runnerSettings);

}
