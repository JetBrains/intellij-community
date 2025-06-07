// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;


import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.editor.Document;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;


@Experimental
@Internal
public enum UndoableActionType {
  START_MARK,
  FINISH_MARK,
  MENTION_ONLY,
  EDITOR_CHANGE,
  NON_UNDOABLE,
  OTHER,
  ;

  public static @Nullable UndoableAction getAction(@NotNull String actionType, @NotNull Collection<? extends Document> docs, boolean isGlobal) {
    UndoableActionType type = valueOf(actionType);
    List<DocumentReference> docRefs = ContainerUtil.map(docs, d -> DocumentReferenceManager.getInstance().create(d));
    return switch (type) {
      case START_MARK -> new StartMarkAction(docs.iterator().next(), "", isGlobal);
      case FINISH_MARK -> new FinishMarkAction(docRefs.get(0), isGlobal);
      case MENTION_ONLY -> new MentionOnlyUndoableAction(docRefs.toArray(DocumentReference.EMPTY_ARRAY));
      case EDITOR_CHANGE -> null;
      case NON_UNDOABLE -> {
        yield docRefs.isEmpty()
              ? null
              : new NonUndoableAction(docRefs.get(0), isGlobal);
      }
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
    return OTHER;
  }
}
