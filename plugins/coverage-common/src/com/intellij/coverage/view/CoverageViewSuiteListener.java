/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageSuite;
import com.intellij.coverage.CoverageSuiteListener;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.coverage.DefaultCoverageFileProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class CoverageViewSuiteListener implements CoverageSuiteListener {
  private final Project myProject;

  public CoverageViewSuiteListener(Project project) {
    myProject = project;
  }

  @Override
  public void coverageDataCalculated(@NotNull CoverageSuitesBundle suitesBundle) {
    CoverageViewManager viewManager = CoverageViewManager.getInstance(myProject);
    if (suitesBundle.getCoverageEngine().createCoverageViewExtension(myProject, suitesBundle, viewManager.getStateBean()) != null) {
      viewManager.createView(suitesBundle, shouldActivate(suitesBundle));
    }
  }

  private static boolean shouldActivate(CoverageSuitesBundle suitesBundle) {
    if (!suitesBundle.shouldActivateToolWindow()) return false;
    final CoverageSuite[] suites = suitesBundle.getSuites();
    for (CoverageSuite suite : suites) {
      if (!(suite.getCoverageDataFileProvider() instanceof DefaultCoverageFileProvider)) return false;
    }
    return true;
  }
}
