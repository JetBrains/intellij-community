// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

@ApiStatus.NonExtendable
public abstract class ChangesViewWorkflowManager {
  @Topic.ProjectLevel
  public static final Topic<ChangesViewWorkflowListener> TOPIC =
    new Topic<>(ChangesViewWorkflowListener.class, Topic.BroadcastDirection.NONE, true);

  public static @NotNull ChangesViewWorkflowManager getInstance(@NotNull Project project) {
    return project.getService(ChangesViewWorkflowManager.class);
  }

  @ApiStatus.Internal
  protected ChangesViewWorkflowManager() { }

  public final @Nullable ChangesViewCommitWorkflowHandler getCommitWorkflowHandler() {
    return doGetCommitWorkflowHandler();
  }

  @ApiStatus.Internal
  protected abstract @Nullable ChangesViewCommitWorkflowHandler doGetCommitWorkflowHandler();

  public interface ChangesViewWorkflowListener extends EventListener {
    void commitWorkflowChanged();
  }
}
