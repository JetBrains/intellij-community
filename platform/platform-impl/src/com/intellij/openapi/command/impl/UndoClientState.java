// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.client.ClientAppSession;
import com.intellij.openapi.client.ClientProjectSession;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.Command;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


final class UndoClientState implements Disposable {

  private static final Logger LOG = Logger.getInstance(UndoClientState.class);

  private static final int COMMANDS_TO_KEEP_LIVE_QUEUES = 100;
  private static final int COMMAND_TO_RUN_COMPACT = 20;
  private static final int FREE_QUEUES_LIMIT = 30;

  private final @Nullable Project project; // null - global, isDefault - error
  private final @NotNull ClientId clientId;
  private final @NotNull CommandMerger commandMerger;
  private final @NotNull CommandBuilder commandBuilder;
  private final @NotNull UndoRedoStacksHolder undoStacksHolder;
  private final @NotNull UndoRedoStacksHolder redoStacksHolder;

  private final boolean isConfirmationSupported;
  private final boolean isCompactSupported;
  private final boolean isGlobalSplitSupported;
  private final boolean isEditorStateRestoreSupported;

  private final @NotNull UndoSharedState sharedState;

  private @NotNull UndoRedoInProgress undoRedoInProgress = UndoRedoInProgress.NONE;
  private int commandTimestamp = 1;

  @SuppressWarnings("unused")
  UndoClientState(@NotNull ClientProjectSession session) {
    this((UndoManagerImpl) UndoManager.getInstance(session.getProject()), session.getClientId());
  }

  @SuppressWarnings("unused")
  UndoClientState(@NotNull ClientAppSession session) {
    this((UndoManagerImpl) UndoManager.getGlobalInstance(), session.getClientId());
  }

  private UndoClientState(@NotNull UndoManagerImpl undoManager, @NotNull ClientId clientId) {
    this.clientId = clientId;
    this.project = undoManager.getProject();
    this.isConfirmationSupported = undoManager.isConfirmationSupported();
    this.isCompactSupported = undoManager.isCompactSupported();
    this.isGlobalSplitSupported = undoManager.isGlobalSplitSupported();
    this.isEditorStateRestoreSupported = undoManager.isEditorStateRestoreSupported();
    this.sharedState = undoManager.getUndoSharedState();
    this.undoStacksHolder = new UndoRedoStacksHolder(sharedState.getAdjustableActions(), true);
    this.redoStacksHolder = new UndoRedoStacksHolder(sharedState.getAdjustableActions(), false);
    this.commandMerger = new CommandMerger(project != null, undoManager.isTransparentSupported());
    this.commandBuilder = new CommandBuilder(project, undoManager.isTransparentSupported(), undoManager.isGroupIdChangeSupported());
  }

  @Override
  public void dispose() {
    Set<DocumentReference> affected = clearStacks();
    sharedState.trimStacks(affected);
  }

  boolean isActiveForCurrentProject() {
    return commandBuilder.isActive();
  }

  boolean isUndoRedoAvailable(@Nullable FileEditor editor, boolean isUndo) {
    Collection<DocumentReference> refs = UndoDocumentUtil.getDocRefs(editor);
    return refs != null && isUndoRedoAvailable(refs, isUndo);
  }

  boolean isUndoRedoAvailable(@NotNull Collection<DocumentReference> docRefs, boolean isUndo) {
    if (isUndo && commandMerger.isUndoAvailable(docRefs)) {
      return true;
    }
    UndoRedoStacksHolder stacksHolder = isUndo ? undoStacksHolder : redoStacksHolder;
    return stacksHolder.canBeUndoneOrRedone(docRefs);
  }

  void undoOrRedo(
    @Nullable FileEditor editor,
    @NotNull @Command String commandName,
    @NotNull Runnable beforeUndoRedoStarted,
    boolean undo
  ) {
    if (isUndoOrRedoInProgress()) {
      throw new UndoIllegalStateException("Undo/redo operation is already in progress");
    }
    undoRedoInProgress = undo ? UndoRedoInProgress.UNDO : UndoRedoInProgress.REDO;
    try {
      var exception = new AtomicReference<RuntimeException>();
      CommandProcessor.getInstance().executeCommand(
        project,
        () -> {
          try {
            beforeUndoRedoStarted.run();
            CopyPasteManager.getInstance().stopKillRings();
            undoOrRedo(editor, undo);
          }
          catch (RuntimeException ex) {
            exception.set(ex);
          }
        },
        commandName,
        null,
        commandMerger.getUndoConfirmationPolicy()
      );
      if (exception.get() != null) {
        throw exception.get();
      }
    }
    finally {
      undoRedoInProgress = UndoRedoInProgress.NONE;
    }
  }

