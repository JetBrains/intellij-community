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
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileGroup;
import com.intellij.lang.ant.config.AntBuildFileGroupManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.treeStructure.Tree;

/**
 * @author VISTALL
 * @since 12:17/09.03.13
 */
public class CreateNewGroupAction extends AnAction {

  private final AntBuildFileGroup myGroup;
  private final Tree myTree;

  public CreateNewGroupAction(AntBuildFileGroup group, Tree tree) {
    super(AntBundle.message("new.group.or.subgroup"), null, AllIcons.General.Add);
    myGroup = group;
    myTree = tree;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final AntBuildFileGroupManager groupManager = AntBuildFileGroupManager.getInstance(e.getProject());

    String text = Messages.showInputDialog(e.getProject(), "Name: ", "Enter Group Name", null, null, new InputValidator() {
      @Override
      public boolean checkInput(String inputString) {
        if(inputString.isEmpty()) {
          return false;
        }
        AntBuildFileGroup[] groups;
        if(myGroup == null) {
          groups = groupManager.getFirstLevelGroups();
        }
        else {
          groups = myGroup.getChildren();
        }

        for (AntBuildFileGroup group : groups) {
          if(group.getName().equalsIgnoreCase(inputString)) {
            return false;
          }
        }
        return true;
      }

      @Override
      public boolean canClose(String inputString) {
        return checkInput(inputString);
      }
    });

    if (StringUtil.notNullize(text).isEmpty()) {
      return;
    }


    final AntBuildFileGroup group = groupManager.createGroup(myGroup, text);

    final AntBuildFile buildFile = MoveToThisGroupAction.findBuildFile(myTree);
    if (buildFile != null) {
      groupManager.moveToGroup(buildFile, group);
    }

    final AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(myTree);
    if (builder != null) {
      builder.queueUpdate();
    }
  }
}
