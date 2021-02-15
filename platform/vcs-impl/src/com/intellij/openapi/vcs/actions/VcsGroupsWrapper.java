// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class VcsGroupsWrapper extends DefaultActionGroup implements DumbAware {

  private static final Logger LOG = Logger.getInstance(VcsGroupsWrapper.class);

  @NotNull private final PresentationFactory myPresentationFactory = new PresentationFactory();

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setVisible(false);
      return;
    }

    Set<String> currentVcses = collectVcses(project, e.getDataContext());

    if (currentVcses.isEmpty()) {
      e.getPresentation().setVisible(false);
    }
    else {
      Map<String, StandardVcsGroup> vcsGroupMap = collectVcsGroups(e);
      StandardVcsGroup firstVcsGroup = vcsGroupMap.get(getFirstItem(currentVcses));
      DefaultActionGroup allVcsesGroup =
        currentVcses.size() == 1 && firstVcsGroup != null ? firstVcsGroup : createAllVcsesGroup(vcsGroupMap, currentVcses);

      copyPresentation(allVcsesGroup, e.getPresentation());
      removeAll();
      addAll(allVcsesGroup);
    }
  }

  private void copyPresentation(@NotNull AnAction sourceAction, @NotNull Presentation target) {
    Presentation source = myPresentationFactory.getPresentation(sourceAction);

    target.setDescription(source.getDescription());
    target.restoreTextWithMnemonic(source);
    target.setVisible(source.isVisible());
    target.setEnabled(source.isEnabled());
  }

  @NotNull
  private static Set<String> collectVcses(@NotNull Project project, @NotNull DataContext dataContext) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);

    return VcsContextUtil.selectedFilesIterable(dataContext)
      .filterMap(vcsManager::getVcsFor)
      .map(AbstractVcs::getName)
      .unique()
      .take(vcsManager.getAllActiveVcss().length) // stop processing files if all available vcses are already affected
      .toSet();
  }

  @NotNull
  private static Map<String, StandardVcsGroup> collectVcsGroups(@NotNull AnActionEvent e) {
    Map<String, StandardVcsGroup> result = new HashMap<>();
    DefaultActionGroup vcsGroup = (DefaultActionGroup)ActionManager.getInstance().getAction("VcsGroup");

    for (AnAction child : vcsGroup.getChildren(e)) {
      if (!(child instanceof StandardVcsGroup)) {
        LOG.error(MessageFormat.format("Any version control group should extend {0}. Violated by {1}, {2}.", // NON-NLS
                                       StandardVcsGroup.class,
                                       ActionManager.getInstance().getId(child), child.getClass()));
      }
      else {
        StandardVcsGroup group = (StandardVcsGroup)child;
        result.put(group.getVcsName(e.getProject()), group);
      }
    }

    return result;
  }

  @NotNull
  private static DefaultActionGroup createAllVcsesGroup(@NotNull Map<String, StandardVcsGroup> vcsGroupsMap, @NotNull Set<String> vcses) {
    DefaultActionGroup result = DefaultActionGroup.createPopupGroup(VcsBundle.messagePointer("group.name.version.control"));

    vcsGroupsMap.entrySet().stream()
      .filter(e -> vcses.contains(e.getKey()))
      .map(Map.Entry::getValue)
      .forEach(result::add);

    return result;
  }
}