  long getNextNanoTime(@NotNull FileEditor editor, boolean isUndo) {
    UndoableGroup lastAction = getLastAction(editor, isUndo);
    return lastAction == null ? -1 : lastAction.getGroupStartPerformedTimestamp();
  }

  boolean isNextAskConfirmation(@NotNull FileEditor editor, boolean isUndo) {
    UndoableGroup lastAction = getLastAction(editor, isUndo);
    return lastAction != null && lastAction.shouldAskConfirmation( /*redo=*/ !isUndo);
  }

  @Nullable String getLastCommandName(@Nullable FileEditor editor, boolean isUndo) {
    Collection<DocumentReference> refs = UndoDocumentUtil.getDocRefs(editor);
    if (refs == null) {
      return null;
    }
    if (isUndo && commandMerger.isUndoAvailable(refs)) {
      return commandMerger.getCommandName();
    }
    UndoRedoStacksHolder stack = isUndo ? undoStacksHolder : redoStacksHolder;
    UndoableGroup lastAction = stack.getLastAction(refs);
    return lastAction == null ? null : lastAction.getCommandName();
  }

  void commandStarted(@NotNull CmdEvent cmdEvent, @NotNull CurrentEditorProvider editorProvider) {
    commandBuilder.commandStarted(cmdEvent, editorProvider);
  }

  void commandFinished(@NotNull CmdEvent cmdEvent) {
    PerformedCommand performedCommand = commandBuilder.commandFinished(cmdEvent);
    commitCommand(performedCommand);
    notifyUndoSpy(performedCommand);
  }

  private void commitCommand(@NotNull PerformedCommand performedCommand) {
    if (performedCommand.shouldClearRedoStack()) {
      redoStacksHolder.clearStacks(performedCommand.affectedDocuments().asCollection(), performedCommand.isGlobal());
    }
    UndoCommandFlushReason flushReason = commandMerger.shouldFlush(performedCommand);
    if (flushReason != null) {
      flushCommandMerger(flushReason, performedCommand);
      compactIfNeeded();
    }
    commandMerger.mergeWithPerformedCommand(performedCommand);
  }

  private void notifyUndoSpy(@NotNull PerformedCommand performedCommand) {
    for (UndoableAction action : performedCommand.undoableActions()) {
      sharedState.addAction(action);
      UndoSpy undoSpy = UndoSpy.getInstance();
      if (undoSpy != null) {
        undoSpy.undoableActionAdded(project, action, UndoableActionType.forAction(action));
      }
    }
  }

  void flushCommandMerger(@NotNull UndoCommandFlushReason flushReason) {
    flushCommandMerger(flushReason, null);
  }

  boolean isInsideCommand() {
    return commandBuilder.isInsideCommand();
  }

  void markCurrentCommandAsGlobal() {
    commandBuilder.markAsGlobal();
  }

  void addAffectedDocuments(Document @NotNull ... docs) {
    commandBuilder.addAffectedDocuments(docs);
  }

  void addAffectedFiles(VirtualFile @NotNull ... files) {
    commandBuilder.addAffectedFiles(files);
  }

  void addUndoableAction(@NotNull CurrentEditorProvider editorProvider, @NotNull UndoableAction action) {
    if (isUndoOrRedoInProgress()) {
      return;
    }
    action.setPerformedNanoTime(System.nanoTime());
    if (isInsideCommand()) {
      commandBuilder.addUndoableAction(action);
    } else {
      LOG.assertTrue(
        action instanceof NonUndoableAction,
        "Undoable actions allowed inside commands only (see com.intellij.openapi.command.CommandProcessor.executeCommand())"
      );
      CmdEvent cmdEvent = CmdEvent.create(
        CommandIdService.currCommandId(),
        null,
        "",
        null,
        UndoConfirmationPolicy.DEFAULT,
        false,
        false
      );
      commandStarted(cmdEvent, editorProvider);
      try {
        commandBuilder.addUndoableAction(action);
      } finally {
        commandFinished(cmdEvent);
      }
    }
  }

  void addDocumentAsAffected(@NotNull DocumentReference docRef) {
    commandBuilder.addDocumentAsAffected(docRef);
  }

