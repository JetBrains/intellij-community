// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Project root changes.
 * <p>
 * For more fine-grained, incremental updates related specifically to workspace model changes,
 * consider using {@link com.intellij.platform.backend.workspace.WorkspaceModelChangeListener}.
 * <p>
 * Note that unlike {@link com.intellij.platform.backend.workspace.WorkspaceModelChangeListener} {@link ModuleRootListener}
 * may produce events unrelated to the workspace model.
 * For example, events may be triggered by:
 * <ul>
 *   <li>File type changes</li>
 *   <li>Manual invocations via {@link ProjectRootManagerEx#makeRootsChange(Runnable, RootsChangeRescanningInfo)}</li>
 * </ul>
 *
 * Both {@link com.intellij.platform.backend.workspace.WorkspaceModelChangeListener} and {@link ModuleRootListener} will
 * generate events when roots validity changes (e.g., when a JAR file is downloaded and the corresponding VirtualFile becomes valid)
 */
@ApiStatus.OverrideOnly
public interface ModuleRootListener extends EventListener {
  @Topic.ProjectLevel
  Topic<ModuleRootListener> TOPIC = new Topic<>(ModuleRootListener.class);

  /**
   * Called within the same write action that triggers the change, but before the change is actually applied.
   * <p>
   * @param event An approximate representation of the upcoming "roots change" event, as estimated by the IDE.
   *              In most cases, the IDE cannot provide precise information in this "before" event.
   *              <p>
   *              Note that {@link ModuleRootEvent#isCausedByWorkspaceModelChangesOnly()} always returns {@code false} in this method.
   *              This is because nested {@linkplain ModuleRootEvent} events are de-duplicated by the IDE, and at the time
   *              {@code beforeRootsChange} is called, it's not yet known whether all events (including de-duplicated ones)
   *              were caused exclusively by workspace model (WSM) changes.
   */
  @RequiresWriteLock
  default void beforeRootsChange(@NotNull ModuleRootEvent event) {
  }

  /**
   * Called within the same write action that triggers the change, after the change is actually applied.
   */
  @RequiresWriteLock
  default void rootsChanged(@NotNull ModuleRootEvent event) {
  }
}
