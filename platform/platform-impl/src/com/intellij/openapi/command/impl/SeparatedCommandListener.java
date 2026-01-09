// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.impl.cmd.CmdEvent;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


/**
 * Notifies about commands and transparent actions in a separated manner,
 * as opposed to {@link com.intellij.openapi.command.CommandListener} allowing overlapping of them.
 * <p>
 * See {@link CommandSeparator}
 */
@ApiStatus.Internal
public interface SeparatedCommandListener {

  @Topic.AppLevel
  Topic<SeparatedCommandListener> TOPIC = new Topic<>(
    SeparatedCommandListener.class,
    Topic.BroadcastDirection.TO_DIRECT_CHILDREN,
    true
  );

  void onCommandStarted(@NotNull CmdEvent cmdStartEvent);

  void onCommandFinished(@NotNull CmdEvent cmdFinishEvent);

  void onCommandFakeFinished(@NotNull CmdEvent cmdFakeFinishEvent);
}