  void invalidateActions(@NotNull DocumentReference ref) {
    if (isInsideCommand()) {
      commandBuilder.invalidateIfAffects(ref);
    }
    commandMerger.invalidateActionsFor(ref);
    undoStacksHolder.invalidateActionsFor(ref);
    redoStacksHolder.invalidateActionsFor(ref);
  }

  boolean isUndoInProgress() {
    return undoRedoInProgress == UndoRedoInProgress.UNDO;
  }

  boolean isRedoInProgress() {
    return undoRedoInProgress == UndoRedoInProgress.REDO;
  }

  void clearUndoRedoQueue(@NotNull DocumentReference docRef) {
    commandBuilder.assertOutsideCommand();
    flushCommandMerger(UndoCommandFlushReason.CLEAR_QUEUE);
    undoStacksHolder.clearStacks(Collections.singleton(docRef), false);
    redoStacksHolder.clearStacks(Collections.singleton(docRef), false);
  }

  void clearDocumentReferences(@NotNull Document document) {
    undoStacksHolder.clearDocumentReferences(document);
    redoStacksHolder.clearDocumentReferences(document);
    commandMerger.clearDocumentReferences(document);
  }

  @Nullable PerClientLocalUndoRedoSnapshot getUndoRedoSnapshotForDocument(@NotNull DocumentReference reference) {
    if (isInsideCommand() && commandBuilder.hasActions()) {
      return null;
    }
    LocalCommandMergerSnapshot mergerSnapshot = commandMerger.getSnapshot(reference);
    if (mergerSnapshot == null) {
      return null;
    }
    return new PerClientLocalUndoRedoSnapshot(
      mergerSnapshot,
      undoStacksHolder.getStack(reference).snapshot(),
      redoStacksHolder.getStack(reference).snapshot()
    );
  }

  boolean resetLocalHistory(DocumentReference reference, PerClientLocalUndoRedoSnapshot snapshot) {
    if (isInsideCommand() && commandBuilder.hasActions()) {
      return false;
    }
    if (!commandMerger.resetLocalHistory(snapshot.getLocalCommandMergerSnapshot())) {
      return false;
    }
    undoStacksHolder.getStack(reference).resetTo(snapshot.getUndoStackSnapshot());
    redoStacksHolder.getStack(reference).resetTo(snapshot.getRedoStackSnapshot());
    return true;
  }

  @NotNull ClientId getClientId() {
    return clientId;
  }

  int getStackSize(@Nullable DocumentReference docRef, boolean isUndo) {
    UndoRedoStacksHolder stacks = isUndo ? undoStacksHolder : redoStacksHolder;
    return stacks.getStackSize(docRef);
  }

  void clearStacks(@Nullable FileEditor editor) {
    var refs = UndoDocumentUtil.getDocRefs(editor);
    if (refs != null) {
      flushCommandMerger(UndoCommandFlushReason.CLEAR_STACKS);
      redoStacksHolder.clearStacks(new HashSet<>(refs), true);
      undoStacksHolder.clearStacks(new HashSet<>(refs), true);
      sharedState.trimStacks(refs);
    }
  }

  @NotNull String dump(@Nullable FileEditor editor) {
    //String currentMerger = currentCommandMerger == null ? "" : currentCommandMerger.dumpState();
    String currentMerger = "";
    String merger = commandMerger.dumpState();
    var refs = UndoDocumentUtil.getDocRefs(editor);
    var forEditor = refs == null ? null : new HashSet<>(refs);
    var docRefs = new LinkedHashSet<DocumentReference>();
    if (forEditor != null) {
      docRefs.addAll(forEditor);
      docRefs.addAll(commandMerger.getAffectedDocuments());
      docRefs.addAll(commandMerger.getAdditionalAffectedDocuments());
      docRefs.addAll(undoStacksHolder.getAffectedDocuments(forEditor));
      docRefs.addAll(redoStacksHolder.getAffectedDocuments(forEditor));
    } else {
      undoStacksHolder.collectAllAffectedDocuments(docRefs);
      redoStacksHolder.collectAllAffectedDocuments(docRefs);
    }
    String stacks = docRefs.stream()
      .map(docRef -> dump(docRef, forEditor))
      .collect(Collectors.joining("\n"));
    String globalStack = dump(null, forEditor);
    // TODO: support dump
    //noinspection ConstantValue
    return """
      %s
      >>CurrentMerger %s
      >>Merger %s
      %s
      %s""".formatted(
        clientId,
        currentMerger.isEmpty() ? "null" : ("\n  " + currentMerger),
        merger.isEmpty() ? "null" : ("\n  " + merger + "\n"),
        stacks,
        globalStack
    );
  }

