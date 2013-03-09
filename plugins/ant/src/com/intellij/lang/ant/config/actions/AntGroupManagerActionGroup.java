/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.actions;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFileGroup;
import com.intellij.lang.ant.config.AntBuildFileGroupManager;
import com.intellij.lang.ant.config.explorer.AntBuildFileNodeDescriptor;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author VISTALL
 * @since 12:19/09.03.13
 */
public class AntGroupManagerActionGroup extends ActionGroup {
  private final AnAction[] myDefaultActions;
  private final AntBuildFileGroup myGroup;
  private final Tree myTree;

  public AntGroupManagerActionGroup(AntBuildFileGroup group, Tree tree) {
    super(group == null ? AntBundle.message("move.to") : group.getName(), true);
    myGroup = group;
    myTree = tree;
    if(myGroup != null) {
      getTemplatePresentation().setIcon(AllIcons.Ant.BuildGroup);
    }
    myDefaultActions = new AnAction[] {new CreateNewGroupAction(group, tree), new MoveToThisGroupAction(tree, group)};
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    Project project = e.getProject();
    if(project == null) {
      return AnAction.EMPTY_ARRAY;
    }

    List<AnAction> actions = new ArrayList<AnAction>();
    Collections.addAll(actions, myDefaultActions);

    AntBuildFileGroup[] groups;
    if(myGroup == null) {
      groups = AntBuildFileGroupManager.getInstance(project).getFirstLevelGroups();
    }
    else {
      groups = myGroup.getChildren();
    }

    if(groups.length != 0) {
      actions.add(Separator.getInstance());
      for (AntBuildFileGroup group : groups) {
        actions.add(new AntGroupManagerActionGroup(group, myTree));
      }
    }

    return actions.toArray(new AnAction[actions.size()]);
  }

  @Override
  public void update(AnActionEvent e) {
    final Object lastSelectedPathComponent = myTree.getLastSelectedPathComponent();
    e.getPresentation().setEnabledAndVisible(lastSelectedPathComponent instanceof DefaultMutableTreeNode && ((DefaultMutableTreeNode)lastSelectedPathComponent).getUserObject() instanceof AntBuildFileNodeDescriptor);
  }
}
