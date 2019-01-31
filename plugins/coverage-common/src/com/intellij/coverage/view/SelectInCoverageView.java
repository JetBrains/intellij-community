package com.intellij.coverage.view;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class SelectInCoverageView implements  SelectInTarget {
  private final Project myProject;

  public SelectInCoverageView(Project project) {
    myProject = project;
  }

  public String toString() {
    return CoverageViewManager.TOOLWINDOW_ID;
  }

  @Override
  public boolean canSelect(final SelectInContext context) {
    final CoverageSuitesBundle suitesBundle = CoverageDataManager.getInstance(myProject).getCurrentSuitesBundle();
    if (suitesBundle != null) {
      final CoverageView coverageView = CoverageViewManager.getInstance(myProject).getToolwindow(suitesBundle);
      if (coverageView != null) {
        final VirtualFile file = context.getVirtualFile();
        return !file.isDirectory() && coverageView.canSelect(file);
      }
    }
    return false;
  }

  @Override
  public void selectIn(final SelectInContext context, final boolean requestFocus) {
    final CoverageSuitesBundle suitesBundle = CoverageDataManager.getInstance(myProject).getCurrentSuitesBundle();
    if (suitesBundle != null) {
      final CoverageViewManager coverageViewManager = CoverageViewManager.getInstance(myProject);
      final CoverageView coverageView = coverageViewManager.getToolwindow(suitesBundle);
      coverageView.select(context.getVirtualFile());
      coverageViewManager.activateToolwindow(coverageView, requestFocus);
    }
  }

  @Override
  public String getToolWindowId() {
    return CoverageViewManager.TOOLWINDOW_ID;
  }

  @Override
  public String getMinorViewId() {
    return null;
  }

  @Override
  public float getWeight() {
    return StandardTargetWeights.STRUCTURE_WEIGHT + 0.5f;
  }
}
