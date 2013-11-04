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
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogBranchFilter;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class BranchFilterPopupComponent extends FilterPopupComponent {

  @NotNull private final VcsLogUI myUi;

  BranchFilterPopupComponent(@NotNull VcsLogClassicFilterUi filterUi, @NotNull VcsLogUI ui) {
    super(filterUi, "Branch");
    myUi = ui;
  }

  @Override
  protected ActionGroup createActionGroup() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    actionGroup.add(createAllAction());

    Groups filteredGroups = new Groups();
    Collection<VcsRef> allRefs = myUi.getLogDataHolder().getDataPack().getRefsModel().getBranches();
    for (Map.Entry<VirtualFile, Collection<VcsRef>> entry : VcsLogUtil.groupRefsByRoot(allRefs).entrySet()) {
      VirtualFile root = entry.getKey();
      Collection<VcsRef> refs = entry.getValue();
      VcsLogProvider provider = myUi.getLogDataHolder().getLogProvider(root);
      VcsLogRefManager refManager = provider.getReferenceManager();
      List<RefGroup> refGroups = refManager.group(refs);

      orderRefGroups(refGroups, filteredGroups);
    }

    actionGroup.add(getFilteredActionGroup(filteredGroups));
    return actionGroup;
  }

  private DefaultActionGroup getFilteredActionGroup(Groups groups) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (String single : groups.singletonGroups) {
      actionGroup.add(new SetValueAction(single, this));
    }
    for (Map.Entry<String, TreeSet<String>> group : groups.expandedGroups.entrySet()) {
      actionGroup.addSeparator(group.getKey());
      for (String action : group.getValue()) {
        actionGroup.add(new SetValueAction(action, this));
      }
    }
    actionGroup.addSeparator();
    for (Map.Entry<String, TreeSet<String>> group : groups.collapsedGroups.entrySet()) {
      DefaultActionGroup popupGroup = new DefaultActionGroup(group.getKey(), true);
      for (String action : group.getValue()) {
        popupGroup.add(new SetValueAction(action, this));
      }
      actionGroup.add(popupGroup);
    }
    return actionGroup;
  }

  private static class Groups {
    private final TreeSet<String> singletonGroups = ContainerUtil.newTreeSet();
    private final TreeMap<String, TreeSet<String>> expandedGroups = ContainerUtil.newTreeMap();
    private final TreeMap<String, TreeSet<String>> collapsedGroups = ContainerUtil.newTreeMap();
  }

  private static void orderRefGroups(List<RefGroup> groups, Groups filteredGroups) {
    for (final RefGroup group : groups) {
      if (group.getRefs().size() == 1) {
        String name = group.getRefs().iterator().next().getName();
        if (!filteredGroups.singletonGroups.contains(name)) {
          filteredGroups.singletonGroups.add(name);
        }
      }
      else if (group.isExpanded()) {
        addToGroup(group, filteredGroups.expandedGroups);
      }
      else {
        addToGroup(group, filteredGroups.collapsedGroups);
      }
    }
  }

  private static void addToGroup(final RefGroup group, TreeMap<String, TreeSet<String>> groupToAdd) {
    TreeSet<String> existingGroup = groupToAdd.get(group.getName());

    TreeSet<String> actions = new TreeSet<String>();
    for (VcsRef ref : group.getRefs()) {
      actions.add(ref.getName());
    }

    if (existingGroup == null) {
      groupToAdd.put(group.getName(), actions);
    }
    else {
      for (String action : actions) {
        existingGroup.add(action);
      }
    }
  }

  @Nullable
  @Override
  protected VcsLogFilter getFilter() {
    String value = getValue();
    return value == ALL
           ? null
           : new VcsLogBranchFilter(myUi.getLogDataHolder().getDataPack().getRefsModel().getBranches(), value);
  }

}
