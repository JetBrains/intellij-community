// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts.Command;
import com.intellij.reference.SoftReference;
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

  private @NotNull List<CommandId> commandIds = new ArrayList<>();
  private @Nullable @Command String commandName;
  private @Nullable Reference<Object> lastGroupId; // weak reference to avoid memleaks when clients pass some exotic objects as commandId
  private @NotNull UndoRedoList<UndoableAction> undoableActions = new UndoRedoList<>();
  private @NotNull UndoAffectedDocuments affectedDocuments = new UndoAffectedDocuments();
  private @NotNull UndoAffectedDocuments additionalAffectedDocuments = new UndoAffectedDocuments();
  private @NotNull UndoConfirmationPolicy undoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT;
  private @Nullable EditorAndState editorStateBefore;
  private @Nullable EditorAndState editorStateAfter;
  private boolean isForcedGlobal;
  private boolean isTransparent;
  private boolean isValid = true;

  CommandMerger(boolean isLocalHistoryActivity, boolean isTransparentSupported) {
    this.isLocalHistoryActivity = isLocalHistoryActivity;
    this.isTransparentSupported = isTransparentSupported;
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

  @Nullable UndoCommandFlushReason shouldFlush(@NotNull PerformedCommand performedCommand) {
    //noinspection ConstantValue
    if (!isCompatible(performedCommand.commandId())) {
      return createFlushReason("INCOMPATIBLE_COMMAND", performedCommand);
    }
    if (isTransparentSupported &&
        performedCommand.isTransparent() &&
        performedCommand.editorStateAfter() == null &&
        editorStateAfter != null) {
      return createFlushReason("NEXT_TRANSPARENT_WITHOUT_EDITOR_STATE_AFTER", performedCommand);
    }
    if (isTransparentSupported &&
        isTransparent() &&
        editorStateBefore == null &&
        performedCommand.editorStateBefore() != null) {
      return createFlushReason("CURRENT_TRANSPARENT_WITHOUT_EDITOR_STATE_BEFORE", performedCommand);
    }
    if (isTransparent() || performedCommand.isTransparent()) {
      boolean changedDocs = hasActions() &&
                            performedCommand.hasActions() &&
                            !affectedDocuments.equals(performedCommand.affectedDocuments());
      return changedDocs ? createFlushReason("TRANSPARENT_WITH_DIFFERENT_DOCS", performedCommand) : null;
    }
    if ((isForcedGlobal || performedCommand.isForcedGlobal()) && !isMergeGlobalCommandsAllowed()) {
      return createFlushReason("GLOBAL", performedCommand);
    }
    boolean canMergeGroup = canMergeGroup(performedCommand.groupId(), SoftReference.dereference(lastGroupId));
    return canMergeGroup ? null : createFlushReason("CHANGED_GROUP", performedCommand);
  }

  @Nullable UndoableGroup formGroup(@NotNull UndoCommandFlushReason flushReason, int commandTimestamp) {
    UndoableGroup group = !hasActions() ? null : createUndoableGroup(flushReason, commandTimestamp);
    reset();
    return group;
  }

  void mergeWithPerformedCommand(@NotNull PerformedCommand performedCommand) {
    mergeState(performedCommand);
    if (!performedCommand.isTransparent() && hasActions()) {
      Object groupId = performedCommand.groupId();
      if (groupId != SoftReference.dereference(lastGroupId)) {
        lastGroupId = groupId == null ? null : new WeakReference<>(groupId);
      }
      if (commandName == null) {
        commandName = performedCommand.commandName();
      }
    }
  }

  void invalidateActionsFor(@NotNull DocumentReference ref) {
    if (affectedDocuments.affects(ref)) {
      isValid = false;
    }
  }

  @Nullable LocalCommandMergerSnapshot getSnapshot(@NotNull DocumentReference reference) {
    if (isGlobal() || additionalAffectedDocuments.size() > 0 || affectedDocuments.size() > 1) {
      return null;
    }
    if (affectedDocuments.size() == 1) {
      DocumentReference currentReference = affectedDocuments.firstAffected();
      if (currentReference != reference) {
        return null;
      }
    }
    return new LocalCommandMergerSnapshot(
      affectedDocuments.firstAffected(),
      undoableActions.snapshot(),
      lastGroupId,
      isTransparent(),
      commandName,
      editorStateBefore,
      editorStateAfter,
      undoConfirmationPolicy
    );
  }

  boolean resetLocalHistory(@NotNull LocalCommandMergerSnapshot snapshot) {
    var references = new UndoAffectedDocuments();
    references.addAffected(snapshot.getDocumentReferences());
    reset(
      new ArrayList<>(), // TODO: snapshot me
      snapshot.getActions().toList(),
      references,
      new UndoAffectedDocuments(),
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
    undoableActions.removeIf(action -> {
      // remove UndoAction only if it doesn't contain anything but `document`, to avoid messing up with (very rare) complex undo actions containing several documents
      DocumentReference[] refs = ObjectUtils.notNull(action.getAffectedDocuments(), DocumentReference.EMPTY_ARRAY);
      return ContainerUtil.and(refs, ref -> ref.equals(refByDoc) || ref.equals(refByFile));
    });
    affectedDocuments.removeAffected(refByFile);
    affectedDocuments.removeAffected(refByDoc);
    additionalAffectedDocuments.removeAffected(refByFile);
    additionalAffectedDocuments.removeAffected(refByDoc);
  }

  @Nullable String getCommandName() {
    return commandName;
  }

  boolean isGlobal() {
    return isForcedGlobal || affectedDocuments.affectsMultiplePhysical();
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
    return !undoableActions.isEmpty();
  }

  @NotNull UndoRedoList<UndoableAction> getCurrentActions() {
    return undoableActions;
  }

  boolean isValid() {
    return isValid;
  }

  @NotNull Collection<DocumentReference> getAffectedDocuments() {
    return affectedDocuments.asCollection();
  }

  @NotNull Collection<DocumentReference> getAdditionalAffectedDocuments() {
    return additionalAffectedDocuments.asCollection();
  }

  @NotNull Collection<CommandId> getCommandIds() {
    return commandIds;
  }

  @Nullable EditorAndState getStateBefore() {
    return editorStateBefore;
  }

  @Nullable EditorAndState getStateAfter() {
    return editorStateAfter;
  }

  @NotNull String dumpState() {
    return UndoDumpUnit.fromMerger(this).toString();
  }

  private void setEditorStateBefore(@Nullable EditorAndState state) {
    if (editorStateBefore == null || !hasActions()) {
      editorStateBefore = state;
    }
  }

  private void setEditorStateAfter(@Nullable EditorAndState state) {
    editorStateAfter = state;
  }

  private void mergeConfirmationPolicy(@NotNull UndoConfirmationPolicy newConfirmationPolicy) {
    if (undoConfirmationPolicy == UndoConfirmationPolicy.DEFAULT) {
      undoConfirmationPolicy = newConfirmationPolicy;
    }
    else if (undoConfirmationPolicy == UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION) {
      if (newConfirmationPolicy == UndoConfirmationPolicy.REQUEST_CONFIRMATION) {
        undoConfirmationPolicy = UndoConfirmationPolicy.REQUEST_CONFIRMATION;
      }
    }
  }

  private boolean hasChangesOf(DocumentReference ref) {
    for (UndoableAction action : undoableActions) {
      DocumentReference[] refs = action.getAffectedDocuments();
      if (refs == null || ArrayUtil.contains(ref, refs)) {
        return true;
      }
    }
    return hasActions() && additionalAffectedDocuments.affects(ref);
  }

  private void mergeState(@NotNull PerformedCommand performedCommand) {
    commandIds.add(performedCommand.commandId());
    setEditorStateBefore(performedCommand.editorStateBefore());
    setEditorStateAfter(performedCommand.editorStateAfter());
    if (isTransparent()) { // todo write test
      if (performedCommand.hasActions()) {
        isTransparent = performedCommand.isTransparent();
      }
    } else {
      if (!hasActions()) {
        isTransparent = performedCommand.isTransparent();
      }
    }
    isValid &= performedCommand.isValid();
    isForcedGlobal |= performedCommand.isForcedGlobal();
    undoableActions.addAll(performedCommand.undoableActions());
    affectedDocuments.addAffected(performedCommand.affectedDocuments());
    additionalAffectedDocuments.addAffected(performedCommand.additionalAffectedDocuments());
    mergeConfirmationPolicy(performedCommand.confirmationPolicy());
  }

  private @NotNull UndoCommandFlushReason createFlushReason(@NotNull String reason, @NotNull PerformedCommand performedCommand) {
    return UndoCommandFlushReason.cannotMergeCommands(
      reason,
      commandName,
      SoftReference.dereference(lastGroupId),
      isTransparent(),
      isForcedGlobal,
      null,
      performedCommand.groupId(),
      performedCommand.isTransparent(),
      performedCommand.isGlobal()
    );
  }

  private @NotNull UndoableGroup createUndoableGroup(@NotNull UndoCommandFlushReason flushReason, int commandTimestamp) {
    if (additionalAffectedDocuments.size() > 0) {
      DocumentReference[] refs = additionalAffectedDocuments.asCollection().toArray(DocumentReference.EMPTY_ARRAY);
      undoableActions.add(new MyEmptyUndoableAction(refs));
    }
    return new UndoableGroup(
      commandIds,
      commandName,
      undoableActions,
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

  private void reset() {
    reset(
      new ArrayList<>(),
      new UndoRedoList<>(),
      new UndoAffectedDocuments(),
      new UndoAffectedDocuments(),
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
    List<CommandId> commandIds,
    UndoRedoList<UndoableAction> currentActions,
    UndoAffectedDocuments allAffectedDocuments,
    UndoAffectedDocuments additionalAffectedDocuments,
    Reference<Object> lastGroupId,
    boolean forcedGlobal,
    boolean transparent,
    @Command String commandName,
    boolean isValid,
    EditorAndState editorStateBefore,
    EditorAndState editorStateAfter,
    UndoConfirmationPolicy undoConfirmationPolicy
  ) {
    this.commandIds = commandIds;
    this.undoableActions = currentActions;
    this.affectedDocuments = allAffectedDocuments;
    this.additionalAffectedDocuments = additionalAffectedDocuments;
    this.lastGroupId = lastGroupId;
    this.isForcedGlobal = forcedGlobal;
    this.isTransparent = transparent;
    this.commandName = commandName;
    this.isValid = isValid;
    this.editorStateBefore = editorStateBefore;
    this.editorStateAfter = editorStateAfter;
    this.undoConfirmationPolicy = undoConfirmationPolicy;
  }

  private boolean hasNonUndoableActions() {
    for (UndoableAction each : undoableActions) {
      if (each instanceof NonUndoableAction) {
        return true;
      }
    }
    return false;
  }

  private boolean isCompatible(@NotNull CommandId commandId) {
    //noinspection ConstantValue
    if (true) { // TODO: transparent commands from the BE ruin the stack
      return true;
    }
    if (commandIds.isEmpty()) {
      return true;
    }
    return commandIds.getFirst().isCompatible(commandId);
  }

  private static boolean isMergeGlobalCommandsAllowed() {
    return ((CoreCommandProcessor)CommandProcessor.getInstance()).isMergeGlobalCommandsAllowed();
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
