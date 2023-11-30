// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Project root changes.
 * <p>
 * Instead of events with {{@link ModuleRootEvent#isCausedByWorkspaceModelChangesOnly()}} one may use
 * {@link com.intellij.platform.backend.workspace.WorkspaceModelChangeListener} and get  more fine-grained incremental events.
 */
@ApiStatus.OverrideOnly
public interface ModuleRootListener extends EventListener {
  @Topic.ProjectLevel
  Topic<ModuleRootListener> TOPIC = new Topic<>(ModuleRootListener.class);

  default void beforeRootsChange(@NotNull ModuleRootEvent event) {
  }

  default void rootsChanged(@NotNull ModuleRootEvent event) {
  }
}
