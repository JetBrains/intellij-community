// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractCoverageProjectViewNodeDecorator implements ProjectViewNodeDecorator {
  private Project myProject;

  public AbstractCoverageProjectViewNodeDecorator(@NotNull Project project) {
    myProject = project;
  }

  /**
   * @deprecated Use {@link #getCoverageDataManager(Project)}
   * @return
   */
  @Nullable
  @Deprecated(forRemoval = true)
  protected final CoverageDataManager getCoverageDataManager() {
    return getCoverageDataManager(myProject);
  }

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  protected final CoverageDataManager getCoverageDataManager(@NotNull Project project) {
    return CoverageDataManager.getInstance(project);
  }

  protected static void appendCoverageInfo(ColoredTreeCellRenderer cellRenderer, @Nls String coverageInfo) {
    if (coverageInfo != null) {
      cellRenderer.append(" (" + coverageInfo + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}
