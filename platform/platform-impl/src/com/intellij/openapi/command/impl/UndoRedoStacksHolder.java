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
import java.util.stream.Collectors;


final class UndoRedoStacksHolder extends UndoRedoStacksHolderBase<UndoableGroup> {
  private final SharedAdjustableUndoableActionsHolder undoableActionsHolder;
  private final UndoRedoList<UndoableGroup> globalStack;

  UndoRedoStacksHolder(@NotNull SharedAdjustableUndoableActionsHolder undoableActionsHolder, boolean isUndo) {
    super(isUndo);
    this.undoableActionsHolder = undoableActionsHolder;
    this.globalStack = new UndoRedoList<>();
  }

  @Override
  void clearDocumentReferences(@NotNull Document document) {
    super.clearDocumentReferences(document);
    DocumentReference referenceFile = DocumentReferenceManager.getInstance().create(document);
    DocumentReference referenceDoc = new DocumentReferenceByDocument(document);
    // remove UndoAction only if it doesn't contain anything but `document`, to avoid messing up with (very rare) complex undo actions containing several documents
    globalStack.removeIf(
      group -> ContainerUtil.and(
        group.getAffectedDocuments(),
        ref -> ref.equals(referenceFile) || ref.equals(referenceDoc)
      )
    );
  }

  boolean canBeUndoneOrRedone(@NotNull Collection<DocumentReference> refs) {
    if (refs.isEmpty()) {
      return !globalStack.isEmpty() && globalStack.getLast().isValid();
    }
    for (DocumentReference docRef : refs) {
      UndoRedoList<UndoableGroup> stack = getStack(docRef);
      if (!stack.isEmpty() && stack.getLast().isValid()) {
        return true;
      }
    }
    return false;
  }

  @Nullable UndoableGroup getLastAction(@NotNull Collection<DocumentReference> refs) {
    if (refs.isEmpty()) {
      return globalStack.getLast();
    }

    UndoableGroup mostRecentAction = null;
    int mostRecentDocTimestamp = 0;

    for (DocumentReference docRef : refs) {
      UndoRedoList<UndoableGroup> stack = getStack(docRef);
      // the stack for a document can be empty in case of compound editors with several documents
      if (stack.isEmpty()) {
        continue;
      }
      UndoableGroup lastAction = stack.getLast();

      int timestamp = lastAction.getCommandTimestamp();
      if (mostRecentAction == null ||
          lastAction.isTemporary() && !mostRecentAction.isTemporary() ||
          lastAction.isTemporary() == mostRecentAction.isTemporary() && (isUndo() ? timestamp > mostRecentDocTimestamp : timestamp < mostRecentDocTimestamp)) {
        mostRecentAction = lastAction;
        mostRecentDocTimestamp = timestamp;
      }
    }

    // result must not be null
    return mostRecentAction;
  }

