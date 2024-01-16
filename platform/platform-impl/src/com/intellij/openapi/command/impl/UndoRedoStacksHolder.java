// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

final class UndoRedoStacksHolder extends UndoRedoStacksHolderBase<UndoableGroup> {
  private final LinkedList<UndoableGroup> myGlobalStack = new LinkedList<>();

  UndoRedoStacksHolder(boolean isUndo) {
    super(isUndo);
  }

  boolean canBeUndoneOrRedone(@NotNull Collection<? extends DocumentReference> refs) {
    if (refs.isEmpty()) return !myGlobalStack.isEmpty() && myGlobalStack.getLast().isValid();
    for (DocumentReference each : refs) {
      if (!getStack(each).isEmpty() && getStack(each).getLast().isValid()) return true;
    }
    return false;
  }

  @Nullable
  UndoableGroup getLastAction(@NotNull Collection<? extends DocumentReference> refs) {
    if (refs.isEmpty()) return myGlobalStack.getLast();

    UndoableGroup mostRecentAction = null;
    int mostRecentDocTimestamp = 0;

    for (DocumentReference each : refs) {
      LinkedList<UndoableGroup> stack = getStack(each);
      // the stack for a document can be empty in case of compound editors with several documents
      if (stack.isEmpty()) continue;
      UndoableGroup lastAction = stack.getLast();

      int timestamp = lastAction.getCommandTimestamp();
      if (mostRecentAction == null || lastAction.isTemporary() && !mostRecentAction.isTemporary() ||
          lastAction.isTemporary() == mostRecentAction.isTemporary() &&
          (myUndo ? timestamp > mostRecentDocTimestamp : timestamp < mostRecentDocTimestamp)) {
        mostRecentAction = lastAction;
        mostRecentDocTimestamp = timestamp;
      }
    }

    // result must not be null
    return mostRecentAction;
  }

  @NotNull
  Set<DocumentReference> collectClashingActions(@NotNull UndoableGroup group) {
    Set<DocumentReference> result = new HashSet<>();

    for (DocumentReference each : group.getAffectedDocuments()) {
      UndoableGroup last = getStack(each).peekLast();
      if (last != null && last != group) {
        result.addAll(last.getAffectedDocuments());
      }
    }

    if (group.isGlobal()) {
      UndoableGroup last = myGlobalStack.peekLast();
      if (last != null && last != group) {
        result.addAll(last.getAffectedDocuments());
      }
    }

    return result;
  }

