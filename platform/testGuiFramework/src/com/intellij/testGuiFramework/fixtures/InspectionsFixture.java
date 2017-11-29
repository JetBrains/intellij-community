/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.fixtures;

import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.fest.swing.edt.GuiActionRunner.execute;

/**
 * Fixture for the Inspections window in the IDE
 */
public class InspectionsFixture extends ToolWindowFixture {
  private final InspectionTree myTree;

  public InspectionsFixture(@NotNull Robot robot, @NotNull Project project, InspectionTree tree) {
    super(ToolWindowId.INSPECTION, project, robot);
    myTree = tree;
  }

  public String getResults() {
    activate();
    waitUntilIsVisible();

    return execute(new GuiQuery<String>() {
      @Override
      @Nullable
      protected String executeInEDT() throws Throwable {
        StringBuilder sb = new StringBuilder();
        InspectionsFixture.describe(myTree.getRoot(), sb, 0);
        return sb.toString();
      }
    });
  }

  public static void describe(@NotNull InspectionTreeNode node, @NotNull StringBuilder sb, int depth) {
    for (int i = 0; i < depth; i++) {
      sb.append("    ");
    }
    sb.append(node.toString());
    sb.append("\n");

    // The exact order of the results sometimes varies so sort the children alphabetically
    // instead to ensure stable test output
    List<InspectionTreeNode> children = new ArrayList<>(node.getChildCount());
    for (int i = 0, n = node.getChildCount(); i < n; i++) {
      children.add((InspectionTreeNode)node.getChildAt(i));
    }
    Collections.sort(children, (node1, node2) -> node1.toString().compareTo(node2.toString()));
    for (InspectionTreeNode child : children) {
      describe(child, sb, depth + 1);
    }
  }
}