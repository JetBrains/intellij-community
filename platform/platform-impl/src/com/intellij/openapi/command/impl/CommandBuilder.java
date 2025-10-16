// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.Command;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ExternalChangeActionUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


final class CommandBuilder {

  private final @Nullable Project undoProject; // null - global, isDefault - error
  private final boolean isTransparentSupported;

  private @Nullable Project commandProject;
  private @NotNull CurrentEditorProvider editorProvider;
  private @NotNull CommandId commandId;
  private @Nullable @Command String commandName;
  private @Nullable Object groupId;
  private @NotNull UndoConfirmationPolicy confirmationPolicy;
  private @Nullable EditorAndState editorStateBefore;
  private @Nullable EditorAndState editorStateAfter;
  private @Nullable DocumentReference originalDocument;
  private @NotNull Collection<UndoableAction> undoableActions;
  private @NotNull UndoAffectedDocuments affectedDocuments;
  private @NotNull UndoAffectedDocuments additionalAffectedDocuments;
  private boolean isForcedGlobal;
  private boolean isTransparent;
  private boolean isValid;
  private boolean isInsideCommand;

  CommandBuilder(@Nullable Project undoProject, boolean isTransparentSupported) {
    this.undoProject = undoProject;
    this.isTransparentSupported = isTransparentSupported;
    reset();
  }

  boolean isInsideCommand() {
    return isInsideCommand;
  }

  boolean isActive() {
    return isInsideCommand() && commandProject == undoProject;
  }

  void commandStarted(
    @Nullable Project commandProject,
    @Nullable @Command String commandName,
    @Nullable Object commandGroupId,
    @NotNull UndoConfirmationPolicy confirmationPolicy,
    @NotNull CurrentEditorProvider editorProvider,
    boolean recordOriginalDocument,
    boolean isTransparent
  ) {
    assertOutsideCommand();
    CommandId id = CommandIdService.currCommandId();
    this.commandId = id == null ? NoCommandId.INSTANCE : id;
    this.commandProject = commandProject;
    this.commandName = commandName;
    this.groupId = commandGroupId;
    this.confirmationPolicy = confirmationPolicy;
    this.editorProvider = new StableEditorProvider(editorProvider.getCurrentEditor(undoProject));
    this.editorStateBefore = currentEditorState();
    this.originalDocument = recordOriginalDocument ? originalDocument() : null;
    this.isTransparent = isTransparent;
    this.isInsideCommand = true;
  }

  boolean hasActions() {
    assertInsideCommand();
    return !undoableActions.isEmpty();
  }

  void addUndoableAction(@NotNull UndoableAction action) {
    assertInsideCommand();
    if (isRefresh()) {
      originalDocument = null;
    }
    undoableActions.add(action);
    affectedDocuments.addAffected(action.getAffectedDocuments());
    isForcedGlobal = isForcedGlobal || action.isGlobal();
  }

  void addDocumentAsAffected(@NotNull DocumentReference docRef) {
    assertInsideCommand();
    if (!hasChangesOf(docRef)) {
      DocumentReference[] refs = { docRef };
      addUndoableAction(new MentionOnlyUndoableAction(refs));
    }
  }

  void addAffectedDocuments(Document @NotNull ... docs) {
    assertInsideCommand();
    additionalAffectedDocuments.addAffected(docs);
  }

  void addAffectedFiles(VirtualFile @NotNull ... files) {
    assertInsideCommand();
    additionalAffectedDocuments.addAffected(files);
  }

  void markAsGlobal() {
    assertInsideCommand();
    isForcedGlobal = true;
  }

  void invalidateIfAffects(@NotNull DocumentReference docRef) {
    assertInsideCommand();
    if (affectedDocuments.affects(docRef)) {
      isValid = false;
    }
  }

