// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final @NotNull DiffDialogHints myDialogHints;

  private @Nullable List<AnAction> myActions;
  private @Nullable Map<Key<?>, Object> myChainContext;
  private @Nullable Map<Change, Map<Key<?>, Object>> myRequestContext;

  public ShowDiffContext() {
    this(DiffDialogHints.DEFAULT);
  }

  public ShowDiffContext(@NotNull DiffDialogHints dialogHints) {
    myDialogHints = dialogHints;
  }

  public @NotNull DiffDialogHints getDialogHints() {
    return myDialogHints;
  }

  public @NotNull List<AnAction> getActions() {
    if (myActions == null) return Collections.emptyList();
    return myActions;
  }

  public @NotNull Map<Key<?>, Object> getChainContext() {
    return ContainerUtil.notNullize(myChainContext);
  }

  public @NotNull Map<Key<?>, Object> getChangeContext(@NotNull Change change) {
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
