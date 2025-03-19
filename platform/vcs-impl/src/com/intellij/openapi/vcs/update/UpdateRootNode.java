// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class UpdateRootNode extends GroupTreeNode {

  private final Project myProject;

  public UpdateRootNode(UpdatedFiles updatedFiles, Project project, @Nls String rootName, ActionInfo actionInfo) {
    super(rootName, false, SimpleTextAttributes.ERROR_ATTRIBUTES, project, Collections.emptyMap(), null);
    myProject = project;

    addGroupsToNode(updatedFiles.getTopLevelGroups(), this, actionInfo);
  }

  private void addGroupsToNode(List<? extends FileGroup> groups, AbstractTreeNode owner, ActionInfo actionInfo) {
    for (FileGroup fileGroup : groups) {
      GroupTreeNode node = addFileGroup(fileGroup, owner, actionInfo);
      if (node != null) {
        addGroupsToNode(fileGroup.getChildren(), node, actionInfo);
      }
    }
  }

  private @Nullable GroupTreeNode addFileGroup(FileGroup fileGroup, AbstractTreeNode parent, ActionInfo actionInfo) {
    if (fileGroup.isEmpty()) {
      return null;
    }
    GroupTreeNode group = new GroupTreeNode(actionInfo.getGroupName(fileGroup), fileGroup.getSupportsDeletion(),
                                            fileGroup.getInvalidAttributes(), myProject, fileGroup.getErrorsMap(), fileGroup.getId());
    Disposer.register(this, group);
    parent.add(group);
    for (final String s : fileGroup.getFiles()) {
      group.addFilePath(s);
    }
    return group;
  }

  @Override
  public boolean getSupportsDeletion() {
    return false;
  }
}
