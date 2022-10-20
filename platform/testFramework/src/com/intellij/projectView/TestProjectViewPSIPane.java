// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.impl.AbstractProjectViewPaneWithAsyncSupport;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

class TestProjectViewPSIPane extends AbstractProjectViewPaneWithAsyncSupport {
  private final TestProjectTreeStructure myTestTreeStructure;
  private final int myWeight;

  TestProjectViewPSIPane(Project project, TestProjectTreeStructure treeStructure, int weight) {
    super(project);
    myTestTreeStructure = treeStructure;
    myWeight = weight;
  }

  @NotNull
  @Override
  public SelectInTarget createSelectInTarget() {
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

  @NotNull
  @Override
  protected ProjectAbstractTreeStructureBase createStructure() {
    return myTestTreeStructure;
  }

  @NotNull
  @Override
  protected ProjectViewTree createTree(@NotNull DefaultTreeModel treeModel) {
    return new MyProjectViewTree(treeModel);
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return AllIcons.General.ProjectTab;
  }

  @Override
  @NotNull
  public String getId() {
    return "";
  }

  @NotNull
  @Override
  public String getTitle() {
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
