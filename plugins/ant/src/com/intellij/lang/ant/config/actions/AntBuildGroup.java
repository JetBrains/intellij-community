// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.config.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.util.NlsActions.ActionDescription;
import static com.intellij.openapi.util.NlsActions.ActionText;

public final class AntBuildGroup extends ActionGroup implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(project != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return AnAction.EMPTY_ARRAY;
    Project project = e.getProject();
    if (project == null) return AnAction.EMPTY_ARRAY;

    final List<AnAction> children = new ArrayList<>();
    final AntConfigurationBase antConfiguration = AntConfigurationBase.getInstance(project);
    for (final AntBuildFile buildFile : antConfiguration.getBuildFileList()) {
      final String name = buildFile.getPresentableName();
      DefaultActionGroup subgroup = new DefaultActionGroup();
      subgroup.getTemplatePresentation().setText(name, false);
      subgroup.setPopup(true);
      fillGroup(buildFile, subgroup, antConfiguration);
      if (subgroup.getChildrenCount() > 0) {
        children.add(subgroup);
      }
    }

    return children.toArray(AnAction.EMPTY_ARRAY);
  }

  private static void fillGroup(final AntBuildFile buildFile, final DefaultActionGroup group, final AntConfiguration antConfiguration) {
    final AntBuildModelBase model = (AntBuildModelBase)buildFile.getModel();
    if (model.getDefaultTargetName() != null) {
      DefaultActionGroup subgroup = new DefaultActionGroup();
      subgroup.add(getOrCreateAction(
        buildFile, TargetAction.getDefaultTargetName(), Collections.singletonList(TargetAction.getDefaultTargetName()), null, model.getDefaultTargetActionId())
      );
      group.add(subgroup);
    }

    final Set<String> addedTargetNames = new HashSet<>();
    addGroupOfTargets(buildFile, model.getFilteredTargets(), addedTargetNames, group);
    addGroupOfTargets(buildFile, antConfiguration.getMetaTargets(buildFile), addedTargetNames, group);
  }

  private static void addGroupOfTargets(final AntBuildFile buildFile,
                                        final AntBuildTarget[] targets,
                                        final Set<? super String> addedTargetNames,
                                        final DefaultActionGroup group) {
    final DefaultActionGroup subgroup = new DefaultActionGroup();
    for (final AntBuildTarget target : targets) {
      final String displayName = target.getName();
      if (addedTargetNames.contains(displayName)) {
        continue;
      }
      addedTargetNames.add(displayName);
      subgroup.add(getOrCreateAction(
        buildFile, displayName, target.getTargetNames(), target.getNotEmptyDescription(), ((AntBuildTargetBase)target).getActionId()
      ));
    }
    if (subgroup.getChildrenCount() > 0) {
      group.add(subgroup);
    }
  }

  private static AnAction getOrCreateAction(final AntBuildFile buildFile,
                                            final @ActionText String displayName,
                                            final List<String> targets,
                                            final @ActionDescription String targetDescription,
                                            final String actionId) {
    AnAction action = null;
    if (actionId != null) {
      action = ActionManager.getInstance().getAction(actionId);
    }
    if (action == null) {
      action = new TargetAction(buildFile, displayName, targets, targetDescription);
    }
    return action;
  }
}
