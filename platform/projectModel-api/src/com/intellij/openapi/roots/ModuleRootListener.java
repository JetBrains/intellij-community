// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Project root changes.
 * <p>
 * Instead of events with {@link ModuleRootEvent#isCausedByWorkspaceModelChangesOnly()} one may use
 * {@link com.intellij.platform.backend.workspace.WorkspaceModelChangeListener} and get more fine-grained incremental events.
 * <p>
 * {@link ModuleRootEvent#isCausedByWorkspaceModelChangesOnly()} is always {@code false} in the {@linkplain #beforeRootsChange(ModuleRootEvent)}.
 * This is because {@linkplain ModuleRootListener} will de-duplicate nested {@linkplain ModuleRootEvent} events, and at the moment of the
 * {@linkplain #beforeRootsChange(ModuleRootEvent)} invocation we don't know if all the events (including de-duplicated) are from WSM or not.
 * <p>
 * {@linkplain com.intellij.platform.backend.workspace.WorkspaceModelChangeListener} is not a direct replacement for {@linkplain ModuleRootListener},
 * because {@linkplain ModuleRootListener} may generate events that are not related to the workspace model. For example, there will be an
 * event on a filetype change, roots validity change (VirtualFileUrl validity change like when a jar file is downloaded), and
 * out of thin air events caused by {@link ProjectRootManagerEx#makeRootsChange(Runnable, RootsChangeRescanningInfo)}
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
