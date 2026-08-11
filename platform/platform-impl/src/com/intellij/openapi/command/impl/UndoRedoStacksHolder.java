// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * There are four physical stack locations:
 * 1. globalStack stores project-wide groups.
 * 2. documentStacks strongly keeps local-file stacks by DocumentReference, so undo can survive local file removal.
 * 3. documentsWithStacks stores file-less Document stacks in Document user data to avoid retaining every created document.
 * 4. nonlocalVirtualFilesWithStacks stores non-local VirtualFile stacks in VirtualFile user data for the same weak-tracking reason.
 */
final class UndoRedoStacksHolder {
  private static final Logger LOG = Logger.getInstance(UndoRedoStacksHolder.class);

  private final Map<DocumentReference, UndoRedoList<UndoableGroup>> documentStacks;
  private final UserDataBackedStacks<Document> documentWithoutFileStacks;
  private final UserDataBackedStacks<VirtualFile> nonlocalVirtualFileStacks;
  private final UndoRedoList<UndoableGroup> globalStack;
  private final boolean isUndo;

  UndoRedoStacksHolder(boolean isUndo) {
    this.documentStacks = CollectionFactory.createSmallMemoryFootprintMap();
    this.documentWithoutFileStacks = new UserDataBackedStacks<>();
    this.nonlocalVirtualFileStacks = new UserDataBackedStacks<>();
    this.globalStack = new UndoRedoList<>();
    this.isUndo = isUndo;
  }

  @NotNull
  UndoRedoList<UndoableGroup> getStack(@NotNull DocumentReference ref) {
    VirtualFile file = ref.getFile();
    if (file != null) {
      if (file.isInLocalFileSystem()) {
        // strongly reference local files for which we can undo file removal
        return documentStacks.computeIfAbsent(ref, _ -> new UndoRedoList<>());
      }
      return nonlocalVirtualFileStacks.computeIfAbsentWeaklyTrackedStack(file);
    }
    // If document is not associated with file, we have to store its stack in document
    // itself to avoid memory leaks caused by holding stacks of all documents, ever created, here.
    // And to know, what documents do exist now, we have to maintain weak reference list of them.
    Document document = Objects.requireNonNull(ref.getDocument(), "DocumentReference must contain file or document " + ref);
    return documentWithoutFileStacks.computeIfAbsentWeaklyTrackedStack(document);
  }

  // remove all references to document to avoid memory leaks
  void clearDocumentReferences(@NotNull Document document) {
    // DocumentReference created from file is not equal to ref created from document from that file,
    // so have to check for leaking both
    DocumentReference referenceFile = DocumentReferenceManager.getInstance().create(document);
    DocumentReference referenceDoc = new DocumentReferenceByDocument(document);

    documentWithoutFileStacks.removeHolder(document);
    documentStacks.remove(referenceFile);
    documentStacks.remove(referenceDoc);

    // remove UndoAction only if it doesn't contain anything but `document`,
    // to avoid messing up with (very rare) complex undo actions containing several documents
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
          lastAction.isTemporary() == mostRecentAction.isTemporary() && (isUndo ? timestamp > mostRecentDocTimestamp : timestamp < mostRecentDocTimestamp)) {
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
      if (isUndo && !group.isTemporary()) {
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
    result.addAll(documentStacks.keySet());
    DocumentReferenceManager documentReferenceManager = DocumentReferenceManager.getInstance();
    for (Document document : documentWithoutFileStacks.getHolders()) {
      result.add(documentReferenceManager.create(document));
    }
    for (VirtualFile file : nonlocalVirtualFileStacks.getHolders()) {
      result.add(documentReferenceManager.create(file));
    }
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
    UndoRedoList<UndoableGroup> stack = docRef == null ? globalStack : getStack(docRef);
    var affected = stack.stream()
      .flatMap(group -> group.getAffectedDocuments().stream())
      .distinct()
      .map(Objects::toString)
      .collect(Collectors.joining(", ", "[", "]"));
    return """
      %s affected %s
      %s""".formatted(getStacksDescription(), affected, dumpStack(stack));
  }

  void clearAllStacks() {
    globalStack.clear();
    clearDocumentStacks();
  }

  private void clearDocumentStacks() {
    documentStacks.clear();
    documentWithoutFileStacks.clearStacks();
    nonlocalVirtualFileStacks.clearStacks();
  }

  private void removeEmptyStacks() {
    documentStacks.entrySet().removeIf(each -> each.getValue().isEmpty());
    CollectionFactory.trimMap(documentStacks); // make sure the following entrySet iteration will not go over empty buckets
    documentWithoutFileStacks.removeEmptyStacks();
    nonlocalVirtualFileStacks.removeEmptyStacks();
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

  private @NotNull Set<DocumentReference> getAffectedDocuments() {
    Set<DocumentReference> result = new HashSet<>();
    collectAllAffectedDocuments(result);
    return result;
  }

  private @NotNull String getStacksDescription() {
    return isUndo ? "undo_stacks" : "redo_stacks";
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