  @TestOnly
  void dropHistoryInTests() {
    commandBuilder.assertOutsideCommand();
    undoStacksHolder.clearAllStacksInTests();
    redoStacksHolder.clearAllStacksInTests();
  }

  private void flushCommandMerger(@NotNull UndoCommandFlushReason flushReason, @Nullable PerformedCommand performedCommand) {
    if (performedCommand != null && !performedCommand.hasActions() && commandMerger.hasActions() && !isUndoOrRedoInProgress()) {
      UndoSpy undoSpy = UndoSpy.getInstance();
      if (undoSpy != null) {
        undoSpy.commandMergerFlushed(project);
      }
    }
    UndoableGroup group = commandMerger.formGroup(flushReason, nextCommandTimestamp());
    if (group != null) {
      composeStartFinishGroup(group);
      undoStacksHolder.addToStacks(group);
    }
  }

  private void compactIfNeeded() {
    if (isCompactSupported && !isUndoOrRedoInProgress() && commandTimestamp % COMMAND_TO_RUN_COMPACT == 0) {
      Set<DocumentReference> docsOnStacks = collectReferencesWithoutMergers();
      docsOnStacks.removeIf(doc -> UndoDocumentUtil.isDocumentOpened(project, doc));
      if (docsOnStacks.size() > FREE_QUEUES_LIMIT) {
        DocumentReference[] docsBackSorted = docsOnStacks.toArray(DocumentReference.EMPTY_ARRAY);
        Arrays.sort(docsBackSorted, Comparator.comparingInt(this::getLastCommandTimestamp));
        for (int i = 0; i < docsBackSorted.length - FREE_QUEUES_LIMIT; i++) {
          DocumentReference doc = docsBackSorted[i];
          if (getLastCommandTimestamp(doc) + COMMANDS_TO_KEEP_LIVE_QUEUES > commandTimestamp) {
            break;
          }
          clearUndoRedoQueue(doc);
          sharedState.trimStacks(Collections.singleton(doc));
        }
      }
    }
  }

  private void undoOrRedo(@Nullable FileEditor editor, boolean isUndo) {
    flushCommandMerger(isUndo ? UndoCommandFlushReason.UNDO : UndoCommandFlushReason.REDO);

    // here we _undo_ (regardless 'isUndo' flag) and drop all 'transparent' actions made right after undoRedo/redo.
    // Such actions should not get into redo/undoRedo stacks.  Note that 'transparent' actions that have been merged with normal actions
    // are not dropped, since this means they did not occur after undo/redo
    UndoRedo undoRedo;
    while ((undoRedo = createUndoOrRedo(editor, true)) != null) {
      if (!undoRedo.isTemporary()) break;
      if (!undoRedo.execute(true, false)) return;
      if (!undoRedo.hasMoreActions()) break;
    }

    while ((undoRedo = createUndoOrRedo(editor, isUndo)) != null) {
      if (!undoRedo.isTransparent()) break;
      if (!undoRedo.execute(false, false)) return;
      if (!undoRedo.hasMoreActions()) break;
    }

    boolean isInsideStartFinishGroup = false;
    while ((undoRedo = createUndoOrRedo(editor, isUndo)) != null) {
      if (editor != null && undoRedo.isBlockedByOtherChanges()) {
        UndoRedo blockingChange = createUndoOrRedo(null, isUndo);
        if (blockingChange != null && !blockingChange.isSameUndoableGroup(undoRedo)) {
          if (undoRedo.confirmSwitchTo(blockingChange)) {
            blockingChange.execute(false, true);
          }
          break;
        }

        // if undo is block by other global command, trying to split global command and undo only local change in editor
        if (isUndo && undoRedo.isGlobal() && isGlobalSplitEnabled()) {
          if (undoRedo.splitGlobalCommand()) {
            var splittedUndo = createUndoOrRedo(editor, true);
            if (splittedUndo != null) {
              undoRedo = splittedUndo;
            }
          }
        }
      }
      boolean executed = undoRedo.execute(false, isInsideStartFinishGroup);
      if (!executed) {
        return;
      }

      if (editor != null && !isUndo && isGlobalSplitEnabled()){
        undoRedo.gatherGlobalCommand();
      }

      isInsideStartFinishGroup = undoRedo.isInsideStartFinishGroup(isInsideStartFinishGroup);
      if (isInsideStartFinishGroup) {
        continue;
      }
      boolean shouldRepeat = undoRedo.isTransparent() && undoRedo.hasMoreActions();
      if (!shouldRepeat) {
        break;
      }
    }
  }

