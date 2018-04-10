/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.DiffDialogHints;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ShowDiffContext {
  @NotNull private final DiffDialogHints myDialogHints;

  @Nullable private List<AnAction> myActions;
  @Nullable private Map<Key, Object> myChainContext;
  @Nullable private Map<Change, Map<Key, Object>> myRequestContext;

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
  public Map<Key, Object> getChainContext() {
    return ContainerUtil.notNullize(myChainContext);
  }

  @NotNull
  public Map<Key, Object> getChangeContext(@NotNull Change change) {
    if (myRequestContext == null) return Collections.emptyMap();
    Map<Key, Object> map = myRequestContext.get(change);
    if (map == null) return Collections.emptyMap();
    return map;
  }

  public void addActions(@NotNull List<AnAction> action) {
    if (myActions == null) myActions = ContainerUtil.newArrayList();
    myActions.addAll(action);
  }

  public void addAction(@NotNull AnAction action) {
    if (myActions == null) myActions = ContainerUtil.newArrayList();
    myActions.add(action);
  }

  public <T> void putChainContext(@NotNull Key<T> key, T value) {
    if (myChainContext == null) myChainContext = ContainerUtil.newHashMap();
    myChainContext.put(key, value);
  }

  public <T> void putChangeContext(@NotNull Change change, @NotNull Key<T> key, T value) {
    if (myRequestContext == null) myRequestContext = ContainerUtil.newHashMap();
    if (!myRequestContext.containsKey(change)) myRequestContext.put(change, ContainerUtil.newHashMap());
    myRequestContext.get(change).put(key, value);
  }
}