  void addToStacks(@NotNull UndoableGroup group) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Adding to " + getStacksDescription() + ": " + group.dumpState());
    }
    for (LinkedList<UndoableGroup> each : getAffectedStacks(group)) {
      if (myUndo && !group.isTemporary()) convertTemporaryActionsToPermanent(each);
      doAddToStack(each, group, each == myGlobalStack ? UndoManagerImpl.getGlobalUndoLimit() : UndoManagerImpl.getDocumentUndoLimit());
    }
  }

  private void doAddToStack(@NotNull LinkedList<UndoableGroup> stack, @NotNull UndoableGroup group, int limit) {
    if (!group.isUndoable() && stack.isEmpty()) return;

    stack.add(group);
    while (stack.size() > limit) {
      clearStacksFrom(stack.getFirst());
    }
  }

  void removeFromStacks(@NotNull UndoableGroup group) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Removing from " + getStacksDescription() + ": " + group.dumpState());
    }
    for (LinkedList<UndoableGroup> each : getAffectedStacks(group)) {
      assert each.getLast() == group;
      each.removeLast();
    }
  }

  public boolean replaceOnStacks(@NotNull UndoableGroup group, @NotNull UndoableGroup newGroup) {
    boolean hasAffectedItems = false;

    for (LinkedList<UndoableGroup> stack : getAffectedStacks(group)) {
      for (var stackIterator = stack.listIterator(); stackIterator.hasNext(); ) {
        var currentGroup = stackIterator.next();
        if (currentGroup != group) continue;

        stackIterator.set(newGroup);
        hasAffectedItems = true;
      }
    }

    return hasAffectedItems;
  }

  void clearStacks(boolean clearGlobal, @NotNull Set<? extends DocumentReference> refs) {
    for (LinkedList<UndoableGroup> each : getAffectedStacks(clearGlobal, refs)) {
      while(!each.isEmpty()) {
        clearStacksFrom(each.getLast());
      }
    }
    removeEmptyStacks();
  }

  @Override
  void clearDocumentReferences(@NotNull Document document) {
    super.clearDocumentReferences(document);
    DocumentReference referenceFile = DocumentReferenceManager.getInstance().create(document);
    DocumentReference referenceDoc = new DocumentReferenceByDocument(document);
    // remove UndoAction only if it doesn't contain anything but `document`, to avoid messing up with (very rare) complex undo actions containing several documents
    myGlobalStack.removeIf(group -> ContainerUtil.and(group.getAffectedDocuments(), ref->ref.equals(referenceFile) || ref.equals(referenceDoc)));
  }

  private static void convertTemporaryActionsToPermanent(LinkedList<UndoableGroup> each) {
    for (int i = each.size() - 1; i >= 0; i--) {
      UndoableGroup group1 = each.get(i);
      if (!group1.isTemporary()) break;
      if (LOG.isDebugEnabled()) {
        LOG.debug("Converting to permanent: " + group1);
      }
      group1.makePermanent();
    }
  }

  private void clearStacksFrom(@NotNull UndoableGroup from) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Clearing " + getStacksDescription() + " from " + from.dumpState());
    }
    for (LinkedList<UndoableGroup> each : getAffectedStacks(from)) {
      int pos = each.indexOf(from);
      if (pos == -1) continue;

      if (pos > 0) {
        int top = each.size() - pos;
        clearStacksFrom(each.get(pos - 1));
        assert each.size() == top && each.indexOf(from) == 0;
      }
      each.removeFirst();
    }
    from.invalidateChangeRanges();
  }

  private @NotNull List<LinkedList<UndoableGroup>> getAffectedStacks(@NotNull UndoableGroup group) {
    return getAffectedStacks(group.isGlobal(), group.getAffectedDocuments());
  }

  private @NotNull List<LinkedList<UndoableGroup>> getAffectedStacks(boolean global, @NotNull Collection<? extends DocumentReference> refs) {
    List<LinkedList<UndoableGroup>> result = new ArrayList<>(refs.size() + 1);
    if (global) result.add(myGlobalStack);
    for (DocumentReference ref : refs) {
      result.add(getStack(ref));
    }
    return result;
  }

  @TestOnly
  void clearAllStacksInTests() {
    clearStacks(true, getAffectedDocuments());
    myGlobalStack.clear();
    myDocumentStacks.clear();
    myDocumentsWithStacks.clear();
    myNonlocalVirtualFilesWithStacks.clear();
  }

  void collectAllAffectedDocuments(@NotNull Collection<? super DocumentReference> result) {
    for (UndoableGroup each : myGlobalStack) {
      result.addAll(each.getAffectedDocuments());
    }
    collectLocalAffectedDocuments(result);
  }

  private void collectLocalAffectedDocuments(@NotNull Collection<? super DocumentReference> result) {
    result.addAll(myDocumentStacks.keySet());
    DocumentReferenceManager documentReferenceManager = DocumentReferenceManager.getInstance();

    for (Document each : myDocumentsWithStacks) {
      result.add(documentReferenceManager.create(each));
    }
    for (VirtualFile each : myNonlocalVirtualFilesWithStacks) {
      result.add(documentReferenceManager.create(each));
    }
  }

  private @NotNull Set<DocumentReference> getAffectedDocuments() {
    Set<DocumentReference> result = new HashSet<>();
    collectAllAffectedDocuments(result);
    return result;
  }

  int getLastCommandTimestamp(@NotNull DocumentReference r) {
    LinkedList<UndoableGroup> stack = getStack(r);
    if (stack.isEmpty()) return 0;
    return Math.max(stack.getFirst().getCommandTimestamp(), stack.getLast().getCommandTimestamp());
  }

  void invalidateActionsFor(@NotNull DocumentReference ref) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Invalidating actions in " + getStacksDescription() + " for " + ref);
    }
    for (List<UndoableGroup> eachStack : getAffectedStacks(true, Collections.singleton(ref))) {
      for (UndoableGroup eachGroup : eachStack) {
        eachGroup.invalidateActionsFor(ref);
      }
    }
  }
}
