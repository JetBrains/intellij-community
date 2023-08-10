// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;


public class WolfChangesFileNameDecorator extends ChangesFileNameDecorator {
  private final WolfTheProblemSolver myProblemSolver;

  public WolfChangesFileNameDecorator(@NotNull Project project) {
    myProblemSolver = WolfTheProblemSolver.getInstance(project);
  }

  @Override
  public void appendFileName(final ChangesBrowserNodeRenderer renderer, final VirtualFile vFile, final String fileName, final Color color, final boolean highlightProblems) {
    int style = SimpleTextAttributes.STYLE_PLAIN;
    Color underlineColor = null;
    if (highlightProblems && vFile != null && !vFile.isDirectory() && myProblemSolver.isProblemFile(vFile)) {
      underlineColor = JBColor.RED;
      style = SimpleTextAttributes.STYLE_WAVED;
    }
    renderer.append(fileName, new SimpleTextAttributes(style, color, underlineColor));
  }
}
