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
import java.util.List;

final class AntExplorerTreeBuilder extends AbstractTreeBuilder {

  private static final TreePath[] EMPTY_TREE_PATH = new TreePath[0];
  private final AntConfigurationListener myAntBuildListener;
  private final Project myProject;
  private AntConfiguration myConfig;

  public AntExplorerTreeBuilder(Project project, JTree tree, DefaultTreeModel treeModel) {
    super(tree, treeModel, new AntExplorerTreeStructure(project), IndexComparator.INSTANCE);
    myProject = project;
    myAntBuildListener = new ConfigurationListener();
    myConfig = AntConfiguration.getInstance(myProject);
    myConfig.addAntConfigurationListener(myAntBuildListener);
    initRootNode();
  }

  public void dispose() {
    final AntConfiguration config = myConfig;
    if (config != null) {
      config.removeAntConfigurationListener(myAntBuildListener);
      myConfig = null;
    }
    super.dispose();
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return false;
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return ((AntNodeDescriptor)nodeDescriptor).isAutoExpand();
  }

  public void setTargetsFiltered(boolean value) {
    ((AntExplorerTreeStructure)getTreeStructure()).setFilteredTargets(value);
    refresh();
  }

  public final void refresh() {
    final List<Object> pathsToExpand = new ArrayList<Object>();
    final List<Object> selectionPaths = new ArrayList<Object>();
    TreeBuilderUtil.storePaths(this, getRootNode(), pathsToExpand, selectionPaths, true);
    ApplicationManager.getApplication().runReadAction(
      new Runnable() {
        public void run() {
          queueUpdate();
        }
      }
    );
    getTree().setSelectionPaths(EMPTY_TREE_PATH);
    TreeBuilderUtil.restorePaths(this, pathsToExpand, selectionPaths, true);
  }

  protected ProgressIndicator createProgressIndicator() {
    return super.createProgressIndicator();//new StatusBarProgress();
  }

  private final class ConfigurationListener implements AntConfigurationListener {
    public void configurationLoaded() {
      queueUpdate();
    }

    public void buildFileAdded(AntBuildFile buildFile) {
      queueUpdate();
    }

    public void buildFileChanged(AntBuildFile buildFile) {
      queueUpdateFrom(buildFile, false);
    }

    public void buildFileRemoved(AntBuildFile buildFile) {
      queueUpdate();
    }
  }

  public void expandAll() {
    final List<Object> pathsToExpand = new ArrayList<Object>();
    final List<Object> selectionPaths = new ArrayList<Object>();
    TreeBuilderUtil.storePaths(this, getRootNode(), pathsToExpand, selectionPaths, true);
    int row = 0;
    while (row < getTree().getRowCount()) {
      getTree().expandRow(row);
      row++;
    }
    getTree().setSelectionPaths(EMPTY_TREE_PATH);
    TreeBuilderUtil.restorePaths(this, pathsToExpand, selectionPaths, true);
  }

  void collapseAll() {
    final List<Object> pathsToExpand = new ArrayList<Object>();
    final List<Object> selectionPaths = new ArrayList<Object>();
    TreeBuilderUtil.storePaths(this, getRootNode(), pathsToExpand, selectionPaths, true);
    TreeUtil.collapseAll(getTree(), 1);
    getTree().setSelectionPaths(EMPTY_TREE_PATH);
    pathsToExpand.clear();
    TreeBuilderUtil.restorePaths(this, pathsToExpand, selectionPaths, true);
  }
}
