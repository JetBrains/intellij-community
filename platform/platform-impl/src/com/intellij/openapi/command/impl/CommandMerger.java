// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts.Command;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.reference.SoftReference;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;


@ApiStatus.Internal
public final class CommandMerger {

  public static boolean canMergeGroup(Object groupId, Object lastGroupId) {
    return groupId != null && Comparing.equal(lastGroupId, groupId);
  }

  private final boolean isLocalHistoryActivity;
  private final boolean isTransparentSupported;
  private @Nullable @Command String commandName;
  private @Nullable Reference<Object> lastGroupId; // weak reference to avoid memleaks when clients pass some exotic objects as commandId
  private @NotNull UndoRedoList<UndoableAction> currentActions = new UndoRedoList<>();
  private @NotNull Set<DocumentReference> allAffectedDocuments = new HashSet<>();
  private @NotNull Set<DocumentReference> additionalAffectedDocuments = new HashSet<>();
  private @NotNull UndoConfirmationPolicy undoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT;
  private @Nullable EditorAndState editorStateBefore;
  private @Nullable EditorAndState editorStateAfter;
  private boolean isForcedGlobal;
  private boolean isTransparent;
  private boolean isValid = true;

  CommandMerger(boolean isLocalHistoryActivity, boolean isTransparent, boolean isTransparentSupported) {
    this.isLocalHistoryActivity = isLocalHistoryActivity;
    this.isTransparentSupported = isTransparentSupported;
    this.isTransparent = isTransparent;
  }

  boolean isUndoAvailable(@NotNull Collection<DocumentReference> refs) {
    if (hasNonUndoableActions()) {
      return false;
    }
    if (refs.isEmpty()) {
      return isGlobal() && hasActions();
    }
    for (DocumentReference doc : refs) {
      if (hasChangesOf(doc)) {
        return true;
      }
    }
    return false;
  }

  @Nullable UndoCommandFlushReason shouldFlush(@Nullable Object nextGroupId, @NotNull UndoCommandData nextCommand) {
    if (isTransparentSupported && nextCommand.isTransparent() && nextCommand.getEditorStateAfter() == null && editorStateAfter != null) {
      return createFlushReason("NEXT_TRANSPARENT_WITHOUT_EDITOR_STATE_AFTER", nextGroupId, nextCommand);
    }
    if (isTransparentSupported && isTransparent() && editorStateBefore == null && nextCommand.getEditorStateBefore() != null) {
      return createFlushReason("CURRENT_TRANSPARENT_WITHOUT_EDITOR_STATE_BEFORE", nextGroupId, nextCommand);
    }
    if (isTransparent() || nextCommand.isTransparent()) {
      boolean changedDocs = hasActions() &&
                            nextCommand.hasActions() &&
                            !allAffectedDocuments.equals(nextCommand.getAllAffectedDocuments());
      return changedDocs ? createFlushReason("TRANSPARENT_WITH_DIFFERENT_DOCS", nextGroupId, nextCommand) : null;
    }
    if ((isForcedGlobal || nextCommand.isForcedGlobal()) && !isMergeGlobalCommandsAllowed()) {
      return createFlushReason("GLOBAL", nextGroupId, nextCommand);
    }
    boolean canMergeGroup = canMergeGroup(nextGroupId, SoftReference.dereference(lastGroupId));
    return canMergeGroup ? null : createFlushReason("CHANGED_GROUP", nextGroupId, nextCommand);
  }

  @Nullable UndoableGroup formGroup(@NotNull UndoCommandFlushReason flushReason, int commandTimestamp) {
    UndoableGroup group;
    if (hasActions()) {
      if (!additionalAffectedDocuments.isEmpty()) {
        DocumentReference[] refs = additionalAffectedDocuments.toArray(DocumentReference.EMPTY_ARRAY);
        currentActions.add(new MyEmptyUndoableAction(refs));
      }
      group = new UndoableGroup(
        commandName,
        currentActions,
        undoConfirmationPolicy,
        editorStateBefore,
        editorStateAfter,
        flushReason,
        commandTimestamp,
        isLocalHistoryActivity,
        isTransparent(),
        isGlobal(),
        isValid
      );
    }
    else {
      group = null;
    }
    reset();
    return group;
  }

  void merge(@Command String commandName, Object groupId, @NotNull UndoCommandData nextCommandToMerge) {
    merge(nextCommandToMerge);
    if (nextCommandToMerge.isTransparent() || !hasActions()) {
      return;
    }
    if (groupId != SoftReference.dereference(lastGroupId)) {
      lastGroupId = groupId == null ? null : new WeakReference<>(groupId);
    }
    if (this.commandName == null) {
      this.commandName = commandName;
    }
  }

