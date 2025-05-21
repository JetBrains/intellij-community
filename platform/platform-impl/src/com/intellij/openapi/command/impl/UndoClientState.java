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
import com.intellij.psi.ExternalChangeAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;


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
    this.clientId = clientId;
    this.adjustableUndoableActionsHolder = undoManager.getAdjustableUndoableActionsHolder();
    this.sharedUndoStacksHolder = undoManager.getSharedUndoStacksHolder();
    this.sharedRedoStacksHolder = undoManager.getSharedRedoStacksHolder();
    this.undoStacksHolder = new UndoRedoStacksHolder(true, adjustableUndoableActionsHolder);
    this.redoStacksHolder = new UndoRedoStacksHolder(false, adjustableUndoableActionsHolder);
    this.commandMerger = new CommandMerger(project, false);
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

  boolean isUndoRedoAvailable(@NotNull Collection<? extends DocumentReference> docRefs, boolean isUndo) {
    if (isUndo && commandMerger.isUndoAvailable(docRefs)) {
      return true;
    }
    UndoRedoStacksHolder stacksHolder = isUndo ? undoStacksHolder : redoStacksHolder;
    return stacksHolder.canBeUndoneOrRedone(docRefs);
  }

  void undoOrRedo(
    @Nullable FileEditor editor,
    @Nullable @Command String commandName,
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
    UndoableGroup lastAction = getLastAction(editor, isUndo, true);
    return lastAction == null ? -1 : lastAction.getGroupStartPerformedTimestamp();
  }

  boolean isNextAskConfirmation(@NotNull FileEditor editor, boolean isUndo) {
    UndoableGroup lastAction = getLastAction(editor, isUndo, true);
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
    UndoConfirmationPolicy undoConfirmationPolicy,
    boolean recordOriginalReference
  ) {
    if (!isInsideCommand()) {
      boolean isTransparent = CommandProcessor.getInstance().isUndoTransparentActionInProgress();
      currentCommandMerger = new CommandMerger(project, isTransparent);
      if (commandProject != null) {
        currentProject = commandProject;
      }
      if (project != null && project == commandProject && recordOriginalReference) {
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
    @Command String commandName,
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
    currentCommandMerger.setEditorStateAfter(EditorAndState.getStateFor(project, editorProvider));
    // we do not want to spoil redo stack in situation, when some 'transparent' actions occurred right after undo.
    if (!currentCommandMerger.isTransparent() && currentCommandMerger.hasActions()) {
      clearRedoStacks(currentCommandMerger);
    }
    if (!commandMerger.shouldMerge(groupId, currentCommandMerger)) {
      flushCurrentCommand();
      compactIfNeeded();
    }
    commandMerger.commandFinished(commandName, groupId, currentCommandMerger);
    currentProject = DummyProject.getInstance();
    this.currentCommandMerger = null;
  }

  void flushCurrentCommand() {
    commandMerger.flushCurrentCommand(undoStacksHolder, nextCommandTimestamp());
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

  boolean isSpeculativeUndoAllowed(@Nullable FileEditor editor, boolean isUndo) {
    if (isUndo && commandMerger.hasActions()) {
      return commandMerger.isSpeculativeUndoAllowed();
    }
    if (editor != null) {
      UndoableGroup action = getLastAction(editor, isUndo, false);
      return action != null && action.isSpeculativeUndoAllowed();
    }
    return false;
  }

  void clearUndoRedoQueue(@NotNull DocumentReference docRef) {
    LOG.assertTrue(!isInsideCommand());
    flushCurrentCommand();
    currentCommandMerger = null;
    undoStacksHolder.clearStacks(false, Set.of(docRef));
    redoStacksHolder.clearStacks(false, Set.of(docRef));
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

  @NotNull String dump(@NotNull Collection<DocumentReference> docRefs) {
    StringBuilder sb = new StringBuilder();
    sb.append(clientId);
    sb.append("\n");
    if (currentCommandMerger == null) {
      sb.append("null CurrentMerger\n");
    }
    else {
      sb.append("CurrentMerger\n  ");
      sb.append(currentCommandMerger.dumpState());
      sb.append("\n");
    }
    sb.append("Merger\n  ");
    sb.append(commandMerger.dumpState());
    sb.append("\n");
    for (DocumentReference doc : docRefs) {
      sb.append(dumpStack(doc, true));
      sb.append("\n");
      sb.append(dumpStack(doc, false));
    }
    return sb.toString();
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
    if (!isUndoOrRedoInProgress() && commandTimestamp % COMMAND_TO_RUN_COMPACT == 0) {
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
    flushCurrentCommand();

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
        if (isUndo && undoRedo.isGlobal() && Registry.is("ide.undo.fallback")) {
          if (undoRedo.splitGlobalCommand()) {
            var splittedUndo = createUndoOrRedo(editor, true);
            if (splittedUndo != null) {
              undoRedo = splittedUndo;
            }
          }
        }
      }
      if (!undoRedo.execute(false, isInsideStartFinishGroup)) {
        return;
      }

      if (editor != null && !isUndo && Registry.is("ide.undo.fallback")){
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
  }

  private void clearRedoStacks(@NotNull CommandMerger nextMerger) {
    redoStacksHolder.clearStacks(nextMerger.isGlobal(), nextMerger.getAllAffectedDocuments());
  }

  private @NotNull String dumpStack(@NotNull DocumentReference doc, boolean isUndo) {
    String name = isUndo ? "UndoStack" : "RedoStack";
    UndoRedoList<UndoableGroup> stack = isUndo ? undoStacksHolder.getStack(doc) : redoStacksHolder.getStack(doc);
    return name + " for " + doc.getDocument() + "\n" + dumpStack(stack);
  }

  private static @NotNull String dumpStack(@NotNull UndoRedoList<UndoableGroup> stack) {
    ArrayList<String> reversed = new ArrayList<>();
    Iterator<UndoableGroup> it = stack.descendingIterator();
    int i = 0;
    while (it.hasNext()) {
      reversed.add("  %s %s".formatted(i, it.next().dumpState0()));
      i++;
    }
    return String.join("\n", reversed);
  }

  private @Nullable UndoableGroup getLastAction(@NotNull FileEditor editor, boolean isUndo, boolean isFlush) {
    Collection<DocumentReference> refs = UndoDocumentUtil.getDocRefs(editor);
    if (refs == null) {
      return null;
    }
    if (isUndo && isFlush) {
      flushCurrentCommand();
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
    commandMerger.flushCurrentCommand(undoStacksHolder, nextCommandTimestamp());
    redoStacksHolder.collectAllAffectedDocuments(affected);
    redoStacksHolder.clearStacks(true, affected);
    undoStacksHolder.collectAllAffectedDocuments(affected);
    undoStacksHolder.clearStacks(true, affected);
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

  private static boolean isRefresh() {
    return ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction.class);
  }

  private static @NotNull UndoManagerImpl getUndoManager(@NotNull ComponentManager manager) {
    return (UndoManagerImpl) manager.getService(UndoManager.class);
  }

  private enum OperationInProgress { NONE, UNDO, REDO }
}
