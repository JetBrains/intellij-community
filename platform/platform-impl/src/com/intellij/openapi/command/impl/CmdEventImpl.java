// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


record CmdEventImpl(
  @NotNull CommandId id,
  @Nullable Project project,
  @Nullable @Command String name,
  @Nullable Object groupId,
  @NotNull UndoConfirmationPolicy confirmationPolicy,
  boolean recordOriginalDocument,
  boolean isTransparent
) implements CmdEvent {
}
