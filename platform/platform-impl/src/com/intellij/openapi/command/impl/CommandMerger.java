// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
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

  private final @Nullable Project project;
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

  CommandMerger(@Nullable Project project, boolean isTransparent) {
    this.project = project;
    this.isTransparent = isTransparent;
  }

  boolean isUndoAvailable(@NotNull Collection<? extends DocumentReference> refs) {
    if (hasNonUndoableActions()) {
      return false;
    }
    if (refs.isEmpty()) {
      return isGlobal() && hasActions();
    }
    for (DocumentReference each : refs) {
      if (hasChangesOf(each, false)) {
        return true;
      }
    }
    return false;
  }

  void addAction(@NotNull UndoableAction action) {
    currentActions.add(action);
    DocumentReference[] refs = action.getAffectedDocuments();
    if (refs != null) {
      Collections.addAll(allAffectedDocuments, refs);
    }
    isForcedGlobal |= action.isGlobal();
  }

  boolean shouldMerge(@Nullable Object groupId, @NotNull CommandMerger nextCommandToMerge) {
    if (nextCommandToMerge.isTransparent() && nextCommandToMerge.editorStateAfter == null && editorStateAfter != null) {
      return false;
    }
    if (isTransparent() && editorStateBefore == null && nextCommandToMerge.editorStateBefore != null) {
      return false;
    }
    if (isTransparent() || nextCommandToMerge.isTransparent()) {
      return !hasActions() || !nextCommandToMerge.hasActions() || allAffectedDocuments.equals(nextCommandToMerge.allAffectedDocuments);
    }
    if ((isForcedGlobal || nextCommandToMerge.isForcedGlobal) && !isMergeGlobalCommandsAllowed()) {
      return false;
    }
    return canMergeGroup(groupId, SoftReference.dereference(lastGroupId));
  }

  void flushCurrentCommand(@NotNull UndoRedoStacksHolder stacksHolder, int commandTimestamp) {
    if (hasActions()) {
      if (!additionalAffectedDocuments.isEmpty()) {
        DocumentReference[] refs = additionalAffectedDocuments.toArray(DocumentReference.EMPTY_ARRAY);
        currentActions.add(new MyEmptyUndoableAction(refs));
      }
      stacksHolder.addToStacks(
        new UndoableGroup(
          commandName,
          isGlobal(),
          commandTimestamp,
          editorStateBefore,
          editorStateAfter,
          currentActions,
          stacksHolder,
          project,
          undoConfirmationPolicy,
          isTransparent(),
          isValid
        )
      );
    }
    reset();
  }

  void commandFinished(@Command String commandName, Object groupId, @NotNull CommandMerger nextCommandToMerge) {
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

  boolean hasChangesOf(DocumentReference ref, boolean onlyDirectChanges) {
    for (UndoableAction action : currentActions) {
      DocumentReference[] refs = action.getAffectedDocuments();
      if (refs == null) {
        if (!onlyDirectChanges) {
          return true;
        }
      }
      else if (ArrayUtil.contains(ref, refs)) {
        return true;
      }
    }
    return hasActions() && additionalAffectedDocuments.contains(ref);
  }

  void setEditorStateBefore(@Nullable EditorAndState state) {
    if (editorStateBefore == null || !hasActions()) {
      editorStateBefore = state;
    }
  }

  void setEditorStateAfter(@Nullable EditorAndState state) {
    editorStateAfter = state;
  }

  void addAdditionalAffectedDocuments(@NotNull Collection<? extends DocumentReference> refs) {
    allAffectedDocuments.addAll(refs);
    additionalAffectedDocuments.addAll(refs);
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
      isTransparent,
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

  void mergeUndoConfirmationPolicy(UndoConfirmationPolicy undoConfirmationPolicy) {
    if (this.undoConfirmationPolicy == UndoConfirmationPolicy.DEFAULT) {
      this.undoConfirmationPolicy = undoConfirmationPolicy;
    }
    else if (this.undoConfirmationPolicy == UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION) {
      if (undoConfirmationPolicy == UndoConfirmationPolicy.REQUEST_CONFIRMATION) {
        this.undoConfirmationPolicy = UndoConfirmationPolicy.REQUEST_CONFIRMATION;
      }
    }
  }

  boolean isPhysical() {
    if (allAffectedDocuments.isEmpty()) {
      return false;
    }
    for (DocumentReference each : allAffectedDocuments) {
      if (isVirtualDocumentChange(each.getFile())) {
        return false;
      }
    }
    return true;
  }

  @Nullable String getCommandName() {
    return commandName;
  }

  boolean isGlobal() {
    return isForcedGlobal || affectsMultiplePhysicalDocs();
  }

  void markAsGlobal() {
    isForcedGlobal = true;
  }

  boolean isTransparent() {
    return isTransparent;
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
    return UndoUnit.fromMerger(this).toString();
  }

  boolean isSpeculativeUndoAllowed() {
    return SpeculativeUndoableAction.isUndoable(
      getCommandName(),
      isGlobal(),
      isTransparent(),
      getUndoConfirmationPolicy(),
      getCurrentActions()
    );
  }

  private void merge(@NotNull CommandMerger nextCommandToMerge) {
    setEditorStateBefore(nextCommandToMerge.editorStateBefore);
    editorStateAfter = nextCommandToMerge.editorStateAfter;
    if (isTransparent) { // todo write test
      if (nextCommandToMerge.hasActions()) {
        isTransparent = nextCommandToMerge.isTransparent;
      }
    } else {
      if (!hasActions()) {
        isTransparent = nextCommandToMerge.isTransparent;
      }
    }
    isValid &= nextCommandToMerge.isValid;
    isForcedGlobal |= nextCommandToMerge.isForcedGlobal;
    currentActions.addAll(nextCommandToMerge.currentActions);
    allAffectedDocuments.addAll(nextCommandToMerge.allAffectedDocuments);
    additionalAffectedDocuments.addAll(nextCommandToMerge.additionalAffectedDocuments);
    mergeUndoConfirmationPolicy(nextCommandToMerge.getUndoConfirmationPolicy());
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
