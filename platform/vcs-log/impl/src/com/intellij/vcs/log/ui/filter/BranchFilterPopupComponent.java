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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogBranchFilterImpl;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BranchFilterPopupComponent extends MultipleValueFilterPopupComponent<VcsLogBranchFilter> {
  private VcsLogClassicFilterUi.BranchFilterModel myBranchFilterModel;

  public BranchFilterPopupComponent(@NotNull VcsLogUiProperties uiProperties,
                                    @NotNull VcsLogClassicFilterUi.BranchFilterModel filterModel) {
    super("Branch", uiProperties, filterModel);
    myBranchFilterModel = filterModel;
  }

  @NotNull
  @Override
  protected String getText(@NotNull VcsLogBranchFilter filter) {
    boolean positiveMatch = !filter.getBranchNames().isEmpty();
    Collection<String> names = positiveMatch ? filter.getBranchNames() : addMinusPrefix(filter.getExcludedBranchNames());
    return displayableText(names);
  }

  @Nullable
  @Override
  protected String getToolTip(@NotNull VcsLogBranchFilter filter) {
    boolean positiveMatch = !filter.getBranchNames().isEmpty();
    Collection<String> names = positiveMatch ? filter.getBranchNames() : filter.getExcludedBranchNames();
    String tooltip = tooltip(names);
    return positiveMatch ? tooltip : "not in " + tooltip;
  }

  @NotNull
  @Override
  protected VcsLogBranchFilter createFilter(@NotNull Collection<String> values) {
    Collection<String> acceptedBranches = ContainerUtil.newArrayList();
    Collection<String> excludedBranches = ContainerUtil.newArrayList();
    for (String value : values) {
      if (value.startsWith("-")) {
        excludedBranches.add(value.substring(1));
      }
      else {
        acceptedBranches.add(value);
      }
    }
    return new VcsLogBranchFilterImpl(acceptedBranches, excludedBranches);
  }

  @Override
  @NotNull
  protected Collection<String> getTextValues(@Nullable VcsLogBranchFilter filter) {
    if (filter == null) return Collections.emptySet();
    return ContainerUtil.newArrayList(ContainerUtil.concat(filter.getBranchNames(), addMinusPrefix(filter.getExcludedBranchNames())));
  }

  @NotNull
  private static List<String> addMinusPrefix(@NotNull Collection<String> branchNames) {
    return ContainerUtil.map(branchNames, new Function<String, String>() {
      @Override
      public String fun(String branchName) {
        return "-" + branchName;
      }
    });
  }

  @Override
  protected boolean supportsNegativeValues() {
    return true;
  }

  @Override
  protected ActionGroup createActionGroup() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    actionGroup.add(createAllAction());
    actionGroup.add(createSelectMultipleValuesAction());

    actionGroup.add(constructActionGroup(myFilterModel.getDataPack(), createRecentItemsActionGroup(), new Function<String, AnAction>() {
      @Override
      public AnAction fun(String name) {
        return createPredefinedValueAction(Collections.singleton(name));
      }
    }, myBranchFilterModel.getVisibleRoots()));
    return actionGroup;
  }

  public static ActionGroup constructActionGroup(@NotNull VcsLogDataPack dataPack, @Nullable ActionGroup recentItemsGroup,
                                                 @NotNull Function<String, AnAction> actionGetter, @Nullable Collection<VirtualFile> visibleRoots) {
    Groups groups = prepareGroups(dataPack, visibleRoots);
    return getFilteredActionGroup(groups, recentItemsGroup, actionGetter);
  }

  private static Groups prepareGroups(@NotNull VcsLogDataPack dataPack, @Nullable Collection<VirtualFile> visibleRoots) {
    Groups filteredGroups = new Groups();
    Collection<VcsRef> allRefs = dataPack.getRefs().getBranches();
    for (Map.Entry<VirtualFile, Collection<VcsRef>> entry : VcsLogUtil.groupRefsByRoot(allRefs).entrySet()) {
      VirtualFile root = entry.getKey();
      if (visibleRoots != null && !visibleRoots.contains(root)) continue;
      Collection<VcsRef> refs = entry.getValue();
      VcsLogProvider provider = dataPack.getLogProviders().get(root);
      VcsLogRefManager refManager = provider.getReferenceManager();
      List<RefGroup> refGroups = refManager.group(refs);

      orderRefGroups(refGroups, filteredGroups);
    }
    return filteredGroups;
  }

  private static DefaultActionGroup getFilteredActionGroup(@NotNull Groups groups, @Nullable ActionGroup recentItems,
                                                           @NotNull Function<String, AnAction> actionGetter) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (String single : groups.singletonGroups) {
      actionGroup.add(actionGetter.fun(single));
    }
    if (recentItems != null) {
      actionGroup.add(recentItems);
    }
    for (Map.Entry<String, TreeSet<String>> group : groups.expandedGroups.entrySet()) {
      actionGroup.addSeparator(group.getKey());
      for (String action : group.getValue()) {
        actionGroup.add(actionGetter.fun(action));
      }
    }
    actionGroup.addSeparator();
    for (Map.Entry<String, TreeSet<String>> group : groups.collapsedGroups.entrySet()) {
      DefaultActionGroup popupGroup = new DefaultActionGroup(group.getKey(), true);
      for (String action : group.getValue()) {
        popupGroup.add(actionGetter.fun(action));
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

  @NotNull
  @Override
  protected List<List<String>> getRecentValuesFromSettings() {
    return myUiProperties.getRecentlyFilteredBranchGroups();
  }

  @Override
  protected void rememberValuesInSettings(@NotNull Collection<String> values) {
    myUiProperties.addRecentlyFilteredBranchGroup(new ArrayList<String>(values));
  }

  @NotNull
  @Override
  protected List<String> getAllValues() {
    return ContainerUtil.map(myFilterModel.getDataPack().getRefs().getBranches(), new Function<VcsRef, String>() {
      @Override
      public String fun(VcsRef ref) {
        return ref.getName();
      }
    });
  }

}
