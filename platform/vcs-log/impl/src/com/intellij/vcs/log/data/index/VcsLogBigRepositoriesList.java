// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;
import java.util.SortedSet;
import java.util.TreeSet;

@State(name = "Vcs.Log.Big.Repositories", storages = {@Storage(value = "vcs.log.big.repos.xml", roamingType = RoamingType.DISABLED)},
  reportStatistic = false)
public class VcsLogBigRepositoriesList implements PersistentStateComponent<VcsLogBigRepositoriesList.State> {
  @NotNull private final Object myLock = new Object();
  @NotNull private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
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
  public void loadState(@NotNull State state) {
    synchronized (myLock) {
      if (state.DIFF_RENAME_LIMIT_ONE) {
        myState = new State(state);
      }
      else {
        myState = new State();
        myState.DIFF_RENAME_LIMIT_ONE = true;
      }
    }
  }

  public void addRepository(@NotNull VirtualFile root) {
    boolean added;
    synchronized (myLock) {
      added = myState.REPOSITORIES.add(root.getPath());
    }
    if (added) myDispatcher.getMulticaster().onRepositoriesListChanged();
  }

  public boolean removeRepository(@NotNull VirtualFile root) {
    boolean removed;
    synchronized (myLock) {
      removed = myState.REPOSITORIES.remove(root.getPath());
    }
    if (removed) myDispatcher.getMulticaster().onRepositoriesListChanged();
    return removed;
  }

  public boolean isBig(@NotNull VirtualFile root) {
    synchronized (myLock) {
      return myState.REPOSITORIES.contains(root.getPath());
    }
  }

  public int getRepositoriesCount() {
    synchronized (myLock) {
      return myState.REPOSITORIES.size();
    }
  }

  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
    myDispatcher.addListener(listener, disposable);
  }

  @NotNull
  public static VcsLogBigRepositoriesList getInstance() {
    return ServiceManager.getService(VcsLogBigRepositoriesList.class);
  }

  public static class State {
    @XCollection(elementName = "repository", valueAttributeName = "path")
    public SortedSet<String> REPOSITORIES = new TreeSet<>();
    @Attribute("diff-rename-limit-one")
    public boolean DIFF_RENAME_LIMIT_ONE = false;

    public State() {
    }

    public State(@NotNull State state) {
      REPOSITORIES = new TreeSet<>(state.REPOSITORIES);
      DIFF_RENAME_LIMIT_ONE = state.DIFF_RENAME_LIMIT_ONE;
    }
  }

  public interface Listener extends EventListener {
    void onRepositoriesListChanged();
  }
}
