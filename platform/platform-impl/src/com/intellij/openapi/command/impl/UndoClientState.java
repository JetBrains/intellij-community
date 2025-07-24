// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.client.ClientAppSession;
import com.intellij.openapi.client.ClientProjectSession;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts.Command;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ExternalChangeActionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.stream.Collectors;


final class UndoClientState implements Disposable {

  private static final Logger LOG = Logger.getInstance(UndoClientState.class);

  private static final int COMMANDS_TO_KEEP_LIVE_QUEUES = 100;
  private static final int COMMAND_TO_RUN_COMPACT = 20;
  private static final int FREE_QUEUES_LIMIT = 30;

  private final @Nullable Project project;
  private final @NotNull ClientId clientId;
  private final @NotNull CommandMerger commandMerger;
  private final @NotNull UndoRedoStacksHolder undoStacksHolder;
  private final @NotNull UndoRedoStacksHolder redoStacksHolder;

  private final @NotNull UndoSpy undoSpy;
  private final boolean isTransparentSupported;
  private final boolean isConfirmationSupported;
  private final boolean isCompactSupported;
  private final boolean isGlobalSplitSupported;

  // yet it is not a client state but shared one defined by undo manager
  private final @NotNull SharedAdjustableUndoableActionsHolder adjustableUndoableActionsHolder;
  private final @NotNull SharedUndoRedoStacksHolder sharedUndoStacksHolder;
  private final @NotNull SharedUndoRedoStacksHolder sharedRedoStacksHolder;

  private OperationInProgress currentOperation = OperationInProgress.NONE;
  private CommandMerger currentCommandMerger = null;
  private Project currentProject = DummyProject.getInstance();
  private DocumentReference originatorReference;
  private int commandTimestamp = 1;
  private int commandLevel = 0;

  @SuppressWarnings("unused")
  UndoClientState(@NotNull ClientProjectSession session) {
    this(getUndoManager(session.getProject()), session.getClientId());
  }

  @SuppressWarnings("unused")
  UndoClientState(@NotNull ClientAppSession session) {
    this(getUndoManager(ApplicationManager.getApplication()), session.getClientId());
  }

  private UndoClientState(@NotNull UndoManagerImpl undoManager, @NotNull ClientId clientId) {
    this.project = undoManager.getProject();
    this.undoSpy = undoManager.getUndoSpy();
    this.isTransparentSupported = undoManager.isTransparentSupported();
    this.isConfirmationSupported = undoManager.isConfirmationSupported();
    this.isCompactSupported = undoManager.isCompactSupported();
    this.isGlobalSplitSupported = undoManager.isGlobalSplitSupported();
    this.clientId = clientId;
    this.adjustableUndoableActionsHolder = undoManager.getAdjustableUndoableActionsHolder();
    this.sharedUndoStacksHolder = undoManager.getSharedUndoStacksHolder();
    this.sharedRedoStacksHolder = undoManager.getSharedRedoStacksHolder();
    this.undoStacksHolder = new UndoRedoStacksHolder(adjustableUndoableActionsHolder, true);
    this.redoStacksHolder = new UndoRedoStacksHolder(adjustableUndoableActionsHolder, false);
    this.commandMerger = new CommandMerger(project, false, isTransparentSupported);
  }

  @Override
  public void dispose() {
    Set<DocumentReference> affected = clearStacks();
    sharedRedoStacksHolder.trimStacks(affected);
    sharedUndoStacksHolder.trimStacks(affected);
  }

