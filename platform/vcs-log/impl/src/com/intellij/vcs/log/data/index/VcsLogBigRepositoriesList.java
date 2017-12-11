/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.SortedSet;

@State(name = "Vcs.Log.Big.Repositories", storages = {@Storage("vcs.log.big.repos.xml")})
public class VcsLogBigRepositoriesList implements PersistentStateComponent<VcsLogBigRepositoriesList.State> {
  @NotNull private final Object myLock = new Object();
  private State myState;

  public VcsLogBigRepositoriesList() {
    synchronized (myLock) {
      myState = new State();
    }
  }

  @Nullable
  @Override
  public State getState() {
    synchronized (myLock) {
      return new State(myState);
    }
  }

  @Override
  public void loadState(State state) {
    synchronized (myLock) {
      myState = new State(state);
    }
  }

  public void addRepository(@NotNull VirtualFile root) {
    synchronized (myLock) {
      myState.REPOSITORIES.add(root.getPath());
    }
  }

  public void removeRepository(@NotNull VirtualFile root) {
    synchronized (myLock) {
      myState.REPOSITORIES.remove(root.getPath());
    }
  }

  public boolean isBig(@NotNull VirtualFile root) {
    synchronized (myLock) {
      return myState.REPOSITORIES.contains(root.getPath());
    }
  }

  @NotNull
  public static VcsLogBigRepositoriesList getInstance() {
    return ServiceManager.getService(VcsLogBigRepositoriesList.class);
  }

  public static class State {
    @XCollection(elementName = "repository", valueAttributeName = "path")
    public SortedSet<String> REPOSITORIES = ContainerUtil.newTreeSet();

    public State() {
    }

    public State(@NotNull State state) {
      REPOSITORIES = ContainerUtil.newTreeSet(state.REPOSITORIES);
    }
  }
}
