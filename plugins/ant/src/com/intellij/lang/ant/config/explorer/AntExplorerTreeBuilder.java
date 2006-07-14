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
    TreeBuilderUtil.storePaths(this, myRootNode, pathsToExpand, selectionPaths, true);
    ((AntExplorerTreeStructure)myTreeStructure).setFilteredTargets(value);
    ApplicationManager.getApplication().runReadAction(
      new Runnable() {
        public void run() {
          updateFromRoot();
        }
      }
    );
    myTree.setSelectionPaths(new TreePath[0]);
    TreeBuilderUtil.restorePaths(this, pathsToExpand, selectionPaths, true);
  }

  public void refresh() {
    ArrayList pathsToExpand = new ArrayList();
    ArrayList selectionPaths = new ArrayList();
    TreeBuilderUtil.storePaths(this, myRootNode, pathsToExpand, selectionPaths, true);
    ApplicationManager.getApplication().runReadAction(
      new Runnable() {
        public void run() {
          updateFromRoot();
        }
      }
    );
    myTree.setSelectionPaths(new TreePath[0]);
    TreeBuilderUtil.restorePaths(this, pathsToExpand, selectionPaths, true);
  }

  protected ProgressIndicator createProgressIndicator() {
    return super.createProgressIndicator();//new StatusBarProgress();
  }

  private final class ConfigurationListener implements AntConfigurationListener {
    public void buildFileAdded(AntBuildFile buildFile) {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }

    public void buildFileChanged(AntBuildFile buildFile) {
      myUpdater.addSubtreeToUpdateByElement(buildFile);
    }

    public void buildFileRemoved(AntBuildFile buildFile) {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }
  }

  void expandAll() {
    ArrayList pathsToExpand = new ArrayList();
    ArrayList selectionPaths = new ArrayList();
    TreeBuilderUtil.storePaths(this, myRootNode, pathsToExpand, selectionPaths, true);
    int row = 0;
    while (row < myTree.getRowCount()) {
      myTree.expandRow(row);
      row++;
    }
    myTree.setSelectionPaths(new TreePath[0]);
    TreeBuilderUtil.restorePaths(this, pathsToExpand, selectionPaths, true);
  }

  void collapseAll() {
    ArrayList pathsToExpand = new ArrayList();
    ArrayList selectionPaths = new ArrayList();
    TreeBuilderUtil.storePaths(this, myRootNode, pathsToExpand, selectionPaths, true);
    TreeUtil.collapseAll(myTree, 1);
    myTree.setSelectionPaths(new TreePath[0]);
    pathsToExpand.clear();
    TreeBuilderUtil.restorePaths(this, pathsToExpand, selectionPaths, true);
  }
}
