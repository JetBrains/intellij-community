// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.DiffDialogHints;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ShowDiffContext {
  @NotNull private final DiffDialogHints myDialogHints;

  @Nullable private List<AnAction> myActions;
  @Nullable private Map<Key<?>, Object> myChainContext;
  @Nullable private Map<Change, Map<Key<?>, Object>> myRequestContext;

  public ShowDiffContext() {
    this(DiffDialogHints.DEFAULT);
  }

  public ShowDiffContext(@NotNull DiffDialogHints dialogHints) {
    myDialogHints = dialogHints;
  }

  @NotNull
  public DiffDialogHints getDialogHints() {
    return myDialogHints;
  }

  @NotNull
  public List<AnAction> getActions() {
    if (myActions == null) return Collections.emptyList();
    return myActions;
  }

  @NotNull
  public Map<Key<?>, Object> getChainContext() {
    return ContainerUtil.notNullize(myChainContext);
  }

  @NotNull
  public Map<Key<?>, Object> getChangeContext(@NotNull Change change) {
    if (myRequestContext == null) return Collections.emptyMap();
    Map<Key<?>, Object> map = myRequestContext.get(change);
    return map == null ? Collections.emptyMap() : map;
  }

  public void addActions(@NotNull List<? extends AnAction> action) {
    if (myActions == null) myActions = new ArrayList<>();
    myActions.addAll(action);
  }

  public void addAction(@NotNull AnAction action) {
    if (myActions == null) myActions = new ArrayList<>();
    myActions.add(action);
  }

  public <T> void putChainContext(@NotNull Key<T> key, T value) {
    if (myChainContext == null) myChainContext = new HashMap<>();
    myChainContext.put(key, value);
  }

  public <T> void putChangeContext(@NotNull Change change, @NotNull Key<T> key, T value) {
    if (myRequestContext == null) myRequestContext = new HashMap<>();
    if (!myRequestContext.containsKey(change)) myRequestContext.put(change, new HashMap<>());
    myRequestContext.get(change).put(key, value);
  }
}
