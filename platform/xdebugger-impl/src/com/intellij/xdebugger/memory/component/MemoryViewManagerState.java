// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.component;

public final class MemoryViewManagerState {
  public boolean isShowWithInstancesOnly = true;
  public boolean isShowWithDiffOnly = false;
  public boolean isShowTrackedOnly = false;
  public boolean isAutoUpdateModeOn = false;

  MemoryViewManagerState() {
  }

  MemoryViewManagerState(MemoryViewManagerState other) {
    isShowWithInstancesOnly = other.isShowWithInstancesOnly;
    isShowWithDiffOnly = other.isShowWithDiffOnly;
    isShowTrackedOnly = other.isShowTrackedOnly;
    isAutoUpdateModeOn = other.isAutoUpdateModeOn;
  }
}
