package com.intellij.openapi.command.impl;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface UndoCapabilities {
  boolean isTransparentSupported();

  boolean isConfirmationSupported();

  boolean isCompactSupported();

  boolean isGlobalSplitSupported();

  boolean isPerClientSupported();

  boolean isCommandRestartSupported();

  boolean isEditorStateRestoreSupported();
}
