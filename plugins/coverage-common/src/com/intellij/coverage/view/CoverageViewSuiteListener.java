package com.intellij.coverage.view;

import com.intellij.coverage.*;
import com.intellij.openapi.project.Project;

/**
* User: anna
* Date: 1/5/12
*/
public class CoverageViewSuiteListener implements CoverageSuiteListener {
  private final CoverageDataManager myDataManager;
  private final Project myProject;

  public CoverageViewSuiteListener(CoverageDataManager dataManager, Project project) {
    myDataManager = dataManager;
    myProject = project;
  }

  public void beforeSuiteChosen() {
    final CoverageSuitesBundle suitesBundle = myDataManager.getCurrentSuitesBundle();
    if (suitesBundle != null) {
      CoverageViewManager.getInstance(myProject).closeView(CoverageViewManager.getDisplayName(suitesBundle));
    }
  }

  public void afterSuiteChosen() {
    final CoverageSuitesBundle suitesBundle = myDataManager.getCurrentSuitesBundle();
    if (suitesBundle == null) return;
    final CoverageViewManager viewManager = CoverageViewManager.getInstance(myProject);
    if (suitesBundle.getCoverageEngine().createCoverageViewExtension(myProject, suitesBundle, viewManager.getState()) != null) {
      viewManager.createToolWindow(CoverageViewManager.getDisplayName(suitesBundle), shouldActivate(suitesBundle));
    }
  }

  private static boolean shouldActivate(CoverageSuitesBundle suitesBundle) {
    final CoverageSuite[] suites = suitesBundle.getSuites();
    for (CoverageSuite suite : suites) {
      if (!(suite.getCoverageDataFileProvider() instanceof DefaultCoverageFileProvider)) return false; 
    }
    return true;
  }
}
