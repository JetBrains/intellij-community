// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A class represents VFileEvent supplied with
 * possible additional induced file system events (like changed entries in a changed jar file) and
 * corresponding additional actions (like cleaning jar entries cache after change).
 */
@ApiStatus.Internal
public class CompoundVFileEvent {
  private final @NotNull VFileEvent myFileEvent;
  private boolean myInducedEventsCalculated;
  private final @NotNull List<VFileEvent> myInducedEvents = new SmartList<>();
  private final @NotNull List<Runnable> myApplyActions = new SmartList<>();

  public CompoundVFileEvent(@NotNull VFileEvent event) {
    myFileEvent = event;
  }

  @NotNull
  public VFileEvent getFileEvent() {
    return myFileEvent;
  }

  public boolean areInducedEventsCalculated() {
    return myInducedEventsCalculated;
  }

  @NotNull
  public List<VFileEvent> getInducedEvents() {
    calculateAdditionalEvents();
    return myInducedEvents;
  }

  @NotNull
  public List<Runnable> getApplyActions() {
    calculateAdditionalEvents();
    return myApplyActions;
  }

  private void calculateAdditionalEvents() {
    if (!myInducedEventsCalculated) {
      myInducedEvents.addAll(VfsImplUtil.getJarInvalidationEvents(myFileEvent, myApplyActions));
      myInducedEventsCalculated = true;
    }
  }

  @Override
  public String toString() {
    return "Compound " + myFileEvent + "; induced: "+(myInducedEventsCalculated ? myInducedEvents : " (not yet calculated)");
  }
}
