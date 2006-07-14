package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildModel;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.impl.MetaTarget;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.StringSetSpinAllocator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AntBuildGroup extends ActionGroup {

  public void update(AnActionEvent event) {
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(project != null);
    presentation.setVisible(project != null);
  }

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return AnAction.EMPTY_ARRAY;
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) return AnAction.EMPTY_ARRAY;

    final List<AnAction> children = new ArrayList<AnAction>();
    final AntConfiguration antConfiguration = AntConfiguration.getInstance(project);
    for (AntBuildFile buildFile : antConfiguration.getBuildFiles()) {
      final String name = buildFile.getPresentableName();
      DefaultActionGroup subgroup = new DefaultActionGroup();
      subgroup.getTemplatePresentation().setText(name, false);
      subgroup.setPopup(true);
      fillGroup(buildFile, subgroup, antConfiguration);
      if (subgroup.getChildrenCount() > 0) {
        children.add(subgroup);
      }
    }

    return children.toArray(new AnAction[children.size()]);
  }

  private static void fillGroup(final AntBuildFile buildFile, final DefaultActionGroup group, final AntConfiguration antConfiguration) {
    AntBuildModel model = buildFile.getModel();
    if (model.getDefaultTargetName() != null) {
      DefaultActionGroup subgroup = new DefaultActionGroup();
      subgroup.add(getOrCreateAction(buildFile, TargetAction.DEFAULT_TARGET_NAME, new String[]{TargetAction.DEFAULT_TARGET_NAME}, null,
                                     model.getDefaultTargetActionId()));
      group.add(subgroup);
    }

    final Set<String> addedTargetNames = StringSetSpinAllocator.alloc();
    try {
      addGroupOfTargets(buildFile, model.getFilteredTargets(), addedTargetNames, group);
      addGroupOfTargets(buildFile, antConfiguration.getMetaTargets(buildFile), addedTargetNames, group);
    }
    finally {
      StringSetSpinAllocator.dispose(addedTargetNames);
    }
  }

  private static void addGroupOfTargets(final AntBuildFile buildFile,
                                        final AntBuildTarget[] targets,
                                        final Set<String> addedTargetNames,
                                        final DefaultActionGroup group) {
    final DefaultActionGroup subgroup = new DefaultActionGroup();
    for (final AntBuildTarget target : targets) {
      final String displayName = target.getName();
      if (addedTargetNames.contains(displayName)) {
        continue;
      }
      addedTargetNames.add(displayName);
      final String[] targetsToRun = (target instanceof MetaTarget) ? ((MetaTarget)target).getTargetNames() : new String[]{displayName};
      subgroup.add(getOrCreateAction(buildFile, displayName, targetsToRun, target.getNotEmptyDescription(), target.getActionId()));
    }
    if (subgroup.getChildrenCount() > 0) {
      group.add(subgroup);
    }
  }

  private static AnAction getOrCreateAction(AntBuildFile buildFile,
                                            String displayName,
                                            String[] targets,
                                            String targetDescription,
                                            String actionId) {
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