  private @Nullable UndoRedo createUndoOrRedo(@Nullable FileEditor editor, boolean isUndo) {
    if (!isUndoRedoAvailable(editor, isUndo)) {
      return null;
    }
    return isUndo
           ? new Undo(project, editor, undoStacksHolder, redoStacksHolder, sharedState.getUndoStacks(), sharedState.getRedoStacks(), isConfirmationSupported, isEditorStateRestoreSupported)
           : new Redo(project, editor, undoStacksHolder, redoStacksHolder, sharedState.getUndoStacks(), sharedState.getRedoStacks(), isConfirmationSupported, isEditorStateRestoreSupported);
  }

  private boolean isGlobalSplitEnabled() {
    return isGlobalSplitSupported && Registry.is("ide.undo.fallback");
  }

  private @Nullable UndoableGroup getLastAction(@NotNull FileEditor editor, boolean isUndo) {
    Collection<DocumentReference> refs = UndoDocumentUtil.getDocRefs(editor);
    if (refs == null) {
      return null;
    }
    if (isUndo) {
      flushCommandMerger(UndoCommandFlushReason.GET_LAST_GROUP);
    }
    UndoRedoStacksHolder stack = isUndo ? undoStacksHolder : redoStacksHolder;
    return stack.getLastAction(refs);
  }

  private @NotNull Set<DocumentReference> collectReferencesWithoutMergers() {
    Set<DocumentReference> result = new HashSet<>();
    undoStacksHolder.collectAllAffectedDocuments(result);
    redoStacksHolder.collectAllAffectedDocuments(result);
    return result;
  }

  private @NotNull Set<DocumentReference> clearStacks() {
    var affected = new HashSet<DocumentReference>();
    flushCommandMerger(UndoCommandFlushReason.CLEAR_STACKS);
    redoStacksHolder.collectAllAffectedDocuments(affected);
    redoStacksHolder.clearStacks(affected, true);
    undoStacksHolder.collectAllAffectedDocuments(affected);
    undoStacksHolder.clearStacks(affected, true);
    return affected;
  }

  private boolean isUndoOrRedoInProgress() {
    return undoRedoInProgress != UndoRedoInProgress.NONE;
  }

  private int nextCommandTimestamp() {
    return ++commandTimestamp;
  }

  private int getLastCommandTimestamp(@NotNull DocumentReference doc) {
    return Math.max(
      undoStacksHolder.getLastCommandTimestamp(doc),
      redoStacksHolder.getLastCommandTimestamp(doc)
    );
  }

  private void composeStartFinishGroup(@NotNull UndoableGroup createdGroup) {
    FinishMarkAction finishMark = createdGroup.getFinishMark();
    if (finishMark != null) {
      boolean global = false;
      String commandName = null;
      UndoRedoList<UndoableGroup> stack = undoStacksHolder.getStack(finishMark.getAffectedDocument());
      Iterator<UndoableGroup> iterator = stack.descendingIterator();
      while (iterator.hasNext()) {
        UndoableGroup group = iterator.next();
        if (group.isGlobal()) {
          global = true;
          commandName = group.getCommandName();
          break;
        }
        if (group.getStartMark() != null) {
          break;
        }
      }
      if (global) {
        finishMark.setGlobal(true);
        finishMark.setCommandName(commandName);
      }
    }
  }

  private @NotNull String dump(@Nullable DocumentReference docRef, @Nullable Collection<DocumentReference> editorRefs) {
    String s = docRef == null ? "Global" : docRef.toString();
    String redo = redoStacksHolder.dump(docRef);
    String undo = undoStacksHolder.dump(docRef);
    String inEditor = docRef != null && editorRefs != null && editorRefs.contains(docRef) ? "inEditor" : "";
    return """
      >>%s %s
      %s
      %s
      """.formatted(s, inEditor, redo, undo);
  }

  private enum UndoRedoInProgress { NONE, UNDO, REDO }
}
