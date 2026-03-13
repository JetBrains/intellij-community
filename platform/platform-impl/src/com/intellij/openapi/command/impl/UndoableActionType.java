// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UndoableAction;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;


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

  public static @NotNull UndoableAction getAction(
    @NotNull UndoableActionType actionType,
    @Nullable Collection<DocumentReference> docRefs,
    boolean isGlobal
  ) {
    if (docRefs == null) {
      return new MockUndoableAction(docRefs, isGlobal);
    }
    return switch (actionType) {
      case START_MARK -> new StartMarkAction(first(docRefs), "", isGlobal);
      case FINISH_MARK -> new FinishMarkAction(first(docRefs), isGlobal);
      case MENTION_ONLY -> new MentionOnlyUndoableAction(docRefs.toArray(DocumentReference.EMPTY_ARRAY));
      case EDITOR_CHANGE -> new MockEditorChangeAction(first(docRefs));
      case NON_UNDOABLE -> new NonUndoableAction(first(docRefs), isGlobal);
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

  private static <T> @NotNull T first(@NotNull Collection<T> collection) {
    return collection.iterator().next();
  }
}
