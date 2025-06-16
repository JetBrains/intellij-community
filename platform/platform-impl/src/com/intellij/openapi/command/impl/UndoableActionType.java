// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;


import com.intellij.openapi.command.undo.*;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;


@Experimental
@Internal
public enum UndoableActionType {
  START_MARK,
  FINISH_MARK,
  MENTION_ONLY,
  EDITOR_CHANGE,
  NON_UNDOABLE,
  GLOBAL,
  OTHER,
  ;

  public static @Nullable UndoableAction getAction(@NotNull String actionType, @NotNull Collection<? extends DocumentReference> docRefs, boolean isGlobal) {
    UndoableActionType type = valueOf(actionType);
    return switch (type) {
      case START_MARK -> new StartMarkAction(docRefs.stream().iterator().next(), "", isGlobal);
      case FINISH_MARK -> new FinishMarkAction(docRefs.stream().iterator().next(), isGlobal);
      case MENTION_ONLY -> new MentionOnlyUndoableAction(docRefs.toArray(DocumentReference.EMPTY_ARRAY));
      case EDITOR_CHANGE -> null;
      case NON_UNDOABLE -> {
        yield docRefs.isEmpty()
              ? null // TODO: possibly outdated, docRefs.isEmpty() is still the case?
              : new NonUndoableAction(docRefs.stream().iterator().next(), isGlobal);
      }
      case GLOBAL -> new MockGlobalUndoableAction(docRefs);
      case OTHER -> new MockUndoableAction(docRefs, isGlobal);
    };
  }

  static @NotNull UndoableActionType forAction(@NotNull UndoableAction action) {
    if (action instanceof StartMarkAction) {
      return START_MARK;
    }
    if (action instanceof FinishMarkAction) {
      return FINISH_MARK;
    }
    if (action instanceof MentionOnlyUndoableAction) {
      return MENTION_ONLY;
    }
    if (action instanceof  EditorChangeAction) {
      return EDITOR_CHANGE;
    }
    if (action instanceof NonUndoableAction) {
      return NON_UNDOABLE;
    }
    if (action instanceof GlobalUndoableAction) {
      return GLOBAL;
    }
    return OTHER;
  }
}
