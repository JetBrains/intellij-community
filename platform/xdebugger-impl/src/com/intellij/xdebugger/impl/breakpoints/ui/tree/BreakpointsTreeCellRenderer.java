// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.tree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.grouping.XBreakpointCustomGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class BreakpointsTreeCellRenderer  {
  private static final Logger LOG = Logger.getInstance(BreakpointsTreeCellRenderer.class);
  private static final SimpleTextAttributes SIMPLE_CELL_ATTRIBUTES_BOLD = SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES.derive(SimpleTextAttributes.STYLE_BOLD, null, null, null);

  private static void customizeRenderer(Project project,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        ColoredTreeCellRenderer renderer) {
    if (value instanceof BreakpointItemNode node) {
      BreakpointItem breakpoint = node.getBreakpointItem();
      breakpoint.setupRenderer(renderer, project, selected);
    }
    else if (value instanceof BreakpointsGroupNode) {
      XBreakpointGroup group = ((BreakpointsGroupNode<?>)value).getGroup();
      renderer.setIcon(group.getIcon(expanded));
      if (group instanceof XBreakpointCustomGroup && ((XBreakpointCustomGroup)group).isDefault()) {
        renderer.append(group.getName(), SIMPLE_CELL_ATTRIBUTES_BOLD);
      }
      else {
        renderer.append(group.getName(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
      }
    }
  }

  public static class BreakpointsCheckboxTreeCellRenderer extends CheckboxTree.CheckboxTreeCellRenderer {
    private final Project myProject;

    public BreakpointsCheckboxTreeCellRenderer(Project project) {
      myProject = project;
    }

    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      ColoredTreeCellRenderer textRenderer = getTextRenderer();
      try {
        BreakpointsTreeCellRenderer.customizeRenderer(myProject, value, selected, expanded, textRenderer);
      }
      catch (Exception e) {
        LOG.error(e);
        textRenderer.clear();
        textRenderer.append(e.getMessage());
      }
    }
  }

  public static class BreakpointsSimpleTreeCellRenderer extends ColoredTreeCellRenderer {
    private final Project myProject;

    public BreakpointsSimpleTreeCellRenderer(Project project) {
      myProject = project;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      customizeRenderer(myProject, value, selected, expanded, this);
    }
  }
}
