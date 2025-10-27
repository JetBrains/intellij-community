// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public interface SeparatedCommandListener {

  @Topic.AppLevel
  Topic<SeparatedCommandListener> TOPIC = new Topic<>(
    SeparatedCommandListener.class,
    Topic.BroadcastDirection.TO_DIRECT_CHILDREN,
    true
  );

  void onCommandStarted(@NotNull CmdEvent cmdEvent);

  void onCommandFinished(@NotNull CmdEvent cmdEvent);
}
