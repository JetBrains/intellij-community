// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.SingletonRefGroup;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class BranchPopupBuilder {
  protected final @NotNull VcsLogDataPack myDataPack;
  private final @Nullable Collection<? extends VirtualFile> myVisibleRoots;
  private final @Nullable List<? extends List<String>> myRecentItems;

  protected BranchPopupBuilder(@NotNull VcsLogDataPack dataPack,
                               @Nullable Collection<? extends VirtualFile> visibleRoots,
                               @Nullable List<? extends List<String>> recentItems) {
    myDataPack = dataPack;
    myVisibleRoots = visibleRoots;
    myRecentItems = recentItems;
  }

  protected abstract @NotNull AnAction createAction(@NotNull @NlsActions.ActionText String name, @NotNull Collection<? extends VcsRef> refs);

  protected void createRecentAction(@NotNull List<AnAction> actionGroup, @NotNull List<String> recentItem) {
    assert myRecentItems == null;
  }

  protected void createFavoritesAction(@NotNull List<AnAction> actionGroup, @NotNull List<String> favorites) {
  }

  public ActionGroup build() {
    return createActions(prepareGroups(myDataPack, myVisibleRoots, myRecentItems));
  }

  private static Groups prepareGroups(@NotNull VcsLogDataPack dataPack,
                                      @Nullable Collection<? extends VirtualFile> visibleRoots,
                                      @Nullable List<? extends List<String>> recentItems) {
    Groups filteredGroups = new Groups();
    Collection<VcsRef> allRefs = VcsLogAggregatedStoredRefsKt.getBranches(dataPack.getRefs());
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

  private @NotNull DefaultActionGroup createActions(@NotNull Groups groups) {
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
    actionGroup.add(Separator.getInstance());
    for (Map.Entry<@NlsActions.ActionText String, TreeMap<@NlsActions.ActionText String, Collection<VcsRef>>> group : groups.otherGroups.entrySet()) {
      List<AnAction> otherActions = new ArrayList<>();
      for (Map.Entry<@NlsActions.ActionText String, Collection<VcsRef>> entry : group.getValue().entrySet()) {
        otherActions.add(createAction(entry.getKey(), entry.getValue()));
      }
      DefaultActionGroup popupGroup = new DefaultActionGroup(group.getKey(), otherActions);
      popupGroup.setPopup(true);
      actionGroup.add(popupGroup);
    }
    return new DefaultActionGroup(actionGroup);
  }

  private static class Groups {
    private final TreeMap<String, Collection<VcsRef>> favoriteGroups = new TreeMap<>();
    private final TreeMap<String, Collection<VcsRef>> singletonGroups = new TreeMap<>();
    private final List<List<String>> recentGroups = new ArrayList<>();
    private final TreeMap<String, TreeMap<String, Collection<VcsRef>>> otherGroups =
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
        TreeMap<String, TreeMap<String, Collection<VcsRef>>> groups = actions.otherGroups;
        TreeMap<String, Collection<VcsRef>> groupActions =
          groups.computeIfAbsent(refGroup.getName(), key -> new TreeMap<>(NaturalComparator.INSTANCE));
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
