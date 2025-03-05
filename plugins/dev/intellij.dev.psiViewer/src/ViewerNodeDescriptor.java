// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.psiViewer;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ViewerNodeDescriptor extends NodeDescriptor<Object> {
  private final @NotNull Object myElement;

  ViewerNodeDescriptor(@NotNull Project project, @NotNull Object element, NodeDescriptor<?> parentDescriptor) {
    super(project, parentDescriptor);
    myElement = element;
    myName = myElement.toString();
  }

  @Override
  public boolean update() {
    return false;
  }

  @Override
  public @NotNull Object getElement() {
    return myElement;
  }
}
