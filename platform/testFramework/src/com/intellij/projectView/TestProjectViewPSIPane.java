// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.projectView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.impl.AbstractProjectViewPaneWithAsyncSupport;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

@ApiStatus.Internal
public final class TestProjectViewPSIPane extends AbstractProjectViewPaneWithAsyncSupport {
  private final TestProjectTreeStructure myTestTreeStructure;
  private final int myWeight;

  public TestProjectViewPSIPane(Project project, TestProjectTreeStructure treeStructure, int weight) {
    super(project);
    myTestTreeStructure = treeStructure;
    myWeight = weight;
  }

  @Override
  public @NotNull SelectInTarget createSelectInTarget() {
    return new SelectInTarget() {
      @Override
      public boolean canSelect(SelectInContext context) {
        return false;
      }

      @Override
      public void selectIn(SelectInContext context, boolean requestFocus) {

      }

      @Override
      public String getMinorViewId() {
        return getId();
      }
    };
  }

  @Override
  protected @NotNull ProjectAbstractTreeStructureBase createStructure() {
    return myTestTreeStructure;
  }

  @Override
  protected @NotNull ProjectViewTree createTree(@NotNull DefaultTreeModel treeModel) {
    return new MyProjectViewTree(treeModel);
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.General.ProjectTab;
  }

  @Override
  public @NotNull String getId() {
    return "";
  }

  @Override
  public @NotNull String getTitle() {
    return "";
  }

  @Override
  public int getWeight() {
    return myWeight;
  }

  @Override
  public boolean supportsManualOrder() {
    return true;
  }

  private static class MyProjectViewTree extends ProjectViewTree {
    private MyProjectViewTree(@NotNull DefaultTreeModel treeModel) {
      super(treeModel);
    }
  }
}
