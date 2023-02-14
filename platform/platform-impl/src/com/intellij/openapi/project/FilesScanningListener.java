// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.util.messages.Topic;

public interface FilesScanningListener {
  @Topic.ProjectLevel Topic<FilesScanningListener> TOPIC = new Topic<>(FilesScanningListener.class, Topic.BroadcastDirection.NONE);

  /** Called (on any thread) when scanning started */
  default void filesScanningStarted() { }

  /** Called (on any thread) when scanning finished */
  default void filesScanningFinished() { }
}
