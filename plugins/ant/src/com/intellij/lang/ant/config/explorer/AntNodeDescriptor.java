package com.intellij.lang.ant.config.explorer;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CellAppearance;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

abstract class AntNodeDescriptor extends NodeDescriptor implements CellAppearance {
  public AntNodeDescriptor(Project project, NodeDescriptor parentDescriptor) {
    super(project, parentDescriptor);
  }

  public abstract boolean isAutoExpand();

  public void customize(SimpleColoredComponent component) {
    component.append(toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public String getText() {
    return toString();
  }
}
