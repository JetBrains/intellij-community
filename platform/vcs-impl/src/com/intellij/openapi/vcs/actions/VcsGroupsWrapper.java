/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

public class VcsGroupsWrapper extends DefaultActionGroup implements DumbAware {

  private static final Logger LOG = Logger.getInstance(VcsGroupsWrapper.class);

  @NotNull private final PresentationFactory myPresentationFactory = new PresentationFactory();
  private Collection<StandardVcsGroup> myVcsGroups;

  public void update(@NotNull AnActionEvent e) {
    if (e.getProject() == null) {
      e.getPresentation().setVisible(false);
    }
    else {
      if (myVcsGroups == null) {
        myVcsGroups = collectVcsGroups(e);
      }

      updateVcsGroups(e);
    }
  }

  private void updateVcsGroups(@NotNull AnActionEvent e) {
    Set<String> currentVcses = collectVcses(VcsContextWrapper.createInstanceOn(e));

    if (currentVcses.isEmpty()) {
      e.getPresentation().setVisible(false);
    }
    else {
      Map<String, StandardVcsGroup> vcsGroupMap =
        myVcsGroups.stream().collect(toMap(group -> group.getVcsName(e.getProject()), Function.identity()));
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
  private static Set<String> collectVcses(@NotNull VcsContext context) {
    return context.getSelectedFilesStream()
      .map(file -> ChangesUtil.getVcsForFile(file, context.getProject()))
      .filter(Objects::nonNull)
      .map(AbstractVcs::getName)
      .collect(toSet());
  }

  @NotNull
  private static Collection<StandardVcsGroup> collectVcsGroups(@NotNull AnActionEvent e) {
    DefaultActionGroup vcsGroup = (DefaultActionGroup)ActionManager.getInstance().getAction("VcsGroup");
    AnAction[] children = vcsGroup.getChildren(e);
    Collection<StandardVcsGroup> result = ContainerUtil.newArrayList();

    for (AnAction child : children) {
      if (!(child instanceof StandardVcsGroup)) {
        LOG.error(MessageFormat.format(
          "Any version control group should extends com.intellij.openapi.vcs.actions.StandardVcsGroup class. GroupId class: {0} group ID: {1}",
          child.getClass().getName(), ActionManager.getInstance().getId(child)));
      }
      else {
        result.add((StandardVcsGroup)child);
      }
    }

    return result;
  }

  @NotNull
  private static DefaultActionGroup createAllVcsesGroup(@NotNull Map<String, StandardVcsGroup> vcsGroupsMap, @NotNull Set<String> vcses) {
    DefaultActionGroup result = new DefaultActionGroup(VcsBundle.message("group.name.version.control"), true);

    vcsGroupsMap.entrySet().stream()
      .filter(e -> vcses.contains(e.getKey()))
      .map(Map.Entry::getValue)
      .forEach(result::add);

    return result;
  }
}
