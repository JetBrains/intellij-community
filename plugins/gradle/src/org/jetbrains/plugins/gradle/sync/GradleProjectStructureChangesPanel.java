package org.jetbrains.plugins.gradle.sync;

import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleToolWindowPanel;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.PlatformFacade;
import org.jetbrains.plugins.gradle.ui.GradleDataKeys;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * UI control for showing difference between the gradle and intellij project structure.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:58 PM
 */
public class GradleProjectStructureChangesPanel extends GradleToolWindowPanel {

  private Tree                            myTree;
  private GradleProjectStructureTreeModel myTreeModel;
  private JPanel                          myContent;

  public GradleProjectStructureChangesPanel(@NotNull Project project,
                                            @NotNull GradleProjectStructureChangesModel model,
                                            @NotNull PlatformFacade platformFacade,
                                            @NotNull GradleProjectStructureHelper projectStructureHelper)
  {
    super(project, platformFacade, projectStructureHelper, GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE);
    model.addListener(new GradleProjectStructureChangeListener() {
      @Override
      public void onChanges(@NotNull final Collection<GradleProjectStructureChange> oldChanges,
                            @NotNull final Collection<GradleProjectStructureChange> currentChanges)
      {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            myTreeModel.update(currentChanges);
            myTreeModel.processObsoleteChanges(ContainerUtil.subtract(oldChanges, currentChanges));
          }
        });
      }
    });
  }

  private void init() {
    myContent = new JPanel(new GridBagLayout());
    myTreeModel = new GradleProjectStructureTreeModel(getProject(), getProjectFacade(), getProjectStructureHelper());
    myTree = new Tree(myTreeModel);
    applyInitialAppearance(myTree, (DefaultMutableTreeNode)myTreeModel.getRoot());

    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = constraints.weighty = 1;
    myContent.add(myTree, constraints);
    myContent.setBackground(myTree.getBackground());

    CustomizationUtil.installPopupHandler(myTree, GradleConstants.ACTION_GROUP_SYNC_TREE, GradleConstants.SYNC_TREE_PLACE);
  }
  
  @NotNull
  @Override
  protected JComponent buildContent() {
    init();
    return myContent;
  }

  @Override
  protected void updateContent() {
    // TODO den implement
    int i = 1;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (GradleDataKeys.SYNC_TREE_NODE.is(dataId)) {
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) {
        return null;
      }
      List<GradleProjectStructureNode<?>> result = new ArrayList<GradleProjectStructureNode<?>>();
      for (TreePath path : paths) {
        result.add((GradleProjectStructureNode<?>)path.getLastPathComponent());
      }
      return result;
    }
    else {
      return super.getData(dataId);
    }
  }

  private static void applyInitialAppearance(@NotNull Tree tree, @NotNull DefaultMutableTreeNode node) {
    if (node.getUserObject() == GradleProjectStructureTreeModel.DEPENDENCIES_NODE_DESCRIPTOR) {
      tree.expandPath(new TreePath(node.getPath()));
      return;
    }

    for (int i = 0; i < node.getChildCount(); i++) {
      applyInitialAppearance(tree, (DefaultMutableTreeNode)node.getChildAt(i));
    }
  }
}
