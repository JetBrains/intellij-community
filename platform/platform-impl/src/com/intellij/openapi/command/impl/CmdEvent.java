// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.Command;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
@ApiStatus.Internal
public interface CmdEvent {
  @NotNull CommandId id();
  @Nullable Project project();
  @Nullable @Command String name();
  @Nullable Object groupId();
  @NotNull UndoConfirmationPolicy confirmationPolicy();
  boolean recordOriginalDocument();
  boolean isTransparent();

  default @NotNull CmdEvent withProject(@Nullable Project project) {
    if (project == project()) {
      return this;
    }
    return create(
      id(),
      project,
      name(),
      groupId(),
      confirmationPolicy(),
      recordOriginalDocument(),
      isTransparent()
    );
  }

  default @NotNull CmdEvent withRecordOriginalDocument(boolean recordOriginalDocument) {
    if (recordOriginalDocument == recordOriginalDocument()) {
      return this;
    }
    return create(
      id(),
      project(),
      name(),
      groupId(),
      confirmationPolicy(),
      recordOriginalDocument,
      isTransparent()
    );
  }

  static @NotNull CmdEvent create(
    @NotNull CommandId id,
    @Nullable Project project,
    @Nullable @Command String name,
    @Nullable Object groupId,
    @NotNull UndoConfirmationPolicy confirmationPolicy,
    boolean recordOriginalReference,
    boolean isTransparent
  ) {
    return new CmdEventImpl(
      id,
      project,
      name,
      groupId,
      confirmationPolicy,
      recordOriginalReference,
      isTransparent
    );
  }
}
