// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class VcsGroupsWrapper extends ActionGroup implements DumbAware {
  private static final Logger LOG = Logger.getInstance(VcsGroupsWrapper.class);

  @Override
  public void update(@NotNull AnActionEvent e) {
    DefaultActionGroup vcsGroup = mergeVcsGroups(e);
    if (vcsGroup == null) {
      e.getPresentation().setVisible(false);
    }
    else {
      e.getPresentation().copyFrom(vcsGroup.getTemplatePresentation());
      vcsGroup.update(e);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return AnAction.EMPTY_ARRAY;

    DefaultActionGroup vcsGroup = mergeVcsGroups(e);
    if (vcsGroup == null) {
      return AnAction.EMPTY_ARRAY;
    }
    else {
      return vcsGroup.getChildren(e);
    }
  }

  private static @NotNull Set<String> collectVcses(@NotNull Project project, @NotNull DataContext dataContext) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);

    return VcsContextUtil.selectedFilesIterable(dataContext)
      .filterMap(file -> vcsManager.getVcsFor(VcsUtil.resolveSymlinkIfNeeded(project, file)))
      .map(AbstractVcs::getName)
      .unique()
      .take(vcsManager.getAllActiveVcss().length) // stop processing files if all available vcses are already affected
      .toSet();
  }

  private static @Nullable DefaultActionGroup mergeVcsGroups(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return null;

    Set<String> currentVcses = collectVcses(project, e.getDataContext());
    if (currentVcses.isEmpty()) return null;

    List<StandardVcsGroup> groups = new ArrayList<>();

    DefaultActionGroup vcsGroup = (DefaultActionGroup)ActionManager.getInstance().getAction("VcsGroup");
    for (AnAction child : vcsGroup.getChildren(e)) {
      StandardVcsGroup standardGroup = ObjectUtils.tryCast(child, StandardVcsGroup.class);
      if (standardGroup == null) {
        LOG.error(MessageFormat.format("Any version control group should extend {0}. Violated by {1}, {2}.", // NON-NLS
                                       StandardVcsGroup.class,
                                       ActionManager.getInstance().getId(child), child.getClass()));
        continue;
      }

      String vcsName = standardGroup.getVcsName(project);
      if (currentVcses.contains(vcsName)) {
        groups.add(standardGroup);
      }
    }

    if (groups.isEmpty()) return null;
    if (groups.size() == 1) return ContainerUtil.getOnlyItem(groups);

    DefaultActionGroup result = DefaultActionGroup.createPopupGroup(VcsBundle.messagePointer("group.name.version.control"));
    result.addAll(groups);
    return result;
  }
}
