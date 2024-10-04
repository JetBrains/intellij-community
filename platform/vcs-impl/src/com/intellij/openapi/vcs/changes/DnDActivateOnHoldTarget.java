// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.util.SingleAlarm;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class DnDActivateOnHoldTarget implements DnDTarget {
  private final SingleAlarm myAlarm;

  protected DnDActivateOnHoldTarget() {
    myAlarm = new SingleAlarm(() -> activateContent(), 700);
  }

  @Override
  public boolean update(DnDEvent event) {
    boolean isDropPossible = isDropPossible(event);
    event.setDropPossible(isDropPossible);
    if (isDropPossible) {
      myAlarm.request();
    }
    else {
      myAlarm.cancelAllRequests();
    }
    return !isDropPossible;
  }

  @Override
  public void cleanUpOnLeave() {
    myAlarm.cancelAllRequests();
  }

  protected abstract void activateContent();

  public abstract boolean isDropPossible(@NotNull DnDEvent event);

  @Override
  public void drop(DnDEvent event) {
    myAlarm.cancelAllRequests();
  }
}