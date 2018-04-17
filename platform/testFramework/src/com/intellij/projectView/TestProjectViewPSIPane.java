// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectTreeBuilder;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

import static com.intellij.openapi.application.Experiments.isFeatureEnabled;

/**
* @author yole
*/
class TestProjectViewPSIPane extends AbstractProjectViewPSIPane {
  private final TestProjectTreeStructure myTestTreeStructure;
  private final int myWeight;

  public TestProjectViewPSIPane(Project project, TestProjectTreeStructure treeStructure, int weight) {
    super(project);
    myTestTreeStructure = treeStructure;
    myWeight = weight;
  }

  @Override
  public SelectInTarget createSelectInTarget() {
    return null;
  }

  @Override
  protected AbstractTreeUpdater createTreeUpdater(AbstractTreeBuilder treeBuilder) {
    return new AbstractTreeUpdater(treeBuilder);
  }

  @Override
  protected BaseProjectTreeBuilder createBuilder(DefaultTreeModel treeModel) {
    return null;
  }

  @Override
  protected ProjectAbstractTreeStructureBase createStructure() {
    return myTestTreeStructure;
  }

  @Override
  protected ProjectViewTree createTree(DefaultTreeModel treeModel) {
    return new ProjectViewTree(treeModel) {
    };
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  @NotNull
  public String getId() {
    return "";
  }

  @Override
  public String getTitle() {
    return null;
  }

  @Override
  public int getWeight() {
    return myWeight;
  }
}
