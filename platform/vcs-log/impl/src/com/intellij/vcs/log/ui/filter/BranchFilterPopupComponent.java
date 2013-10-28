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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogBranchFilter;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    Collection<VcsRef> allRefs = myUi.getLogDataHolder().getDataPack().getRefsModel().getBranches();
    for (Map.Entry<VirtualFile, Collection<VcsRef>> entry : VcsLogUtil.groupRefsByRoot(allRefs).entrySet()) {
      VirtualFile root = entry.getKey();
      Collection<VcsRef> refs = entry.getValue();
      VcsLogProvider provider = myUi.getLogDataHolder().getLogProvider(root);
      VcsLogRefManager refManager = provider.getReferenceManager();
      List<RefGroup> groups = refManager.group(refs);

      List<AnAction> orderedGroups = orderRefGroups(groups);
      actionGroup.addAll(orderedGroups);
    }
    return actionGroup;
  }

  private List<AnAction> orderRefGroups(List<RefGroup> groups) {
    DefaultActionGroup singletonGroup = new DefaultActionGroup();
    DefaultActionGroup expandedGroup = new DefaultActionGroup();
    DefaultActionGroup collapsedGroup = new DefaultActionGroup();

    for (RefGroup group : groups) {
      if (group.getRefs().size() == 1) {
        singletonGroup.add(new SetValueAction(group.getRefs().iterator().next().getName(), this));
      }
      else if (group.isExpanded()) {
        expandedGroup.addSeparator(group.getName());
        expandedGroup.add(createActionGroup(group, false));
      }
      else {
        collapsedGroup.add(createActionGroup(group, true));
      }
    }

    return Arrays.asList(singletonGroup, expandedGroup, Separator.getInstance(), collapsedGroup);
  }

  private DefaultActionGroup createActionGroup(RefGroup group, boolean popup) {
    DefaultActionGroup innerGroup = new DefaultActionGroup(group.getName(), popup);
    for (VcsRef ref : group.getRefs()) {
      innerGroup.add(new SetValueAction(ref.getName(), this));
    }
    return innerGroup;
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
