/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
    if (isFeatureEnabled("project.view.async.tree.model")) return null;
    return new ProjectTreeBuilder(myProject, myTree, treeModel, AlphaComparator.INSTANCE,
                                  (ProjectAbstractTreeStructureBase)myTreeStructure) {
      @Override
      protected AbstractTreeUpdater createUpdater() {
        return createTreeUpdater(this);
      }
    };
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
