// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;

import java.util.EventListener;

@ApiStatus.Internal
public interface ChangeListAvailabilityListener extends EventListener {
  Topic<ChangeListAvailabilityListener> TOPIC = Topic.create("VCS changelists availability changed", ChangeListAvailabilityListener.class);

  @RequiresEdt
  default void onBefore() {}

  @RequiresEdt
  default void onAfter() {}
}
