/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 10/6/2016
 */
public class GradleProjectCompositeSelectorDialog extends DialogWrapper {

  private static final int MAX_PATH_LENGTH = 40;
  @NotNull
  private final Project myProject;
  @Nullable
  private final GradleProjectSettings myCompositeRootSettings;
  private JPanel mainPanel;
  private JPanel contentPanel;
  @SuppressWarnings("unused")
  private JBLabel myDescriptionLbl;
  private ExternalSystemUiAware myExternalSystemUiAware;
  private CheckboxTree myTree;

  public GradleProjectCompositeSelectorDialog(@NotNull Project project, String compositeRootProjectPath) {
    super(project, true);
    myProject = project;
    myCompositeRootSettings = GradleSettings.getInstance(myProject).getLinkedProjectSettings(compositeRootProjectPath);
    myExternalSystemUiAware = ExternalSystemUiUtil.getUiAware(GradleConstants.SYSTEM_ID);
    myTree = createTree();

    setTitle(String.format("%s Project Build Composite", GradleConstants.SYSTEM_ID.getReadableName()));
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTree).
      addExtraAction(new SelectAllButton()).
      addExtraAction(new UnselectAllButton()).
      setToolbarPosition(ActionToolbarPosition.BOTTOM).
      setToolbarBorder(IdeBorderFactory.createEmptyBorder());
    contentPanel.add(decorator.createPanel());
    return mainPanel;
  }

  @Override
  protected void doOKAction() {
    if (myCompositeRootSettings != null) {
      Pair[] pairs = myTree.getCheckedNodes(Pair.class, null);
      Set<String> compositeParticipants = new HashSet<>();
      for (Pair pair : pairs) {
        compositeParticipants.add(pair.second.toString());
      }
      myCompositeRootSettings.setCompositeParticipants(compositeParticipants.isEmpty() ? null : compositeParticipants);
    }
    super.doOKAction();
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();
  }

  @Override
  public void dispose() {
    super.dispose();
  }

  private CheckboxTree createTree() {
    final CheckedTreeNode root = new CheckedTreeNode();
    if (myCompositeRootSettings != null) {
      List<TreeNode> nodes = ContainerUtil.newArrayList();
      for (GradleProjectSettings projectSettings : GradleSettings.getInstance(myProject).getLinkedProjectsSettings()) {
        if (projectSettings == myCompositeRootSettings) continue;
        boolean added = myCompositeRootSettings.getCompositeParticipants().contains(projectSettings.getExternalProjectPath());

        String representationName = myExternalSystemUiAware.getProjectRepresentationName(
          projectSettings.getExternalProjectPath(), projectSettings.getExternalProjectPath());
        CheckedTreeNode treeNode = new CheckedTreeNode(Pair.create(representationName, projectSettings.getExternalProjectPath()));
        treeNode.setChecked(added);
        nodes.add(treeNode);
      }

      TreeUtil.addChildrenTo(root, nodes);
    }

    final CheckboxTree tree = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer(true, false) {

      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (!(value instanceof CheckedTreeNode)) return;
        CheckedTreeNode node = (CheckedTreeNode)value;

        if (!(node.getUserObject() instanceof Pair)) return;
        Pair pair = (Pair)node.getUserObject();

        ColoredTreeCellRenderer renderer = getTextRenderer();
        renderer.setIcon(myExternalSystemUiAware.getProjectIcon());
        String projectName = (String)pair.first;
        renderer.append(projectName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        String projectPath = StringUtil.trimMiddle((String)pair.second, MAX_PATH_LENGTH);
        renderer.append(" (" + projectPath + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
        setToolTipText((String)pair.second);
      }
    }, root);

    TreeUtil.expand(tree, 1);
    return tree;
  }

  private void walkTree(Consumer<CheckedTreeNode> consumer) {
    final TreeModel treeModel = myTree.getModel();
    final Object root = treeModel.getRoot();
    if (!(root instanceof CheckedTreeNode)) return;

    for (TreeNode node : TreeUtil.childrenToArray((CheckedTreeNode)root)) {
      if (!(node instanceof CheckedTreeNode)) continue;
      consumer.consume(((CheckedTreeNode)node));
    }
  }

  private class SelectAllButton extends AnActionButton {
    public SelectAllButton() {
      super("Select All", AllIcons.Actions.Selectall);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      walkTree(node -> node.setChecked(true));
      ((DefaultTreeModel)myTree.getModel()).reload();
    }
  }

  private class UnselectAllButton extends AnActionButton {
    public UnselectAllButton() {
      super("Unselect All", AllIcons.Actions.Unselectall);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      walkTree(node -> node.setChecked(false));
      ((DefaultTreeModel)myTree.getModel()).reload();
    }
  }
}
