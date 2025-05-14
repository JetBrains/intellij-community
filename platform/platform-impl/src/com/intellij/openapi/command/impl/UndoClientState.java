// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.client.ClientAppSession;
import com.intellij.openapi.client.ClientProjectSession;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

final class UndoClientState implements Disposable {
  final ClientId myClientId;
  final UndoRedoStacksHolder myUndoStacksHolder;
  final UndoRedoStacksHolder myRedoStacksHolder;

  final CommandMerger myMerger;
  final UndoManagerImpl myManager;

  CommandMerger myCurrentMerger;
  Project myCurrentActionProject = DummyProject.getInstance();

  int myCommandTimestamp = 1;

  int myCommandLevel;

  UndoManagerImpl.OperationState myCurrentOperationState = UndoManagerImpl.OperationState.NONE;

  DocumentReference myOriginatorReference;

  @SuppressWarnings("unused")
  private UndoClientState(ClientProjectSession session) {
    myManager = getUndoManager(session.getProject());
    myClientId = session.getClientId();
    myMerger = new CommandMerger(this);
    myUndoStacksHolder = new UndoRedoStacksHolder(true, myManager.myAdjustableUndoableActionsHolder);
    myRedoStacksHolder = new UndoRedoStacksHolder(false, myManager.myAdjustableUndoableActionsHolder);
  }

  @SuppressWarnings("unused")
  private UndoClientState(ClientAppSession session) {
    myManager = getUndoManager(ApplicationManager.getApplication());
    myMerger = new CommandMerger(this);
    myClientId = session.getClientId();
    myUndoStacksHolder = new UndoRedoStacksHolder(true, myManager.myAdjustableUndoableActionsHolder);
    myRedoStacksHolder = new UndoRedoStacksHolder(false, myManager.myAdjustableUndoableActionsHolder);
  }

  @Nullable
  PerClientLocalUndoRedoSnapshot getUndoRedoSnapshotForDocument(DocumentReference reference) {
    CommandMerger currentMerger = myCurrentMerger;
    if (currentMerger != null && currentMerger.hasActions()) {
      return null;
    }

    LocalCommandMergerSnapshot mergerSnapshot = myMerger.getSnapshot(reference);
    if (mergerSnapshot == null) {
      return null;
    }

    return new PerClientLocalUndoRedoSnapshot(
      mergerSnapshot,
      myUndoStacksHolder.getStack(reference).snapshot(),
      myRedoStacksHolder.getStack(reference).snapshot(),

      myManager.myAdjustableUndoableActionsHolder.getStack(reference).snapshot()
    );
  }

  boolean resetLocalHistory(DocumentReference reference, PerClientLocalUndoRedoSnapshot snapshot) {
    CommandMerger currentMerger = myCurrentMerger;
    if (currentMerger != null && currentMerger.hasActions()) {
      return false;
    }

    if (!myMerger.resetLocalHistory(snapshot.getLocalCommandMergerSnapshot())) {
      return false;
    }
    myUndoStacksHolder.getStack(reference).resetTo(snapshot.getUndoStackSnapshot());
    myRedoStacksHolder.getStack(reference).resetTo(snapshot.getRedoStackSnapshot());

    myManager.myAdjustableUndoableActionsHolder.getStack(reference).resetTo(snapshot.getActionsHolderSnapshot());

    return true;
  }

  private static @NotNull UndoManagerImpl getUndoManager(@NotNull ComponentManager manager) {
    return (UndoManagerImpl)manager.getService(UndoManager.class);
  }

  int nextCommandTimestamp() {
    return ++myCommandTimestamp;
  }

  @Override
  public void dispose() {
    myManager.invalidate(this);
  }

  @NotNull String dump(@NotNull Collection<DocumentReference> docs) {
    StringBuilder sb = new StringBuilder();
    sb.append(myClientId);
    sb.append("\n");
    if (myCurrentMerger == null) {
      sb.append("null CurrentMerger");
      sb.append("\n");
    }
    else {
      sb.append("CurrentMerger");
      sb.append("\n  ");
      sb.append(myCurrentMerger.dumpState());
      sb.append("\n");
    }
    sb.append("Merger");
    sb.append("\n  ");
    sb.append(myMerger.dumpState());
    sb.append("\n");
    for (DocumentReference doc : docs) {
      sb.append(dumpStack(doc, true));
      sb.append("\n");
      sb.append(dumpStack(doc, false));
    }
    return sb.toString();
  }

  private @NotNull String dumpStack(@NotNull DocumentReference doc, boolean isUndo) {
    String name = isUndo ? "UndoStack" : "RedoStack";
    UndoRedoList<UndoableGroup> stack = isUndo ? myUndoStacksHolder.getStack(doc) : myRedoStacksHolder.getStack(doc);
    return name + " for " + doc.getDocument() + "\n" + dumpStack(stack);
  }

  private static @NotNull String dumpStack(@NotNull UndoRedoList<UndoableGroup> stack) {
    ArrayList<String> reversed = new ArrayList<>();
    Iterator<UndoableGroup> it = stack.descendingIterator();
    int i = 0;
    while (it.hasNext()) {
      reversed.add("  %s %s".formatted(i, it.next().dumpState0()));
      i++;
    }
    return String.join("\n", reversed);
  }
}
