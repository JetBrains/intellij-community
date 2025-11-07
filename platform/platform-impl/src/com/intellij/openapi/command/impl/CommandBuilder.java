// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ExternalChangeActionUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


final class CommandBuilder {

  private static final Logger LOG = Logger.getInstance(CommandBuilder.class);

  private final @Nullable Project undoProject; // null - global, isDefault - error
  private final boolean isTransparentSupported;
  private final boolean isGroupIdChangeSupported;

  private @NotNull CmdEvent cmdEvent;
  private @Nullable Throwable tracedStartCommand;
  private @NotNull CurrentEditorProvider editorProvider;
  private @Nullable EditorAndState editorStateBefore;
  private @Nullable EditorAndState editorStateAfter;
  private @Nullable DocumentReference originalDocument;
  private @NotNull Collection<UndoableAction> undoableActions;
  private @NotNull UndoAffectedDocuments affectedDocuments;
  private @NotNull UndoAffectedDocuments additionalAffectedDocuments;
  private boolean isForcedGlobal;
  private boolean isValid;
  private boolean isInsideCommand;

  CommandBuilder(@Nullable Project undoProject, boolean isTransparentSupported, boolean isGroupIdChangeSupported) {
    this.undoProject = undoProject;
    this.isTransparentSupported = isTransparentSupported;
    this.isGroupIdChangeSupported = isGroupIdChangeSupported;
    reset();
  }

  boolean isInsideCommand() {
    return isInsideCommand;
  }

  boolean isActive() {
    return isInsideCommand() && cmdEvent.project() == undoProject;
  }

  boolean hasActions() {
    assertInsideCommand();
    return !undoableActions.isEmpty();
  }

  void commandStarted(@NotNull CmdEvent cmdEvent, @NotNull CurrentEditorProvider editorProvider) {
    assertOutsideCommand(cmdEvent);
    if (LOG.isTraceEnabled() || ApplicationManager.getApplication().isUnitTestMode()) {
      this.tracedStartCommand = new Throwable();
    }
    this.cmdEvent = cmdEvent;
    this.editorProvider = editorProvider;
    this.editorStateBefore = currentEditorState();
    this.originalDocument = this.cmdEvent.recordOriginalDocument() ? originalDocument() : null;
    this.isInsideCommand = true;
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

  @NotNull PerformedCommand commandFinished(@NotNull CmdEvent cmdEvent) {
    assertInsideCommand(cmdEvent);
    if (isGroupIdChangeSupported) {
      this.cmdEvent = cmdEvent;
    }
    this.editorStateAfter = currentEditorState();
    if (originalDocument != null && hasActions() && !isTransparent() && affectedDocuments.affectsOnlyPhysical()) {
      addDocumentAsAffected(Objects.requireNonNull(originalDocument));
    }
    return buildAndReset();
  }

  void assertOutsideCommand() {
    assertOutsideCommand(null);
  }

  private void assertInsideCommand() {
    assertInsideCommand(null);
  }

  private void assertOutsideCommand(@Nullable CmdEvent cmdEvent) {
    if (isInsideCommand) {
      String startEvent = cmdEvent == null ? "" : (", startEvent: " + cmdEvent);
      throw new UndoIllegalStateException(
        "Nested command detected, please report the stacktrace" + startEvent, tracedStartCommand
      );
    }
  }

  private void assertInsideCommand(@Nullable CmdEvent cmdEvent) {
    if (!isInsideCommand) {
      String finishEvent = cmdEvent == null ? "" : (", finishEvent: " + cmdEvent);
      throw new UndoIllegalStateException("Must be called inside a command" + finishEvent);
    }
  }

  private @NotNull PerformedCommand buildAndReset() {
    PerformedCommand performedCommand = new PerformedCommand(
      cmdEvent.id(),
      cmdEvent.name(),
      cmdEvent.groupId(),
      cmdEvent.confirmationPolicy(),
      editorStateBefore,
      editorStateAfter,
      undoableActions,
      affectedDocuments,
      additionalAffectedDocuments,
      isTransparent(),
      isForcedGlobal,
      isGlobal(),
      isValid
    );
    reset();
    return performedCommand;
  }

  private @Nullable EditorAndState currentEditorState() {
    try {
      return EditorAndState.getStateFor(undoProject, editorProvider);
    } catch (Throwable ex) {
      LOG.error("Failed to retrieve editor state", ex);
      return null;
    }
  }

  private @Nullable DocumentReference originalDocument() {
    if (undoProject != null && undoProject == cmdEvent.project()) {
      // note: originatorReference depends on FocusedComponent :sad_trombone_for_rd:, see IJPL-192250
      return UndoDocumentUtil.getDocReference(undoProject, editorProvider);
    }
    return null;
  }

  private boolean isTransparent() {
    if (isTransparentSupported) {
      return cmdEvent.isTransparent();
    }
    return cmdEvent.isTransparent() && !hasActions();
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

  private boolean isGlobal() {
    return isForcedGlobal || affectedDocuments.affectsMultiplePhysical();
  }

  private void reset() {
    this.cmdEvent = NoEvent.INSTANCE;
    this.tracedStartCommand = null;
    this.editorProvider = NoEditorProvider.INSTANCE;
    this.editorStateBefore = null;
    this.editorStateAfter = null;
    this.originalDocument = null;
    this.undoableActions = new ArrayList<>(2);
    this.affectedDocuments = new UndoAffectedDocuments();
    this.additionalAffectedDocuments = new UndoAffectedDocuments();
    this.isForcedGlobal = false;
    this.isValid = true;
    this.isInsideCommand = false;
  }

  private static boolean isRefresh() {
    return ExternalChangeActionUtil.isExternalChangeInProgress();
  }

  private static final class NoEvent implements CmdEvent {
    static final NoEvent INSTANCE = new NoEvent();

    // region Unsupported

    @Override
    public @NotNull CommandId id() { throw new UnsupportedOperationException(); }

    @Override
    public @Nullable Project project() { throw new UnsupportedOperationException(); }

    @Override
    public @Nullable String name() { throw new UnsupportedOperationException(); }

    @Override
    public @Nullable Object groupId() { throw new UnsupportedOperationException(); }

    @Override
    public @NotNull UndoConfirmationPolicy confirmationPolicy() { throw new UnsupportedOperationException(); }

    @Override
    public boolean recordOriginalDocument() { throw new UnsupportedOperationException(); }

    @Override
    public boolean isTransparent() { throw new UnsupportedOperationException(); }

    // endregion
  }

  private static final class NoEditorProvider implements CurrentEditorProvider {
    static final CurrentEditorProvider INSTANCE = new NoEditorProvider();

    // region Unsupported

    @Override
    public @Nullable FileEditor getCurrentEditor(@Nullable Project project) { throw new UnsupportedOperationException(); }

    // endregion
  }
}
