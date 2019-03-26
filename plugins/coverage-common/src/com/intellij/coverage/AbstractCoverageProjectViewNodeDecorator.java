// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public abstract class AbstractCoverageProjectViewNodeDecorator implements ProjectViewNodeDecorator {
  @Nullable
  private final CoverageDataManager myCoverageDataManager;

  public AbstractCoverageProjectViewNodeDecorator(@Nullable CoverageDataManager coverageDataManager) {
    myCoverageDataManager = coverageDataManager;
  }

  public AbstractCoverageProjectViewNodeDecorator(@NotNull Project project) {
    myCoverageDataManager = CoverageDataManager.getInstance(project);
  }

  @Nullable
  protected CoverageDataManager getCoverageDataManager() {
    return myCoverageDataManager;
  }

  protected static void appendCoverageInfo(ColoredTreeCellRenderer cellRenderer, String coverageInfo) {
    if (coverageInfo != null) {
      cellRenderer.append(" (" + coverageInfo + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}