  boolean isActive() {
    return Comparing.equal(project, currentProject) ||
           project == null && currentProject.isDefault();
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
    currentOperation = undo ? OperationInProgress.UNDO : OperationInProgress.REDO;
    try {
      RuntimeException[] exception = new RuntimeException[1];
      CommandProcessor.getInstance().executeCommand(
        project,
        () -> {
          try {
            beforeUndoRedoStarted.run();
            CopyPasteManager.getInstance().stopKillRings();
            undoOrRedo(editor, undo);
          }
          catch (RuntimeException ex) {
            exception[0] = ex;
          }
        },
        commandName,
        null,
        commandMerger.getUndoConfirmationPolicy()
      );
      if (exception[0] != null) {
        throw exception[0];
      }
    }
    finally {
      currentOperation = OperationInProgress.NONE;
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

  void commandStarted(
    @Nullable Project commandProject,
    @NotNull CurrentEditorProvider editorProvider,
    @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
    boolean recordOriginalReference
  ) {
    undoSpy.commandStarted(commandProject, undoConfirmationPolicy);
    if (!isInsideCommand()) {
      boolean isTransparent = CommandProcessor.getInstance().isUndoTransparentActionInProgress();
      currentCommandMerger = new CommandMerger(project, isTransparent, isTransparentSupported);
      if (commandProject != null) {
        currentProject = commandProject;
      }
      if (project != null && project == commandProject && recordOriginalReference) {
        // note: originatorReference depends on FocusedComponent :sad_trombone_for_rd:, see IJPL-192250
        originatorReference = UndoDocumentUtil.getDocReference(project, editorProvider);
      }
    }
    currentCommandMerger.setEditorStateBefore(EditorAndState.getStateFor(project, editorProvider));
    currentCommandMerger.mergeUndoConfirmationPolicy(undoConfirmationPolicy);
    commandLevel++;
    LOG.assertTrue(commandProject == null || !(currentProject instanceof DummyProject));
  }

  void commandFinished(
    @NotNull CurrentEditorProvider editorProvider,
    @Nullable @Command String commandName,
    @Nullable Object groupId
  ) {
    if (!isInsideCommand()) {
      // possible if command listener was added within command
      return;
    }
    commandLevel--;
    if (isInsideCommand()) {
      return;
    }
    CommandMerger currentCommandMerger = this.currentCommandMerger; // compactIfNeeded can null the reference
    if (project != null &&
        currentCommandMerger.hasActions() &&
        !currentCommandMerger.isTransparent() &&
        currentCommandMerger.isPhysical() &&
        originatorReference != null) {
      addDocumentAsAffected(originatorReference);
    }
    originatorReference = null;
    // note: result of shouldFlush depends on FocusedComponent :sad_trombone_for_rd:
    EditorAndState editorStateAfter = EditorAndState.getStateFor(project, editorProvider);
    currentCommandMerger.setEditorStateAfter(editorStateAfter);
    // we do not want to spoil redo stack in situation, when some 'transparent' actions occurred right after undo.
    if (!currentCommandMerger.isTransparent() && currentCommandMerger.hasActions()) {
      clearRedoStacks(currentCommandMerger);
    }
    UndoCommandFlushReason flushReason = commandMerger.shouldFlush(groupId, currentCommandMerger);
    if (flushReason != null) {
      flushCurrentCommand(flushReason);
      compactIfNeeded();
    }
    commandMerger.commandFinished(commandName, groupId, currentCommandMerger);
    undoSpy.commandFinished(currentProject, commandName, groupId, currentCommandMerger.isTransparent());
    currentProject = DummyProject.getInstance();
    this.currentCommandMerger = null;
  }

  void flushCurrentCommand(@NotNull UndoCommandFlushReason flushReason) {
    if (currentCommandMerger != null && !currentCommandMerger.hasActions() && commandMerger.hasActions() && !isUndoOrRedoInProgress()) {
      undoSpy.commandMergerFlushed(project);
    }
    commandMerger.flushCurrentCommand(undoStacksHolder, flushReason, nextCommandTimestamp());
  }

  boolean isInsideCommand() {
    return commandLevel > 0;
  }

  void markCurrentCommandAsGlobal() {
    if (!isInsideCommand()) {
      LOG.error("Must be called inside command");
      return;
    }
    currentCommandMerger.markAsGlobal();
  }

  void addAffectedDocuments(Document @NotNull ... docs) {
    if (!isInsideCommand()) {
      LOG.error("Must be called inside command");
      return;
    }
    var refs = Arrays.stream(docs)
      .filter(doc -> {
        // is document's file still valid
        var file = FileDocumentManager.getInstance().getFile(doc);
        return file == null || file.isValid();
      })
      .map(doc -> DocumentReferenceManager.getInstance().create(doc))
      .toList();
    currentCommandMerger.addAdditionalAffectedDocuments(refs);
  }

  void addAffectedFiles(VirtualFile @NotNull ... files) {
    if (!isInsideCommand()) {
      LOG.error("Must be called inside command");
      return;
    }
    var refs = Arrays.stream(files)
      .map(doc -> DocumentReferenceManager.getInstance().create(doc))
      .toList();
    currentCommandMerger.addAdditionalAffectedDocuments(refs);
  }

  void addUndoableAction(@NotNull CurrentEditorProvider editorProvider, @NotNull UndoableAction action) {
    if (isUndoOrRedoInProgress()) {
      return;
    }
    action.setPerformedNanoTime(System.nanoTime());
    if (isInsideCommand()) {
      if (isRefresh()) {
        originatorReference = null;
      }
      addUndoableAction(action);
    } else {
      LOG.assertTrue(
        action instanceof NonUndoableAction,
        "Undoable actions allowed inside commands only (see com.intellij.openapi.command.CommandProcessor.executeCommand())"
      );
      commandStarted(null, editorProvider, UndoConfirmationPolicy.DEFAULT, false);
      addUndoableAction(action);
      commandFinished(editorProvider, "", null);
    }
  }

  void addDocumentAsAffected(@NotNull DocumentReference docRef) {
    if (currentCommandMerger != null && !currentCommandMerger.hasChangesOf(docRef, true)) {
      DocumentReference[] refs = {docRef};
      addUndoableAction(new MentionOnlyUndoableAction(refs));
    }
  }

  void invalidateActions(@NotNull DocumentReference ref) {
    commandMerger.invalidateActionsFor(ref);
    if (currentCommandMerger != null) {
      currentCommandMerger.invalidateActionsFor(ref);
    }
    undoStacksHolder.invalidateActionsFor(ref);
    redoStacksHolder.invalidateActionsFor(ref);
  }

  int nextCommandTimestamp() {
    return ++commandTimestamp;
  }

  boolean isUndoInProgress() {
    return currentOperation == OperationInProgress.UNDO;
  }

  boolean isRedoInProgress() {
    return currentOperation == OperationInProgress.REDO;
  }

  void clearUndoRedoQueue(@NotNull DocumentReference docRef) {
    LOG.assertTrue(!isInsideCommand());
    flushCurrentCommand(UndoCommandFlushReason.CLEAR_QUEUE);
    currentCommandMerger = null;
    undoStacksHolder.clearStacks(Collections.singleton(docRef), false);
    redoStacksHolder.clearStacks(Collections.singleton(docRef), false);
  }

  void clearDocumentReferences(@NotNull Document document) {
    undoStacksHolder.clearDocumentReferences(document);
    redoStacksHolder.clearDocumentReferences(document);
    commandMerger.clearDocumentReferences(document);
  }

  @Nullable PerClientLocalUndoRedoSnapshot getUndoRedoSnapshotForDocument(
    @NotNull DocumentReference reference,
    @NotNull SharedAdjustableUndoableActionsHolder adjustableUndoableActionsHolder
  ) {
    CommandMerger currentMerger = currentCommandMerger;
    if (currentMerger != null && currentMerger.hasActions()) {
      return null;
    }
    LocalCommandMergerSnapshot mergerSnapshot = commandMerger.getSnapshot(reference);
    if (mergerSnapshot == null) {
      return null;
    }
    return new PerClientLocalUndoRedoSnapshot(
      mergerSnapshot,
      undoStacksHolder.getStack(reference).snapshot(),
      redoStacksHolder.getStack(reference).snapshot(),
      adjustableUndoableActionsHolder.getStack(reference).snapshot()
    );
  }

  boolean resetLocalHistory(DocumentReference reference, PerClientLocalUndoRedoSnapshot snapshot) {
    CommandMerger currentMerger = currentCommandMerger;
    if (currentMerger != null && currentMerger.hasActions()) {
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
      flushCurrentCommand(UndoCommandFlushReason.CLEAR_STACKS);
      redoStacksHolder.clearStacks(new HashSet<>(refs), true);
      undoStacksHolder.clearStacks(new HashSet<>(refs), true);
      sharedRedoStacksHolder.trimStacks(refs);
      sharedUndoStacksHolder.trimStacks(refs);
    }
  }

  @NotNull String dump(@Nullable FileEditor editor) {
    String currentMerger = currentCommandMerger == null ? "" : currentCommandMerger.dumpState();
    String merger = commandMerger.dumpState();
    var refs = UndoDocumentUtil.getDocRefs(editor);
    var forEditor = refs == null ? null : new HashSet<>(refs);
    var docRefs = new LinkedHashSet<DocumentReference>();
    if (forEditor != null) {
      docRefs.addAll(forEditor);
      docRefs.addAll(commandMerger.getAllAffectedDocuments());
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
    undoStacksHolder.clearAllStacksInTests();
    redoStacksHolder.clearAllStacksInTests();

    int commandLevelBeforeDrop = commandLevel;
    commandLevel = 0;
    LOG.assertTrue(
      commandLevelBeforeDrop == 0,
      "Level: " + commandLevelBeforeDrop + "\nCommand: " + commandMerger.getCommandName()
    );
  }

  private void addActionToSharedStack(@NotNull UndoableAction action) {
    if (action instanceof AdjustableUndoableAction adjustable) {
      DocumentReference[] affected = action.getAffectedDocuments();
      if (affected == null) {
        return;
      }
      adjustableUndoableActionsHolder.addAction(adjustable);
      for (DocumentReference reference : affected) {
        for (MutableActionChangeRange changeRange : adjustable.getChangeRanges(reference)) {
          sharedUndoStacksHolder.addToStack(reference, changeRange.toImmutable(false));
          sharedRedoStacksHolder.addToStack(reference, changeRange.toImmutable(true));
        }
      }
    }
  }

  private void compactIfNeeded() {
    if (isCompactSupported && !isUndoOrRedoInProgress() && commandTimestamp % COMMAND_TO_RUN_COMPACT == 0) {
      Set<DocumentReference> docsOnStacks = collectReferencesWithoutMergers();
      docsOnStacks.removeIf(doc -> UndoDocumentUtil.isDocumentOpened(project, doc));
      if (docsOnStacks.size() > FREE_QUEUES_LIMIT) {
        DocumentReference[] docsBackSorted = docsOnStacks.toArray(DocumentReference.EMPTY_ARRAY);
        Arrays.sort(docsBackSorted, Comparator.comparingInt(doc -> getLastCommandTimestamp(doc)));
        for (int i = 0; i < docsBackSorted.length - FREE_QUEUES_LIMIT; i++) {
          DocumentReference doc = docsBackSorted[i];
          if (getLastCommandTimestamp(doc) + COMMANDS_TO_KEEP_LIVE_QUEUES > commandTimestamp) {
            break;
          }
          clearUndoRedoQueue(doc);
          sharedRedoStacksHolder.trimStacks(Collections.singleton(doc));
          sharedUndoStacksHolder.trimStacks(Collections.singleton(doc));
        }
      }
    }
  }

  private void undoOrRedo(@Nullable FileEditor editor, boolean isUndo) {
    flushCurrentCommand(isUndo ? UndoCommandFlushReason.UNDO : UndoCommandFlushReason.REDO);

    // here we _undo_ (regardless 'isUndo' flag) and drop all 'transparent' actions made right after undoRedo/redo.
    // Such actions should not get into redo/undoRedo stacks.  Note that 'transparent' actions that have been merged with normal actions
    // are not dropped, since this means they did not occur after undo/redo
    UndoRedo undoRedo;
    while ((undoRedo = createUndoOrRedo(editor, true)) != null) {
      if (!undoRedo.isTemporary()) break;
      if (!undoRedo.execute(true, !isConfirmationSupported)) return;
      if (!undoRedo.hasMoreActions()) break;
    }

    while ((undoRedo = createUndoOrRedo(editor, isUndo)) != null) {
      if (!undoRedo.isTransparent()) break;
      if (!undoRedo.execute(false, !isConfirmationSupported)) return;
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
      if (!undoRedo.execute(false, !isConfirmationSupported || isInsideStartFinishGroup)) {
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
           ? new Undo(project, editor, undoStacksHolder, redoStacksHolder, sharedUndoStacksHolder, sharedRedoStacksHolder)
           : new Redo(project, editor, undoStacksHolder, redoStacksHolder, sharedUndoStacksHolder, sharedRedoStacksHolder);
  }

  private void addUndoableAction(@NotNull UndoableAction action) {
    addActionToSharedStack(action);
    currentCommandMerger.addAction(action);
    if (!(currentProject instanceof DummyProject)) {
      undoSpy.undoableActionAdded(currentProject, action, UndoableActionType.forAction(action));
    }
  }

  private void clearRedoStacks(@NotNull CommandMerger nextMerger) {
    redoStacksHolder.clearStacks(nextMerger.getAllAffectedDocuments(), nextMerger.isGlobal());
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
      flushCurrentCommand(UndoCommandFlushReason.GET_LAST_GROUP);
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
    flushCurrentCommand(UndoCommandFlushReason.CLEAR_STACKS);
    redoStacksHolder.collectAllAffectedDocuments(affected);
    redoStacksHolder.clearStacks(affected, true);
    undoStacksHolder.collectAllAffectedDocuments(affected);
    undoStacksHolder.clearStacks(affected, true);
    return affected;
  }

  private boolean isUndoOrRedoInProgress() {
    return currentOperation != OperationInProgress.NONE;
  }

  private int getLastCommandTimestamp(@NotNull DocumentReference doc) {
    return Math.max(
      undoStacksHolder.getLastCommandTimestamp(doc),
      redoStacksHolder.getLastCommandTimestamp(doc)
    );
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

  private static boolean isRefresh() {
    return ExternalChangeActionUtil.isExternalChangeInProgress();
  }

  private static @NotNull UndoManagerImpl getUndoManager(@NotNull ComponentManager manager) {
    return (UndoManagerImpl) manager.getService(UndoManager.class);
  }

  private enum OperationInProgress { NONE, UNDO, REDO }
}
