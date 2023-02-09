// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.SortedSet;
import java.util.TreeSet;

@State(name = "Vcs.Log.Big.Repositories", storages = @Storage(StoragePathMacros.CACHE_FILE))
@Service(Service.Level.APP)
public final class VcsLogBigRepositoriesList implements PersistentStateComponent<VcsLogBigRepositoriesList.State> {
  private final @NotNull Object myLock = new Object();
  private final @NotNull EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
  private State myState;

  public VcsLogBigRepositoriesList() {
    synchronized (myLock) {
      myState = new State();
    }
  }

  @Override
  public @NotNull State getState() {
    synchronized (myLock) {
      return new State(myState);
    }
  }

  @Override
  public void loadState(@NotNull State state) {
    synchronized (myLock) {
      if (state.diffRenameLimitOne) {
        myState = new State(state);
      }
      else {
        myState = new State();
        myState.diffRenameLimitOne = true;
      }
    }
  }

  public void addRepository(@NotNull VirtualFile root) {
    boolean added;
    synchronized (myLock) {
      added = myState.repositories.add(root.getPath());
    }
    if (added) myDispatcher.getMulticaster().onRepositoriesListChanged();
  }

  public boolean removeRepository(@NotNull VirtualFile root) {
    boolean removed;
    synchronized (myLock) {
      removed = myState.repositories.remove(root.getPath());
    }
    if (removed) myDispatcher.getMulticaster().onRepositoriesListChanged();
    return removed;
  }

  public boolean isBig(@NotNull VirtualFile root) {
    synchronized (myLock) {
      return myState.repositories.contains(root.getPath());
    }
  }

  public int getRepositoryCount() {
    synchronized (myLock) {
      return myState.repositories.size();
    }
  }

  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
    myDispatcher.addListener(listener, disposable);
  }

  public static @NotNull VcsLogBigRepositoriesList getInstance() {
    return ApplicationManager.getApplication().getService(VcsLogBigRepositoriesList.class);
  }

  public static final class State {
    @XCollection(elementName = "repository", valueAttributeName = "path", style = XCollection.Style.v2)
    public SortedSet<String> repositories = new TreeSet<>();
    @Attribute("diff-rename-limit-one")
    public boolean diffRenameLimitOne = false;

    public State() {
    }

    public State(@NotNull State state) {
      repositories = new TreeSet<>(state.repositories);
      diffRenameLimitOne = state.diffRenameLimitOne;
    }
  }

  public interface Listener extends EventListener {
    void onRepositoriesListChanged();
  }
}
