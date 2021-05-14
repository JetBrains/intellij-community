// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A class represents file event supplied with possible nested file system events
 * (e.g.: changed entries in a changed jar file).
 */
@ApiStatus.Internal
public class DisclosedVfsEvent {
  private final @NotNull VFileEvent myFileEvent;

  private boolean myNestedFsEventsCalculated;
  private final @NotNull List<VFileEvent> myNestedFsFileEvents = new SmartList<>();
  private final @NotNull List<Runnable> myApplyActions = new SmartList<>();

  public DisclosedVfsEvent(@NotNull VFileEvent event) {
    myFileEvent = event;
  }

  @NotNull
  public VFileEvent getFileEvent() {
    return myFileEvent;
  }

  @NotNull
  public List<VFileEvent> getNestedFsFileEvents() {
    disclose();
    return myNestedFsFileEvents;
  }

  @NotNull
  public List<Runnable> getApplyActions() {
    disclose();
    return myApplyActions;
  }

  private void disclose() {
    if (!myNestedFsEventsCalculated) {
      if (ApplicationManager.getApplication().isWriteThread()) {
        assert !AsyncEventSupport.shouldSuppressAppliers() : "Nested file events must be processed by async file listeners!";
      }
      myNestedFsFileEvents.addAll(VfsImplUtil.getJarInvalidationEvents(myFileEvent, myApplyActions));
      myNestedFsEventsCalculated = true;
    }
  }

  @Override
  public String toString() {
    return "Disclosed event: base event = " + myFileEvent;
  }
}