  void setEditorStateBefore(@Nullable EditorAndState state) {
    if (editorStateBefore == null || !hasActions()) {
      editorStateBefore = state;
    }
  }

  void invalidateActionsFor(@NotNull DocumentReference ref) {
    if (allAffectedDocuments.contains(ref)) {
      isValid = false;
    }
  }

  @Nullable LocalCommandMergerSnapshot getSnapshot(@NotNull DocumentReference reference) {
    if (isGlobal() || !additionalAffectedDocuments.isEmpty() || allAffectedDocuments.size() > 1) {
      return null;
    }
    if (allAffectedDocuments.size() == 1) {
      DocumentReference currentReference = allAffectedDocuments.iterator().next();
      if (currentReference != reference) {
        return null;
      }
    }
    return new LocalCommandMergerSnapshot(
      allAffectedDocuments.stream().findFirst().orElse(null),
      currentActions.snapshot(),
      lastGroupId,
      isTransparent(),
      commandName,
      editorStateBefore,
      editorStateAfter,
      undoConfirmationPolicy
    );
  }

  boolean resetLocalHistory(@NotNull LocalCommandMergerSnapshot snapshot) {
    HashSet<DocumentReference> references = new HashSet<>();
    DocumentReference reference = snapshot.getDocumentReferences();
    if (reference != null) {
      references.add(reference);
    }
    reset(
      snapshot.getActions().toList(),
      references,
      new HashSet<>(),
      snapshot.getLastGroupId(),
      false,
      snapshot.getTransparent(),
      snapshot.getCommandName(),
      true,
      snapshot.getStateBefore(),
      snapshot.getStateAfter(),
      snapshot.getUndoConfirmationPolicy()
    );
    return true;
  }

  // remove all references to document to avoid memory leaks
  void clearDocumentReferences(@NotNull Document document) {
    ThreadingAssertions.assertEventDispatchThread();
    // DocumentReference for document is not equal to the DocumentReference from the file of that doc, so try both
    DocumentReference refByFile = DocumentReferenceManager.getInstance().create(document);
    DocumentReference refByDoc = new DocumentReferenceByDocument(document);
    currentActions.removeIf(action -> {
      // remove UndoAction only if it doesn't contain anything but `document`, to avoid messing up with (very rare) complex undo actions containing several documents
      DocumentReference[] refs = ObjectUtils.notNull(action.getAffectedDocuments(), DocumentReference.EMPTY_ARRAY);
      return ContainerUtil.and(refs, ref -> ref.equals(refByDoc) || ref.equals(refByFile));
    });
    allAffectedDocuments.remove(refByFile);
    allAffectedDocuments.remove(refByDoc);
    additionalAffectedDocuments.remove(refByFile);
    additionalAffectedDocuments.remove(refByDoc);
  }

  void mergeUndoConfirmationPolicy(@NotNull UndoConfirmationPolicy undoConfirmationPolicy) {
    if (this.undoConfirmationPolicy == UndoConfirmationPolicy.DEFAULT) {
      this.undoConfirmationPolicy = undoConfirmationPolicy;
    }
    else if (this.undoConfirmationPolicy == UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION) {
      if (undoConfirmationPolicy == UndoConfirmationPolicy.REQUEST_CONFIRMATION) {
        this.undoConfirmationPolicy = UndoConfirmationPolicy.REQUEST_CONFIRMATION;
      }
    }
  }

  @Nullable String getCommandName() {
    return commandName;
  }

  boolean isGlobal() {
    return isForcedGlobal || affectsMultiplePhysicalDocs();
  }

  boolean isTransparent() {
    if (isTransparentSupported) {
      return isTransparent;
    }
    return isTransparent && !hasActions();
  }

