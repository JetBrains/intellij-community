/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.explorer;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.TreeBuilderUtil;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;

final class AntExplorerTreeBuilder extends AbstractTreeBuilder {

  private final AntConfigurationListener myAntBuildListener;
  private final Project myProject;
  
  public AntExplorerTreeBuilder(Project project, JTree tree, DefaultTreeModel treeModel) {
    super(tree, treeModel, new AntExplorerTreeStructure(project), IndexComparator.INSTANCE);
    myProject = project;
    myAntBuildListener = new ConfigurationListener();
    AntConfiguration.getInstance(myProject).addAntConfigurationListener(myAntBuildListener);
    initRootNode();
  }

  public void dispose() {
    super.dispose();
    AntConfiguration.getInstance(myProject).removeAntConfigurationListener(myAntBuildListener);
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return false;
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return ((AntNodeDescriptor)nodeDescriptor).isAutoExpand();
  }

  public void setTargetsFiltered(boolean value) {
    ArrayList pathsToExpand = new ArrayList();
    ArrayList selectionPaths = new ArrayList();
    TreeBuilderUtil.storePaths(this, getRootNode(), pathsToExpand, selectionPaths, true);
    ((AntExplorerTreeStructure)getTreeStructure()).setFilteredTargets(value);
    ApplicationManager.getApplication().runReadAction(
      new Runnable() {
        public void run() {
          updateFromRoot();
        }
      }
    );
    getTree().setSelectionPaths(new TreePath[0]);
    TreeBuilderUtil.restorePaths(this, pathsToExpand, selectionPaths, true);
  }

  public void refresh() {
    ArrayList pathsToExpand = new ArrayList();
    ArrayList selectionPaths = new ArrayList();
    TreeBuilderUtil.storePaths(this, getRootNode(), pathsToExpand, selectionPaths, true);
    ApplicationManager.getApplication().runReadAction(
      new Runnable() {
        public void run() {
          updateFromRoot();
        }
      }
    );
    getTree().setSelectionPaths(new TreePath[0]);
    TreeBuilderUtil.restorePaths(this, pathsToExpand, selectionPaths, true);
  }

  protected ProgressIndicator createProgressIndicator() {
    return super.createProgressIndicator();//new StatusBarProgress();
  }

  private final class ConfigurationListener implements AntConfigurationListener {
    public void configurationLoaded() {
      getUpdater().addSubtreeToUpdate(getRootNode());
    }

    public void buildFileAdded(AntBuildFile buildFile) {
      getUpdater().addSubtreeToUpdate(getRootNode());
    }

    public void buildFileChanged(AntBuildFile buildFile) {
      getUpdater().addSubtreeToUpdateByElement(buildFile);
    }

    public void buildFileRemoved(AntBuildFile buildFile) {
      getUpdater().addSubtreeToUpdate(getRootNode());
    }
  }

  public void expandAll() {
    ArrayList pathsToExpand = new ArrayList();
    ArrayList selectionPaths = new ArrayList();
    TreeBuilderUtil.storePaths(this, getRootNode(), pathsToExpand, selectionPaths, true);
    int row = 0;
    while (row < getTree().getRowCount()) {
      getTree().expandRow(row);
      row++;
    }
    getTree().setSelectionPaths(new TreePath[0]);
    TreeBuilderUtil.restorePaths(this, pathsToExpand, selectionPaths, true);
  }

  void collapseAll() {
    ArrayList pathsToExpand = new ArrayList();
    ArrayList selectionPaths = new ArrayList();
    TreeBuilderUtil.storePaths(this, getRootNode(), pathsToExpand, selectionPaths, true);
    TreeUtil.collapseAll(getTree(), 1);
    getTree().setSelectionPaths(new TreePath[0]);
    pathsToExpand.clear();
    TreeBuilderUtil.restorePaths(this, pathsToExpand, selectionPaths, true);
  }
}
