// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.explorer;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

abstract class AntNodeDescriptor extends NodeDescriptor implements CellAppearanceEx {
  AntNodeDescriptor(Project project, NodeDescriptor parentDescriptor) {
    super(project, parentDescriptor);
  }

  @Override
  public void customize(@NotNull SimpleColoredComponent component) {
    component.append(toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  @Override
  public @NotNull @Nls String getText() {
    return toString();
  }
}