  @NotNull UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return undoConfirmationPolicy;
  }

  boolean hasActions() {
    return !currentActions.isEmpty();
  }

  @NotNull UndoRedoList<UndoableAction> getCurrentActions() {
    return currentActions;
  }

  boolean isValid() {
    return isValid;
  }

  @NotNull Set<DocumentReference> getAllAffectedDocuments() {
    return allAffectedDocuments;
  }

  @NotNull Set<DocumentReference> getAdditionalAffectedDocuments() {
    return additionalAffectedDocuments;
  }

  @NotNull String dumpState() {
    return UndoDumpUnit.fromMerger(this).toString();
  }

  private boolean hasChangesOf(DocumentReference ref) {
    for (UndoableAction action : currentActions) {
      DocumentReference[] refs = action.getAffectedDocuments();
      if (refs == null) {
        return true;
      }
      else if (ArrayUtil.contains(ref, refs)) {
        return true;
      }
    }
    return hasActions() && additionalAffectedDocuments.contains(ref);
  }

  private void merge(@NotNull UndoCommandData nextCommandToMerge) {
    setEditorStateBefore(nextCommandToMerge.getEditorStateBefore());
    editorStateAfter = nextCommandToMerge.getEditorStateAfter();
    if (isTransparent()) { // todo write test
      if (nextCommandToMerge.hasActions()) {
        isTransparent = nextCommandToMerge.isTransparent();
      }
    } else {
      if (!hasActions()) {
        isTransparent = nextCommandToMerge.isTransparent();
      }
    }
    isValid &= nextCommandToMerge.isValid();
    isForcedGlobal |= nextCommandToMerge.isForcedGlobal();
    currentActions.addAll(nextCommandToMerge.getUndoableActions());
    allAffectedDocuments.addAll(nextCommandToMerge.getAllAffectedDocuments());
    additionalAffectedDocuments.addAll(nextCommandToMerge.getAdditionalAffectedDocuments());
    mergeUndoConfirmationPolicy(nextCommandToMerge.getUndoConfirmationPolicy());
  }

  private @NotNull UndoCommandFlushReason createFlushReason(
    @NotNull String reason,
    @Nullable Object nextGroupId,
    @NotNull UndoCommandData nextCommandToMerge
  ) {
    return UndoCommandFlushReason.cannotMergeCommands(
      reason,
      commandName,
      SoftReference.dereference(lastGroupId),
      isTransparent(),
      isForcedGlobal,
      null,
      nextGroupId,
      nextCommandToMerge.isTransparent(),
      nextCommandToMerge.isGlobal()
    );
  }

  private void reset() {
    reset(
      new UndoRedoList<>(),
      new HashSet<>(),
      new HashSet<>(),
      null,
      false,
      false,
      null,
      true,
      null,
      null,
      UndoConfirmationPolicy.DEFAULT
    );
  }

  @SuppressWarnings("SameParameterValue")
  private void reset(
    UndoRedoList<UndoableAction> currentActions,
    HashSet<DocumentReference> allAffectedDocuments,
    HashSet<DocumentReference> additionalAffectedDocuments,
    Reference<Object> lastGroupId,
    boolean forcedGlobal,
    boolean transparent,
    @Command String commandName,
    boolean isValid,
    EditorAndState editorStateBefore,
    EditorAndState editorStateAfter,
    UndoConfirmationPolicy undoConfirmationPolicy
  ) {
    this.currentActions = currentActions;
    this.allAffectedDocuments = allAffectedDocuments;
    this.additionalAffectedDocuments = additionalAffectedDocuments;
    this.lastGroupId = lastGroupId;
    this.isForcedGlobal = forcedGlobal;
    this.isTransparent = transparent;
    this.commandName = commandName;
    this.isValid = isValid;
    this.editorStateAfter = editorStateAfter;
    this.editorStateBefore = editorStateBefore;
    this.undoConfirmationPolicy = undoConfirmationPolicy;
  }

  private boolean affectsMultiplePhysicalDocs() {
    Set<VirtualFile> affectedFiles = new HashSet<>();
    for (DocumentReference each : allAffectedDocuments) {
      VirtualFile file = each.getFile();
      if (isVirtualDocumentChange(file)) {
        continue;
      }
      affectedFiles.add(file);
      if (affectedFiles.size() > 1) {
        return true;
      }
    }
    return false;
  }

  private boolean hasNonUndoableActions() {
    for (UndoableAction each : currentActions) {
      if (each instanceof NonUndoableAction) {
        return true;
      }
    }
    return false;
  }

  private static boolean isMergeGlobalCommandsAllowed() {
    return ((CoreCommandProcessor)CommandProcessor.getInstance()).isMergeGlobalCommandsAllowed();
  }

  private static boolean isVirtualDocumentChange(VirtualFile file) {
    return file == null || file instanceof LightVirtualFile;
  }

  private static final class MyEmptyUndoableAction extends BasicUndoableAction {
    MyEmptyUndoableAction(DocumentReference @NotNull [] refs) {
      super(refs);
    }

    @Override
    public void undo() {
    }

    @Override
    public void redo() {
    }
  }
}
