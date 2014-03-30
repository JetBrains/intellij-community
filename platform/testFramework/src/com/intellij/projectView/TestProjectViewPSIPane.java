package com.intellij.projectView;

import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectTreeBuilder;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
* @author yole
*/
class TestProjectViewPSIPane extends AbstractProjectViewPSIPane {
  private TestProjectTreeStructure myTestTreeStructure;

  public TestProjectViewPSIPane(Project project, TestProjectTreeStructure treeStructure) {
    super(project);
    myTestTreeStructure = treeStructure;
  }

  @Override
  public SelectInTarget createSelectInTarget() {
    return null;
  }

  @NonNls
  public String getComponentName() {
    return "comp name";
  }

  @Override
  protected AbstractTreeUpdater createTreeUpdater(AbstractTreeBuilder treeBuilder) {
    return new AbstractTreeUpdater(treeBuilder);
  }

  @Override
  @NotNull
  protected BaseProjectTreeBuilder createBuilder(DefaultTreeModel treeModel) {
    return new ProjectTreeBuilder(myProject, myTree, treeModel, AlphaComparator.INSTANCE,
                                  (ProjectAbstractTreeStructureBase)myTreeStructure) {
      @Override
      protected AbstractTreeUpdater createUpdater() {
        return createTreeUpdater(this);
      }

      protected void addTaskToWorker(final Runnable runnable, boolean first, final Runnable postRunnable) {
        runnable.run();
        postRunnable.run();
      }
    };
  }

  @Override
  protected ProjectAbstractTreeStructureBase createStructure() {
    return myTestTreeStructure;
  }

  @Override
  protected ProjectViewTree createTree(DefaultTreeModel treeModel) {
    return new ProjectViewTree(myProject, treeModel) {
      @Override
      public DefaultMutableTreeNode getSelectedNode() {
        return null;
      }
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
    return 0;
  }

  public void projectOpened() {
    final Runnable runnable = new DumbAwareRunnable() {
      @Override
      public void run() {
        final ProjectView projectView = ProjectView.getInstance(myProject);
        projectView.addProjectPane(TestProjectViewPSIPane.this);
      }
    };
    StartupManager.getInstance(myProject).registerPostStartupActivity(runnable);
  }

  public void projectClosed() {
  }

  public void initComponent() { }

  public void disposeComponent() {

  }
}
