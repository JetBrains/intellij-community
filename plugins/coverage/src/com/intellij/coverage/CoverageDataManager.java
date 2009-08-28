/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.coverage;

import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author ven
 */
public abstract class CoverageDataManager implements ProjectComponent, JDOMExternalizable {

  public static CoverageDataManager getInstance(Project project) {
    return project.getComponent(CoverageDataManager.class);
  }

  /**
   * @param directory  {@link com.intellij.psi.PsiDirectory} to obtain coverage information for
   * @return human-readable coverage information
   */
  public abstract String getDirCoverageInformationString(PsiDirectory directory);

  /**
   * @param packageFQName qualified name of a package to obtain coverage information for
   * @param module optional parameter to restrict coverage to source directories of a certain module
   * @return human-readable coverage information
   */
  public abstract String getPackageCoverageInformationString(String packageFQName, @Nullable Module module);

  /**
   * @param classFQName {@link com.intellij.psi.PsiClass} to obtain coverage information for
   * @return human-readable coverage information
   */
  public abstract String getClassCoverageInformationString(String classFQName);

  /**
   * List coverage suite for presentation from IDEA
   *
   * @param name                  presentable name of a suite
   * @param fileProvider
   * @param filters               configured filters for this suite
   * @param lastCoverageTimeStamp when this coverage data was gathered
   * @param suiteToMergeWith      null remove coverage pack from prev run and get from new
   * @param coverageRunner
   * @param collectLineInfo
   * @param tracingEnabled
   */
  public abstract CoverageSuite addCoverageSuite(String name,
                                                 CoverageFileProvider fileProvider,
                                                 String[] filters,
                                                 long lastCoverageTimeStamp,
                                                 @Nullable String suiteToMergeWith, final CoverageRunner coverageRunner,
                                                 final boolean collectLineInfo, final boolean tracingEnabled);


  public abstract CoverageSuite addCoverageSuite(CoverageEnabledConfiguration config);
  /**
   * TeamCity 3.1.1 compatibility
   */
  @SuppressWarnings({"UnusedDeclaration"})
  @Deprecated
  public CoverageSuite addCoverageSuite(String name,
                                        CoverageFileProvider fileProvider,
                                        String[] filters,
                                        long lastCoverageTimeStamp,
                                        boolean suiteToMergeWith) {
    return addCoverageSuite(name, fileProvider, filters, lastCoverageTimeStamp, null, null, false, false);
  }


  /**
   * @return registered suites
   */
  public abstract CoverageSuite[] getSuites();

  /**
   * @return currently aactive suite
   */
  public abstract CoverageSuite getCurrentSuite();

  /**
   * Choose active suite. Calling this method triggers updating the presentations in project view, editors etc.
   * @param suite coverage suite to choose. <b>null</b> means no coverage information should be presented 
   */
  public abstract void chooseSuite(@Nullable CoverageSuite suite);

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
   * @param computation {@link com.intellij.openapi.util.Computable to be run}
   * @return result of the computation or null if the project is already closing.
   */
  @Nullable
  public abstract <T> T doInReadActionIfProjectOpen(Computable<T> computation);

  public abstract void selectSubCoverage(@NotNull CoverageSuite suite, List<String> methodNames);

  public abstract void restoreMergedCoverage(@NotNull CoverageSuite suite);

  public abstract void addSuiteListener(CoverageSuiteListener listener, Disposable parentDisposable);
}
