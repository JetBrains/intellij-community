// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;


import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;


@Experimental
@Internal
public enum UndoableActionType {
  START_MARK,
  FINISH_MARK,
  MENTION_ONLY,
  EDITOR_CHANGE,
  NON_UNDOABLE,
  OTHER,
}
