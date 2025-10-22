// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.Command;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Internal
public interface SeparatedCommandListener {

  @Topic.AppLevel
  Topic<SeparatedCommandListener> TOPIC = new Topic<>(
    SeparatedCommandListener.class,
    Topic.BroadcastDirection.TO_DIRECT_CHILDREN,
    true
  );

  void onCommandStarted(
    @Nullable CommandId commandId,
    @Nullable Project commandProject,
    @Nullable @Command String commandName,
    @Nullable Object commandGroupId,
    @NotNull UndoConfirmationPolicy confirmationPolicy,
    boolean recordOriginalReference,
    boolean isTransparent
  );

  void onCommandFinished(
    @Nullable Project commandProject,
    @Nullable @Command String commandName,
    @Nullable Object commandGroupId,
    boolean isTransparent
  );
}
