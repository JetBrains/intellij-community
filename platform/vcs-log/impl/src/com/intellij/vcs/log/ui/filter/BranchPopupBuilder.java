// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.impl.SingletonRefGroup;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class BranchPopupBuilder {
  @NotNull protected final VcsLogDataPack myDataPack;
  @Nullable private final Collection<? extends VirtualFile> myVisibleRoots;
  @Nullable private final List<? extends List<String>> myRecentItems;

  protected BranchPopupBuilder(@NotNull VcsLogDataPack dataPack,
                               @Nullable Collection<? extends VirtualFile> visibleRoots,
                               @Nullable List<? extends List<String>> recentItems) {
    myDataPack = dataPack;
    myVisibleRoots = visibleRoots;
    myRecentItems = recentItems;
  }

  @NotNull
  protected abstract AnAction createAction(@NotNull @NlsActions.ActionText String name, @NotNull Collection<? extends VcsRef> refs);

  protected void createRecentAction(@NotNull List<AnAction> actionGroup, @NotNull List<String> recentItem) {
    assert myRecentItems == null;
  }

  protected void createFavoritesAction(@NotNull List<AnAction> actionGroup, @NotNull List<String> favorites) {
  }

  @NotNull
  protected AnAction createCollapsedAction(@NotNull @NlsActions.ActionText String actionName, @NotNull Collection<? extends VcsRef> refs) {
    return createAction(actionName, refs);
  }

  public ActionGroup build() {
    return createActions(prepareGroups(myDataPack, myVisibleRoots, myRecentItems));
  }

  private static Groups prepareGroups(@NotNull VcsLogDataPack dataPack,
                                      @Nullable Collection<? extends VirtualFile> visibleRoots,
                                      @Nullable List<? extends List<String>> recentItems) {
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
    List<AnAction> actionGroup = new ArrayList<>();
    for (Map.Entry<@NlsActions.ActionText String, Collection<VcsRef>> entry : groups.singletonGroups.entrySet()) {
      actionGroup.add(createAction(entry.getKey(), entry.getValue()));
    }
    if (!groups.recentGroups.isEmpty()) {
      List<AnAction> recents = new ArrayList<>();
      for (List<String> recentItem : groups.recentGroups) {
        createRecentAction(recents, recentItem);
      }
      DefaultActionGroup recentGroup = new DefaultActionGroup(VcsLogBundle.message("vcs.log.filter.recent"), recents);
      recentGroup.setPopup(true);
      actionGroup.add(recentGroup);
    }
    if (groups.favoriteGroups.size() > 1) {
      createFavoritesAction(actionGroup, new ArrayList<>(ContainerUtil.map2LinkedSet(ContainerUtil.flatten(groups.favoriteGroups.values()),
                                                                                     ref -> ref.getName())));
    }
    for (Map.Entry<@NlsActions.ActionText String, Collection<VcsRef>> entry : groups.favoriteGroups.entrySet()) {
      actionGroup.add(createAction(entry.getKey(), entry.getValue()));
    }
    for (Map.Entry<@NlsContexts.Separator String, TreeMap<@NlsActions.ActionText String, Collection<VcsRef>>> group : groups.expandedGroups.entrySet()) {
      actionGroup.add(Separator.create(group.getKey()));
      for (Map.Entry<@NlsActions.ActionText String, Collection<VcsRef>> entry : group.getValue().entrySet()) {
        actionGroup.add(createAction(entry.getKey(), entry.getValue()));
      }
    }
    actionGroup.add(Separator.getInstance());
    for (Map.Entry<@NlsActions.ActionText String, TreeMap<@NlsActions.ActionText String, Collection<VcsRef>>> group : groups.collapsedGroups.entrySet()) {
      List<AnAction> collapsed = new ArrayList<>();
      for (Map.Entry<@NlsActions.ActionText String, Collection<VcsRef>> entry : group.getValue().entrySet()) {
        collapsed.add(createCollapsedAction(entry.getKey(), entry.getValue()));
      }
      DefaultActionGroup popupGroup = new DefaultActionGroup(group.getKey(), collapsed);
      popupGroup.setPopup(true);
      actionGroup.add(popupGroup);
    }
    return new DefaultActionGroup(actionGroup);
  }

  private static class Groups {
    private final TreeMap<String, Collection<VcsRef>> favoriteGroups = new TreeMap<>();
    private final TreeMap<String, Collection<VcsRef>> singletonGroups = new TreeMap<>();
    private final List<List<String>> recentGroups = new ArrayList<>();
    private final TreeMap<String, TreeMap<String, Collection<VcsRef>>> expandedGroups =
      new TreeMap<>();
    private final TreeMap<String, TreeMap<String, Collection<VcsRef>>> collapsedGroups =
      new TreeMap<>();
  }

  private static void putActionsForReferences(@NotNull VcsLogDataPack pack,
                                              @NotNull List<? extends RefGroup> references,
                                              @NotNull Groups actions) {
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

  private static <T> void append(@NotNull TreeMap<String, Collection<T>> map,
                                 @NotNull String key,
                                 @NotNull Collection<? extends T> values) {
    map.computeIfAbsent(key, k -> new HashSet<>()).addAll(values);
  }
}
