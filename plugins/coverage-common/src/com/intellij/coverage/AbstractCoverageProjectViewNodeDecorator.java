package com.intellij.coverage;

import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

/**
 * @author Roman.Chernyatchik
 */
public abstract class AbstractCoverageProjectViewNodeDecorator implements ProjectViewNodeDecorator {
  private final CoverageDataManager myCoverageDataManager;
    
  public AbstractCoverageProjectViewNodeDecorator(final CoverageDataManager coverageDataManager) {
    myCoverageDataManager = coverageDataManager;
  }

  protected CoverageDataManager getCoverageDataManager() {
    return myCoverageDataManager;
  }

  protected static void appendCoverageInfo(ColoredTreeCellRenderer cellRenderer, String coverageInfo) {
    if (coverageInfo != null) {
      cellRenderer.append(" (" + coverageInfo + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}
