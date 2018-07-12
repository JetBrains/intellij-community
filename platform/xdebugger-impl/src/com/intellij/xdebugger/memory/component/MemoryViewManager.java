// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.component;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.EventDispatcher;
import com.intellij.xdebugger.memory.event.MemoryViewManagerListener;
import org.jetbrains.annotations.NotNull;

@State(name = "MemoryViewSettings", storages = @Storage("memory.view.xml"))
public class MemoryViewManager implements PersistentStateComponent<MemoryViewManagerState> {
  public static final String MEMORY_VIEW_CONTENT = "MemoryView";

  private final EventDispatcher<MemoryViewManagerListener> myDispatcher =
    EventDispatcher.create(MemoryViewManagerListener.class);
  private MemoryViewManagerState myState = new MemoryViewManagerState();

  public static MemoryViewManager getInstance() {
    return ServiceManager.getService(MemoryViewManager.class);
  }

  @NotNull
  @Override
  public MemoryViewManagerState getState() {
    return new MemoryViewManagerState(myState);
  }

  @Override
  public void loadState(@NotNull MemoryViewManagerState state) {
    myState = state;
    fireStateChanged();
  }

  public void setShowDiffOnly(boolean value) {
    if (myState.isShowWithDiffOnly != value) {
      myState.isShowWithDiffOnly = value;
      fireStateChanged();
    }
  }

  public void setShowWithInstancesOnly(boolean value) {
    if (myState.isShowWithInstancesOnly != value) {
      myState.isShowWithInstancesOnly = value;
      fireStateChanged();
    }
  }

  public void setShowTrackedOnly(boolean value) {
    if (myState.isShowTrackedOnly != value) {
      myState.isShowTrackedOnly = value;
      fireStateChanged();
    }
  }

  public void setAutoUpdate(boolean value) {
    if (myState.isAutoUpdateModeOn != value) {
      myState.isAutoUpdateModeOn = value;
      fireStateChanged();
    }
  }

  public boolean isNeedShowDiffOnly() {
    return myState.isShowWithDiffOnly;
  }

  public boolean isNeedShowInstancesOnly() {
    return myState.isShowWithInstancesOnly;
  }

  public boolean isNeedShowTrackedOnly() {
    return myState.isShowTrackedOnly;
  }

  public boolean isAutoUpdateModeEnabled() {
    return myState.isAutoUpdateModeOn;
  }

  public void addMemoryViewManagerListener(MemoryViewManagerListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  private void fireStateChanged() {
    myDispatcher.getMulticaster().stateChanged(new MemoryViewManagerState(myState));
  }
}