  @NotNull Set<DocumentReference> collectClashingActions(@NotNull UndoableGroup group) {
    Set<DocumentReference> result = new HashSet<>();
    for (DocumentReference docRef : group.getAffectedDocuments()) {
      UndoableGroup last = getStack(docRef).peekLast();
      if (last != null && last != group) {
        result.addAll(last.getAffectedDocuments());
      }
    }
    if (group.isGlobal()) {
      UndoableGroup last = globalStack.peekLast();
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
    for (UndoRedoList<UndoableGroup> stack : getAffectedStacks(group)) {
      if (isUndo() && !group.isTemporary()) {
        convertTemporaryActionsToPermanent(stack);
      }
      int undoLimit = stack == globalStack ? UndoManagerImpl.getGlobalUndoLimit() : UndoManagerImpl.getDocumentUndoLimit();
      doAddToStack(stack, group, undoLimit);
    }
  }

  void removeFromStacks(@NotNull UndoableGroup group) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Removing from " + getStacksDescription() + ": " + group.dumpState());
    }
    for (UndoRedoList<UndoableGroup> stack : getAffectedStacks(group)) {
      assert stack.getLast() == group;
      stack.removeLast();
    }
  }

  boolean replaceOnStacks(@NotNull UndoableGroup group, @NotNull UndoableGroup newGroup) {
    boolean hasAffectedItems = false;
    for (UndoRedoList<UndoableGroup> stack : getAffectedStacks(group)) {
      for (var stackIterator = stack.listIterator(); stackIterator.hasNext(); ) {
        var currentGroup = stackIterator.next();
        if (currentGroup != group) {
          continue;
        }
        stackIterator.set(newGroup);
        hasAffectedItems = true;
      }
    }
    return hasAffectedItems;
  }

  void clearStacks(@NotNull Collection<DocumentReference> refs, boolean clearGlobal) {
    for (UndoRedoList<UndoableGroup> stack : getAffectedStacks(refs, clearGlobal)) {
      while(!stack.isEmpty()) {
        clearStacksFrom(stack.getLast());
      }
    }
    removeEmptyStacks();
  }

  void collectAllAffectedDocuments(@NotNull Collection<DocumentReference> result) {
    for (UndoableGroup undoableGroup : globalStack) {
      result.addAll(undoableGroup.getAffectedDocuments());
    }
    collectLocalAffectedDocuments(result);
  }

  int getLastCommandTimestamp(@NotNull DocumentReference r) {
    UndoRedoList<UndoableGroup> stack = getStack(r);
    if (stack.isEmpty()) {
      return 0;
    }
    return Math.max(stack.getFirst().getCommandTimestamp(), stack.getLast().getCommandTimestamp());
  }

  void invalidateActionsFor(@NotNull DocumentReference ref) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Invalidating actions in " + getStacksDescription() + " for " + ref);
    }
    for (UndoRedoList<UndoableGroup> stack : getAffectedStacks(Collections.singleton(ref), true)) {
      for (UndoableGroup undoableGroup : stack) {
        undoableGroup.invalidateActionsFor(ref);
      }
    }
  }

  int getStackSize(@Nullable DocumentReference docRef) {
    if (docRef == null) {
      return globalStack.size();
    }
    return getStack(docRef).size();
  }

  @NotNull Collection<DocumentReference> getAffectedDocuments(@Nullable Collection<DocumentReference> docRefs) {
    if (docRefs == null) {
      return getAffectedDocuments();
    } else {
      var result = new HashSet<>(docRefs);
      var stacks = getAffectedStacks(docRefs, true);
      for (UndoRedoList<UndoableGroup> stack : stacks) {
        for (UndoableGroup group : stack) {
          result.addAll(group.getAffectedDocuments());
        }
      }
      return result;
    }
  }

  @NotNull String dump(@Nullable DocumentReference docRef) {
    String stackName = isUndo() ? "UndoStack" : "RedoStack";
    UndoRedoList<UndoableGroup> stack = docRef == null ? globalStack : getStack(docRef);
    var affected = stack.stream()
      .flatMap(group -> group.getAffectedDocuments().stream())
      .distinct()
      .map(Objects::toString)
      .collect(Collectors.joining(", ", "[", "]"));
    return """
      %s affected %s
      %s""".formatted(stackName, affected, dumpStack(stack));
  }

  @TestOnly
  void clearAllStacksInTests() {
    clearStacks(getAffectedDocuments(), true);
    globalStack.clear();
    myDocumentStacks.clear();
    myDocumentsWithStacks.clear();
    myNonlocalVirtualFilesWithStacks.clear();
  }

  private void doAddToStack(@NotNull UndoRedoList<UndoableGroup> stack, @NotNull UndoableGroup group, int limit) {
    if (!group.isUndoable() && stack.isEmpty()) {
      return;
    }
    stack.add(group);
    while (stack.size() > limit) {
      clearStacksFrom(stack.getFirst());
    }
  }

  private void clearStacksFrom(@NotNull UndoableGroup from) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Clearing " + getStacksDescription() + " from " + from.dumpState());
    }
    for (UndoRedoList<UndoableGroup> stack : getAffectedStacks(from)) {
      int pos = stack.indexOf(from);
      if (pos == -1) {
        continue;
      }
      if (pos > 0) {
        int top = stack.size() - pos;
        clearStacksFrom(stack.get(pos - 1));
        assert stack.size() == top && stack.indexOf(from) == 0;
      }
      stack.removeFirstSlow();
    }
    from.invalidateChangeRanges(undoableActionsHolder);
  }

  private @NotNull List<UndoRedoList<UndoableGroup>> getAffectedStacks(@NotNull UndoableGroup group) {
    return getAffectedStacks(group.getAffectedDocuments(), group.isGlobal());
  }

  private @NotNull List<UndoRedoList<UndoableGroup>> getAffectedStacks(@NotNull Collection<DocumentReference> refs, boolean global) {
    List<UndoRedoList<UndoableGroup>> result = new ArrayList<>(refs.size() + 1);
    if (global) {
      result.add(globalStack);
    }
    for (DocumentReference ref : refs) {
      result.add(getStack(ref));
    }
    return result;
  }

  private void collectLocalAffectedDocuments(@NotNull Collection<DocumentReference> result) {
    result.addAll(myDocumentStacks.keySet());
    DocumentReferenceManager documentReferenceManager = DocumentReferenceManager.getInstance();

    for (Document document : myDocumentsWithStacks) {
      result.add(documentReferenceManager.create(document));
    }
    for (VirtualFile file : myNonlocalVirtualFilesWithStacks) {
      result.add(documentReferenceManager.create(file));
    }
  }

  private @NotNull Set<DocumentReference> getAffectedDocuments() {
    Set<DocumentReference> result = new HashSet<>();
    collectAllAffectedDocuments(result);
    return result;
  }

  private static void convertTemporaryActionsToPermanent(@NotNull UndoRedoList<UndoableGroup> stack) {
    for (int i = stack.size() - 1; i >= 0; i--) {
      UndoableGroup undoableGroup = stack.get(i);
      if (!undoableGroup.isTemporary()) {
        break;
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Converting to permanent: " + undoableGroup);
      }
      undoableGroup.makePermanent();
    }
  }

  private static @NotNull String dumpStack(@NotNull UndoRedoList<UndoableGroup> stack) {
    if (stack.isEmpty()) {
      return "  empty";
    }
    StringBuilder rows = new StringBuilder();
    for (int i = stack.size() - 1; i >= 0; i--) {
      String row = "  " + i + " " + stack.get(i).dumpState0();
      rows.append(row);
      if (i != 0) {
        rows.append("\n");
      }
    }
    return rows.toString();
  }
}
