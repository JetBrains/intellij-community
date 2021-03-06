// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures;

import com.intellij.analysis.problemsView.toolWindow.ProblemsView;
import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.fest.swing.edt.GuiActionRunner.execute;

/**
 * Fixture for the Inspections window in the IDE
 */
public class InspectionsFixture extends ToolWindowFixture {
  private final InspectionTree myTree;

  public InspectionsFixture(@NotNull Robot robot, @NotNull Project project, InspectionTree tree) {
    super(ProblemsView.ID, project, robot);
    myTree = tree;
  }

  public String getResults() {
    activate();
    waitUntilIsVisible();

    return execute(new GuiQuery<>() {
      @Override
      @NotNull
      protected String executeInEDT() {
        StringBuilder sb = new StringBuilder();
        describe(myTree.getInspectionTreeModel().getRoot(), sb, 0);
        return sb.toString();
      }
    });
  }

  public static void describe(@NotNull InspectionTreeNode node, @NotNull StringBuilder sb, int depth) {
    sb.append("    ".repeat(depth));
    sb.append(node);
    sb.append("\n");

    // The exact order of the results sometimes varies so sort the children alphabetically
    // instead to ensure stable test output
    List<InspectionTreeNode> children = new ArrayList<>(node.getChildren());
    children.sort(Comparator.comparing(Object::toString));
    for (InspectionTreeNode child : children) {
      describe(child, sb, depth + 1);
    }
  }
}