/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.impl.SingletonRefGroup;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class BranchPopupBuilder {
  @NotNull protected final VcsLogDataPack myDataPack;
  @Nullable private final Collection<VirtualFile> myVisibleRoots;
  @Nullable private final List<List<String>> myRecentItems;

  protected BranchPopupBuilder(@NotNull VcsLogDataPack dataPack,
                               @Nullable Collection<VirtualFile> visibleRoots,
                               @Nullable List<List<String>> recentItems) {
    myDataPack = dataPack;
    myVisibleRoots = visibleRoots;
    myRecentItems = recentItems;
  }

  @NotNull
  protected abstract AnAction createAction(@NotNull String name, @NotNull Collection<VcsRef> refs);

  protected void createRecentAction(@NotNull DefaultActionGroup actionGroup, @NotNull List<String> recentItem) {
    assert myRecentItems == null;
  }

  protected void createFavoritesAction(@NotNull DefaultActionGroup actionGroup, @NotNull List<String> favorites) {
  }

  @NotNull
  protected AnAction createCollapsedAction(@NotNull String actionName, @NotNull Collection<VcsRef> refs) {
    return createAction(actionName, refs);
  }

  public ActionGroup build() {
    return createActions(prepareGroups(myDataPack, myVisibleRoots, myRecentItems));
  }

  private static Groups prepareGroups(@NotNull VcsLogDataPack dataPack,
                                      @Nullable Collection<VirtualFile> visibleRoots,
                                      @Nullable List<List<String>> recentItems) {
    Groups filteredGroups = new Groups();
    Collection<VcsRef> allRefs = dataPack.getRefs().getBranches();
    for (Map.Entry<VirtualFile, Set<VcsRef>> entry : VcsLogUtil.groupRefsByRoot(allRefs).entrySet()) {
      VirtualFile root = entry.getKey();
      if (visibleRoots != null && !visibleRoots.contains(root)) continue;
      List<RefGroup> refGroups = dataPack.getLogProviders().get(root).getReferenceManager().groupForBranchFilter(entry.getValue());

      putActionsForReferences(dataPack, refGroups, filteredGroups);
    }

    if (recentItems != null) {
      filteredGroups.recentGroups.addAll(recentItems);
    }

    return filteredGroups;
  }

  @NotNull
  private DefaultActionGroup createActions(@NotNull Groups groups) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (Map.Entry<String, Collection<VcsRef>> entry : groups.singletonGroups.entrySet()) {
      actionGroup.add(createAction(entry.getKey(), entry.getValue()));
    }
    if (!groups.recentGroups.isEmpty()) {
      DefaultActionGroup recentGroup = new DefaultActionGroup("Recent", true);
      for (List<String> recentItem : groups.recentGroups) {
        createRecentAction(recentGroup, recentItem);
      }
      actionGroup.add(recentGroup);
    }
    if (groups.favoriteGroups.size() > 1) {
      createFavoritesAction(actionGroup, ContainerUtil.newArrayList(groups.favoriteGroups.keySet()));
    }
    for (Map.Entry<String, Collection<VcsRef>> entry : groups.favoriteGroups.entrySet()) {
      actionGroup.add(createAction(entry.getKey(), entry.getValue()));
    }
    for (Map.Entry<String, TreeMap<String, Collection<VcsRef>>> group : groups.expandedGroups.entrySet()) {
      actionGroup.addSeparator(group.getKey());
      for (Map.Entry<String, Collection<VcsRef>> entry : group.getValue().entrySet()) {
        actionGroup.add(createAction(entry.getKey(), entry.getValue()));
      }
    }
    actionGroup.addSeparator();
    for (Map.Entry<String, TreeMap<String, Collection<VcsRef>>> group : groups.collapsedGroups.entrySet()) {
      DefaultActionGroup popupGroup = new DefaultActionGroup(group.getKey(), true);
      for (Map.Entry<String, Collection<VcsRef>> entry : group.getValue().entrySet()) {
        popupGroup.add(createCollapsedAction(entry.getKey(), entry.getValue()));
      }
      actionGroup.add(popupGroup);
    }
    return actionGroup;
  }

  private static class Groups {
    private final TreeMap<String, Collection<VcsRef>> favoriteGroups = ContainerUtil.newTreeMap();
    private final TreeMap<String, Collection<VcsRef>> singletonGroups = ContainerUtil.newTreeMap();
    private final List<List<String>> recentGroups = ContainerUtil.newArrayList();
    private final TreeMap<String, TreeMap<String, Collection<VcsRef>>> expandedGroups = ContainerUtil.newTreeMap();
    private final TreeMap<String, TreeMap<String, Collection<VcsRef>>> collapsedGroups = ContainerUtil.newTreeMap();
  }

  private static void putActionsForReferences(@NotNull VcsLogDataPack pack, @NotNull List<RefGroup> references, @NotNull Groups actions) {
    for (RefGroup refGroup : references) {
      if (refGroup instanceof SingletonRefGroup) {
        VcsRef ref = ((SingletonRefGroup)refGroup).getRef();
        if (isFavorite(pack, ref)) {
          append(actions.favoriteGroups, refGroup.getName(), ref);
        }
        else {
          append(actions.singletonGroups, refGroup.getName(), ref);
        }
      }
      else {
        TreeMap<String, TreeMap<String, Collection<VcsRef>>> groups =
          refGroup.isExpanded() ? actions.expandedGroups : actions.collapsedGroups;
        TreeMap<String, Collection<VcsRef>> groupActions = groups.computeIfAbsent(refGroup.getName(), key -> new TreeMap<>());
        for (VcsRef ref : refGroup.getRefs()) {
          if (isFavorite(pack, ref)) {
            append(actions.favoriteGroups, ref.getName(), ref);
          }
          append(groupActions, ref.getName(), ref);
        }
      }
    }
  }

  public static boolean isFavorite(@NotNull VcsLogDataPack pack, @NotNull VcsRef ref) {
    return pack.getLogProviders().get(ref.getRoot()).getReferenceManager().isFavorite(ref);
  }

  private static <T> void append(@NotNull TreeMap<String, Collection<T>> map, @NotNull String key, @NotNull T value) {
    append(map, key, Collections.singleton(value));
  }

  private static <T> void append(@NotNull TreeMap<String, Collection<T>> map, @NotNull String key, @NotNull Collection<T> values) {
    map.computeIfAbsent(key, k -> new HashSet<>()).addAll(values);
  }
}