  @NotNull PerformedCommand commandFinished(@Nullable @Command String commandName, @Nullable Object groupId) {
    assertInsideCommand();
    this.commandName = commandName;
    this.groupId = groupId;
    this.editorStateAfter = currentEditorState();
    if (originalDocument != null && hasActions() && !isTransparent() && affectedDocuments.affectsOnlyPhysical()) {
      addDocumentAsAffected(Objects.requireNonNull(originalDocument));
    }
    return buildAndReset();
  }

  private @NotNull PerformedCommand buildAndReset() {
    PerformedCommand performedCommand = new PerformedCommand(
      commandId,
      commandName,
      groupId,
      confirmationPolicy,
      editorStateBefore,
      editorStateAfter,
      undoableActions,
      affectedDocuments,
      additionalAffectedDocuments,
      isTransparent(),
      isForcedGlobal,
      isGlobal(),
      hasActions(),
      isValid,
      shouldClearRedoStack()
    );
    reset();
    return performedCommand;
  }

  private @Nullable EditorAndState currentEditorState() {
    return EditorAndState.getStateFor(undoProject, editorProvider);
  }

  private @Nullable DocumentReference originalDocument() {
    if (undoProject != null && undoProject == commandProject) {
      // note: originatorReference depends on FocusedComponent :sad_trombone_for_rd:, see IJPL-192250
      return UndoDocumentUtil.getDocReference(undoProject, editorProvider);
    }
    return null;
  }

  private boolean isTransparent() {
    if (isTransparentSupported) {
      return isTransparent;
    }
    return isTransparent && !hasActions();
  }

  private boolean hasChangesOf(@NotNull DocumentReference ref) {
    for (UndoableAction action : undoableActions) {
      DocumentReference[] refs = action.getAffectedDocuments();
      if (refs != null && ArrayUtil.contains(ref, refs)) {
        return true;
      }
    }
    return hasActions() && additionalAffectedDocuments.affects(ref);
  }

  private boolean shouldClearRedoStack() {
    return !isTransparent() && hasActions();
  }

  private boolean isGlobal() {
    return isForcedGlobal || affectedDocuments.affectsMultiplePhysical();
  }

  private void reset() {
    this.commandProject = null;
    this.editorProvider = NoEditorProvider.INSTANCE;
    this.commandId = NoCommandId.INSTANCE;
    this.commandName = null;
    this.groupId = null;
    this.confirmationPolicy = UndoConfirmationPolicy.DEFAULT;
    this.editorStateBefore = null;
    this.editorStateAfter = null;
    this.originalDocument = null;
    this.undoableActions = new ArrayList<>(2);
    this.affectedDocuments = new UndoAffectedDocuments();
    this.additionalAffectedDocuments = new UndoAffectedDocuments();
    this.isForcedGlobal = false;
    this.isTransparent = false;
    this.isValid = true;
    this.isInsideCommand = false;
  }

  private void assertInsideCommand() {
    if (!isInsideCommand) {
      throw new UndoIllegalStateException("Must be called inside a command");
    }
  }

  private void assertOutsideCommand() {
    if (isInsideCommand) {
      throw new UndoIllegalStateException("Must be called outside a command");
    }
  }

  private static boolean isRefresh() {
    return ExternalChangeActionUtil.isExternalChangeInProgress();
  }

  private static final class NoCommandId implements CommandId {
    static final NoCommandId INSTANCE = new NoCommandId();

    @Override
    public boolean isCompatible(@NotNull CommandId commandId) {
      return true;
    }
  }

  private static final class NoEditorProvider implements CurrentEditorProvider {
    static final CurrentEditorProvider INSTANCE = new NoEditorProvider();

    @Override
    public @Nullable FileEditor getCurrentEditor(@Nullable Project project) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class StableEditorProvider implements CurrentEditorProvider {
    private final @Nullable FileEditor editor;

    StableEditorProvider(@Nullable FileEditor editor) {
      this.editor = editor;
    }

    @SuppressWarnings("UsagesOfObsoleteApi")
    @Override
    public @Nullable FileEditor getCurrentEditor() {
      return editor;
    }

    @Override
    public @Nullable FileEditor getCurrentEditor(@Nullable Project project) {
      return editor;
    }
  }
}
