// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl.stores;

import com.intellij.util.messages.Topic;

/**
 * Only for project- and module- level components.
 */
public interface BatchUpdateListener {
  @Topic.ProjectLevel
  Topic<BatchUpdateListener> TOPIC = new Topic<>(BatchUpdateListener.class, Topic.BroadcastDirection.NONE, true);

  default void onBatchUpdateStarted() {
  }

  default void onBatchUpdateFinished() {
  }
}
