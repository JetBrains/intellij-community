// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.client.ClientAppSession;
import com.intellij.openapi.client.ClientProjectSession;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
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

  private final UndoManagerImpl undoManager;
  private final ClientId clientId;
  private final CommandMerger commandMerger;
  private final UndoRedoStacksHolder undoStacksHolder;
  private final UndoRedoStacksHolder redoStacksHolder;

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
    this.undoManager = undoManager;
    this.clientId = clientId;
    this.commandMerger = new CommandMerger(this);
    this.undoStacksHolder = new UndoRedoStacksHolder(true, undoManager.getAdjustableUndoableActionsHolder());
    this.redoStacksHolder = new UndoRedoStacksHolder(false, undoManager.getAdjustableUndoableActionsHolder());
  }

  @Override
  public void dispose() {
    Set<DocumentReference> affected = clearStacks();
    undoManager.trimSharedStacks(affected);
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
    @Nullable Project project,
    @NotNull CurrentEditorProvider editorProvider,
    UndoConfirmationPolicy undoConfirmationPolicy,
    boolean recordOriginalReference
  ) {
    if (!isInsideCommand()) {
      boolean isTransparent = CommandProcessor.getInstance().isUndoTransparentActionInProgress();
      currentCommandMerger = new CommandMerger(this, isTransparent);
      if (project != null && recordOriginalReference) {
        originatorReference = UndoDocumentUtil.getDocReference(project, editorProvider);
      }
    }
    LOG.assertTrue(currentCommandMerger != null);
    currentCommandMerger.setBeforeState(EditorAndState.getStateFor(project, editorProvider));
    currentCommandMerger.mergeUndoConfirmationPolicy(undoConfirmationPolicy);
    commandLevel++;
  }

  void commandFinished(
    @Nullable Project project,
    @NotNull CurrentEditorProvider editorProvider,
    @NlsContexts.Command String commandName,
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
    if (project != null &&
        currentCommandMerger.hasActions() &&
        !currentCommandMerger.isTransparent() &&
        currentCommandMerger.isPhysical() &&
        originatorReference != null) {
      addDocumentAsAffected(originatorReference);
    }
    originatorReference = null;
    currentCommandMerger.setAfterState(EditorAndState.getStateFor(project, editorProvider));
    commandMerger.commandFinished(commandName, groupId, currentCommandMerger);
    resetCurrentCommandMerger();
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

  void addUndoableAction(
    @Nullable Project project,
    @NotNull CurrentEditorProvider editorProvider,
    @NotNull UndoableAction action
  ) {
    if (isUndoOrRedoInProgress()) {
      return;
    }
    action.setPerformedNanoTime(System.nanoTime());
    if (isInsideCommand()) {
      if (isRefresh()) {
        originatorReference = null;
      }
      currentCommandMerger.addAction(action);
    } else {
      LOG.assertTrue(
        action instanceof NonUndoableAction,
        "Undoable actions allowed inside commands only (see com.intellij.openapi.command.CommandProcessor.executeCommand())"
      );
      commandStarted(project, editorProvider, UndoConfirmationPolicy.DEFAULT, false);
      currentCommandMerger.addAction(action);
      commandFinished(project, editorProvider, "", null);
    }
  }

  void addDocumentAsAffected(@NotNull DocumentReference docRef) {
    if (currentCommandMerger != null && !currentCommandMerger.hasChangesOf(docRef, true)) {
      DocumentReference[] refs = {docRef};
      currentCommandMerger.addAction(new MentionOnlyUndoableAction(refs));
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

  void startUndoOrRedo(boolean undo) {
    currentOperation = undo ? OperationInProgress.UNDO : OperationInProgress.REDO;
  }

  void finishUndoOrRedo() {
    currentOperation = OperationInProgress.NONE;
  }

  boolean isUndoInProgress() {
    return currentOperation == OperationInProgress.UNDO;
  }

  boolean isRedoInProgress() {
    return currentOperation == OperationInProgress.REDO;
  }

  void compactIfNeeded() {
    if (!isUndoOrRedoInProgress() && commandTimestamp % COMMAND_TO_RUN_COMPACT == 0) {
      Set<DocumentReference> docsOnStacks = collectReferencesWithoutMergers();
      docsOnStacks.removeIf(doc -> UndoDocumentUtil.isDocumentOpened(undoManager.getProject(), doc));
      if (docsOnStacks.size() > FREE_QUEUES_LIMIT) {
        DocumentReference[] docsBackSorted = docsOnStacks.toArray(DocumentReference.EMPTY_ARRAY);
        Arrays.sort(docsBackSorted, Comparator.comparingInt(doc -> getLastCommandTimestamp(doc)));
        for (int i = 0; i < docsBackSorted.length - FREE_QUEUES_LIMIT; i++) {
          DocumentReference doc = docsBackSorted[i];
          if (getLastCommandTimestamp(doc) + COMMANDS_TO_KEEP_LIVE_QUEUES > commandTimestamp) {
            break;
          }
          clearUndoRedoQueue(doc);
          undoManager.trimSharedStacks(doc);
        }
      }
    }
  }

  void clearUndoRedoQueue(@NotNull DocumentReference docRef) {
    commandMerger.flushCurrentCommand();
    resetCurrentCommandMerger();
    undoStacksHolder.clearStacks(false, Set.of(docRef));
    redoStacksHolder.clearStacks(false, Set.of(docRef));
  }

  @Nullable PerClientLocalUndoRedoSnapshot getUndoRedoSnapshotForDocument(
    DocumentReference reference,
    SharedAdjustableUndoableActionsHolder adjustableUndoableActionsHolder
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

  // TODO: remove getter
  void setCurrentProject(Project currentProject) {
    this.currentProject = currentProject;
  }

  // TODO: remove getter
  Project getCurrentProject() {
    return currentProject;
  }

  UndoManagerImpl getUndoManager() {
    return undoManager;
  }

  ClientId getClientId() {
    return clientId;
  }

  UndoRedoStacksHolder getUndoStacksHolder() {
    return undoStacksHolder;
  }

  UndoRedoStacksHolder getRedoStacksHolder() {
    return redoStacksHolder;
  }

  CommandMerger getCommandMerger() {
    return commandMerger;
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

  private @Nullable UndoableGroup getLastAction(@NotNull FileEditor editor, boolean isUndo) {
    Collection<DocumentReference> refs = UndoDocumentUtil.getDocRefs(editor);
    if (refs == null) {
      return null;
    }
    if (isUndo) {
      commandMerger.flushCurrentCommand();
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

  private void resetCurrentCommandMerger() {
    LOG.assertTrue(!isInsideCommand());
    currentCommandMerger = null;
  }

  private @NotNull Set<DocumentReference> clearStacks() {
    var affected = new HashSet<DocumentReference>();
    commandMerger.flushCurrentCommand(nextCommandTimestamp(), undoStacksHolder);
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
