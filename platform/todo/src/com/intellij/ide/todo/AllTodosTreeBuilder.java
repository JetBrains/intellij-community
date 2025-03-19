// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public class AllTodosTreeBuilder extends TodoTreeBuilder {

  public AllTodosTreeBuilder(@NotNull JTree tree,
                             @NotNull Project project) {
    super(tree, project);
  }

  @Override
  protected @NotNull TodoTreeStructure createTreeStructure() {
    return new AllTodosTreeStructure(myProject);
  }
}
