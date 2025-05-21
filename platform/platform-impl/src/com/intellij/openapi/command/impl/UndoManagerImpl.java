// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.client.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.NlsActions.ActionDescription;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.NlsContexts.Command;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ExternalChangeAction;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.*;

import java.util.*;

public class UndoManagerImpl extends UndoManager {

  private static final Logger LOG = Logger.getInstance(UndoManagerImpl.class);

  @TestOnly
  @SuppressWarnings("StaticNonFinalField")
  public static boolean ourNeverAskUser = false;

  public static boolean isRefresh() {
    return ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction.class);
  }

  public static int getGlobalUndoLimit() {
    return Registry.intValue("undo.globalUndoLimit");
  }

  public static int getDocumentUndoLimit() {
    return Registry.intValue("undo.documentUndoLimit");
  }

  private final @Nullable Project myProject;
  private final @NotNull SharedAdjustableUndoableActionsHolder myAdjustableUndoableActionsHolder = new SharedAdjustableUndoableActionsHolder();
  private final @NotNull SharedUndoRedoStacksHolder mySharedUndoStacksHolder = new SharedUndoRedoStacksHolder(true, myAdjustableUndoableActionsHolder);
  private final @NotNull SharedUndoRedoStacksHolder mySharedRedoStacksHolder = new SharedUndoRedoStacksHolder(false, myAdjustableUndoableActionsHolder);

  private @Nullable CurrentEditorProvider myOverriddenEditorProvider;

  @SuppressWarnings("unused")
  private UndoManagerImpl(@NotNull Project project) {
    this((ComponentManager)project);
  }

  @SuppressWarnings("unused")
  private UndoManagerImpl() {
    this((ComponentManager)null);
  }

  @ApiStatus.Internal
  @NonInjectable
  protected UndoManagerImpl(@Nullable ComponentManager componentManager) {
    myProject = componentManager instanceof Project project ? project : null;
  }

  @Override
  public boolean isUndoAvailable(@Nullable FileEditor editor) {
    return isUndoRedoAvailable(editor, true);
  }

  @Override
  public boolean isRedoAvailable(@Nullable FileEditor editor) {
    return isUndoRedoAvailable(editor, false);
  }

  @Override
  public @NotNull Pair<String, String> getUndoActionNameAndDescription(FileEditor editor) {
    return getUndoOrRedoActionNameAndDescription(editor, true);
  }

  @Override
  public @NotNull Pair<String, String> getRedoActionNameAndDescription(FileEditor editor) {
    return getUndoOrRedoActionNameAndDescription(editor, false);
  }

  @Override
  public void undo(@Nullable FileEditor editor) {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    LOG.assertTrue(isUndoAvailable(editor));
    undoOrRedo(editor, true);
  }

  @Override
  public void redo(@Nullable FileEditor editor) {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    LOG.assertTrue(isRedoAvailable(editor));
    undoOrRedo(editor, false);
  }

  @Override
  public boolean isUndoInProgress() {
    UndoClientState state = getClientState();
    return state != null && state.isUndoInProgress();
  }

  @Override
  public boolean isRedoInProgress() {
    UndoClientState state = getClientState();
    return state != null && state.isRedoInProgress();
  }

  @Override
  public void nonundoableActionPerformed(@NotNull DocumentReference ref, boolean isGlobal) {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    if (myProject != null && myProject.isDisposed()) {
      return;
    }
    undoableActionPerformed(new NonUndoableAction(ref, isGlobal));
  }

  @Override
  public void undoableActionPerformed(@NotNull UndoableAction action) {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    if (myProject != null && myProject.isDisposed()) {
      return;
    }
    UndoClientState state = getClientState();
    if (state != null) {
      state.addUndoableAction(getEditorProvider(), action);
    }
  }

  @Override
  public long getNextUndoNanoTime(@NotNull FileEditor editor) {
    UndoClientState state = getClientState(editor);
    return state == null ? -1 : state.getNextNanoTime(editor, true);
  }

  @Override
  public long getNextRedoNanoTime(@NotNull FileEditor editor) {
    UndoClientState state = getClientState(editor);
    return state == null ? -1 : state.getNextNanoTime(editor, false);
  }

  @Override
  public boolean isNextUndoAskConfirmation(@NotNull FileEditor editor) {
    UndoClientState state = getClientState(editor);
    return state != null && state.isNextAskConfirmation(editor, true);
  }

  @Override
  public boolean isNextRedoAskConfirmation(@NotNull FileEditor editor) {
    UndoClientState state = getClientState(editor);
    return state != null && state.isNextAskConfirmation(editor, false);
  }

  public boolean isActive() {
    UndoClientState state = getClientState();
    return state != null && state.isActive();
  }

  public void addDocumentAsAffected(@NotNull Document document) {
    UndoClientState state = getClientState();
    if (state != null) {
      state.addDocumentAsAffected(DocumentReferenceManager.getInstance().create(document));
    }
  }

  public void markCurrentCommandAsGlobal() {
    UndoClientState state = getClientState();
    if (state != null) {
      state.markCurrentCommandAsGlobal();
    }
  }

  public void addAffectedFiles(VirtualFile @NotNull ... files) {
    UndoClientState state = getClientState();
    if (state != null) {
      state.addAffectedFiles(files);
    }
  }

  public void invalidateActionsFor(@NotNull DocumentReference ref) {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    for (UndoClientState state : getAllClientStates()) {
      state.invalidateActions(ref);
    }
  }

  public @NotNull CurrentEditorProvider getEditorProvider() {
    CurrentEditorProvider provider = myOverriddenEditorProvider;
    return (provider != null) ? provider : CurrentEditorProvider.getInstance();
  }

  public @Nullable Project getProject() {
    return myProject;
  }

  @ApiStatus.Internal
  public boolean isInsideCommand() {
    UndoClientState state = getClientState();
    return state != null && state.isInsideCommand();
  }

  @ApiStatus.Internal
  public @Nullable ResetUndoHistoryToken createResetUndoHistoryToken(@NotNull FileEditor editor) {
    Collection<DocumentReference> references = UndoDocumentUtil.getDocumentReferences(editor);
    if (references.size() != 1) {
      return null;
    }
    DocumentReference reference = references.iterator().next();
    LocalUndoRedoSnapshot snapshot = getUndoRedoSnapshotForDocument(reference);
    if (snapshot == null) {
      return null;
    }
    return new ResetUndoHistoryToken(this, snapshot, reference);
  }

  @ApiStatus.Internal
  public @NotNull String dumpState(@Nullable FileEditor editor) {
    boolean undoAvailable = isUndoAvailable(editor);
    boolean redoAvailable = isRedoAvailable(editor);
    Pair<String, String> undoDescription = getUndoActionNameAndDescription(editor);
    Pair<String, String> redoDescription = getRedoActionNameAndDescription(editor);
    String undoStatus = "undo: %s, %s, %s".formatted(undoAvailable, undoDescription.getFirst(), undoDescription.getSecond());
    String redoStatus = "redo: %s, %s, %s".formatted(redoAvailable, redoDescription.getFirst(), redoDescription.getSecond());
    String stacks;
    UndoClientState state = getClientState(editor);
    Collection<DocumentReference> docRefs = UndoDocumentUtil.getDocRefs(editor);
    if (state == null && docRefs == null) {
      stacks = "no state, no docs";
    } else if (state != null && docRefs == null) {
      stacks = "no docs";
    } else if (state == null /* && docRefs != null */) {
      stacks = "no state";
    } else {
      stacks = state.dump(docRefs);
    }
    return  "\n" + undoStatus + "\n" + redoStatus + "\n" + stacks;
  }

  @ApiStatus.Internal
  public void clearDocumentReferences(@NotNull Document document) {
    ThreadingAssertions.assertEventDispatchThread();
    for (UndoClientState state : getAllClientStates()) {
      state.clearDocumentReferences(document);
    }
    mySharedUndoStacksHolder.clearDocumentReferences(document);
    mySharedRedoStacksHolder.clearDocumentReferences(document);
  }

  @ApiStatus.Internal
  protected void notifyUndoRedoStarted(@Nullable FileEditor editor, @NotNull Disposable disposable, boolean isUndo) {
    ApplicationManager.getApplication()
      .getMessageBus()
      .syncPublisher(UndoRedoListener.Companion.getTOPIC())
      .undoRedoStarted(myProject, this, editor, isUndo, disposable);
  }

  @ApiStatus.Internal
  protected boolean isSpeculativeUndoAllowed(@Nullable FileEditor editor, boolean isUndo) {
    UndoClientState clientState = getClientState(editor);
    return clientState != null && clientState.isSpeculativeUndoAllowed(editor, isUndo);
  }

  void trimSharedStacks(@NotNull DocumentReference docRef) {
    mySharedRedoStacksHolder.trimStacks(Collections.singleton(docRef));
    mySharedUndoStacksHolder.trimStacks(Collections.singleton(docRef));
  }

  void onCommandStarted(Project project, UndoConfirmationPolicy undoConfirmationPolicy, boolean recordOriginalReference) {
    UndoClientState state = getClientState();
    if (state == null || !state.isInsideCommand()) {
      for (UndoProvider undoProvider : getUndoProviders()) {
        undoProvider.commandStarted(project);
      }
    }
    if (state != null) {
      state.commandStarted(project, getEditorProvider(), undoConfirmationPolicy, recordOriginalReference);
    }
  }

  void onCommandFinished(Project project, @Command String commandName, Object commandGroupId) {
    UndoClientState state = getClientState();
    if (state != null) {
      state.commandFinished(getEditorProvider(), commandName, commandGroupId);
    }
    if (state == null || !state.isInsideCommand()) {
      for (UndoProvider undoProvider : getUndoProviders()) {
        undoProvider.commandFinished(project);
      }
    }
  }

  void addAffectedDocuments(Document @NotNull ... docs) {
    UndoClientState state = getClientState();
    if (state != null) {
      state.addAffectedDocuments(docs);
    }
  }

  @Nullable LocalUndoRedoSnapshot getUndoRedoSnapshotForDocument(@NotNull DocumentReference reference) {
    HashMap<ClientId, PerClientLocalUndoRedoSnapshot> map = new HashMap<>();
    for (UndoClientState state : getAllClientStates()) {
      PerClientLocalUndoRedoSnapshot perClientSnapshot = state.getUndoRedoSnapshotForDocument(reference, myAdjustableUndoableActionsHolder);
      if (perClientSnapshot == null) {
        return null;
      }
      map.put(state.getClientId(), perClientSnapshot);
    }
    return new LocalUndoRedoSnapshot(
      map,
      mySharedUndoStacksHolder.getStack(reference).snapshot(),
      mySharedRedoStacksHolder.getStack(reference).snapshot()
    );
  }

  boolean resetLocalHistory(DocumentReference reference, LocalUndoRedoSnapshot snapshot) {
    for (UndoClientState state : getAllClientStates()) {
      PerClientLocalUndoRedoSnapshot perClientSnapshot = snapshot.getClientSnapshots().get(state.getClientId());
      if (perClientSnapshot == null) {
        perClientSnapshot = PerClientLocalUndoRedoSnapshot.Companion.empty();
      }
      boolean success = state.resetLocalHistory(reference, perClientSnapshot);
      if (success) {
        myAdjustableUndoableActionsHolder.getStack(reference).resetTo(perClientSnapshot.getActionsHolderSnapshot());
      } else {
        return false;
      }
    }
    mySharedUndoStacksHolder.getStack(reference).resetTo(snapshot.getSharedUndoStack());
    mySharedRedoStacksHolder.getStack(reference).resetTo(snapshot.getSharedRedoStack());
    return true;
  }

  boolean isUndoRedoAvailable(@NotNull DocumentReference docRef, boolean undo) {
    UndoClientState state = getClientState();
    return state != null && state.isUndoRedoAvailable(Collections.singleton(docRef), undo);
  }

  @NotNull SharedAdjustableUndoableActionsHolder getAdjustableUndoableActionsHolder() {
    return myAdjustableUndoableActionsHolder;
  }

  @NotNull SharedUndoRedoStacksHolder getSharedUndoStacksHolder() {
    return mySharedUndoStacksHolder;
  }

  @NotNull SharedUndoRedoStacksHolder getSharedRedoStacksHolder() {
    return mySharedRedoStacksHolder;
  }

  @TestOnly
  public void setOverriddenEditorProvider(@Nullable CurrentEditorProvider p) {
    myOverriddenEditorProvider = p;
  }

  @TestOnly
  public void dropHistoryInTests() {
    UndoClientState state = getClientState();
    if (state != null) {
      flushMergers();
      state.dropHistoryInTests();
    }
  }

  @TestOnly
  public void flushCurrentCommandMerger() {
    UndoClientState state = getClientState();
    if (state != null) {
      state.flushCurrentCommand();
    }
  }

  @TestOnly
  public void clearUndoRedoQueueInTests(@NotNull VirtualFile file) {
    UndoClientState state = getClientState();
    if (state != null) {
      DocumentReference docRef = DocumentReferenceManager.getInstance().create(file);
      state.clearUndoRedoQueue(docRef);
      trimSharedStacks(docRef);
    }
  }

  @TestOnly
  public void clearUndoRedoQueueInTests(@NotNull Document document) {
    UndoClientState state = getClientState();
    if (state != null) {
      DocumentReference docRef = DocumentReferenceManager.getInstance().create(document);
      state.clearUndoRedoQueue(docRef);
      trimSharedStacks(docRef);
    }
  }

  private @NotNull Pair<@ActionText String, @ActionDescription String> getUndoOrRedoActionNameAndDescription(@Nullable FileEditor editor, boolean undo) {
    UndoClientState state = getClientState(editor);
    String desc = null;
    if (state != null && state.isUndoRedoAvailable(editor, undo)) {
      desc = state.getLastCommandName(editor, undo);
    }
    if (desc == null) {
      desc = "";
    }
    String shortActionName = StringUtil.first(desc, 30, true);
    if (desc.isEmpty()) {
      desc = undo ? ActionsBundle.message("action.undo.description.empty")
                  : ActionsBundle.message("action.redo.description.empty");
    }
    String name = undo ? ActionsBundle.message("action.undo.text", shortActionName)
                       : ActionsBundle.message("action.redo.text", shortActionName);
    String description = undo ? ActionsBundle.message("action.undo.description", desc)
                              : ActionsBundle.message("action.redo.description", desc);
    return Pair.create(name.trim(), description.trim());
  }

  private boolean isUndoRedoAvailable(@Nullable FileEditor editor, boolean undo) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    UndoClientState state = getClientState(editor);
    return state != null && state.isUndoRedoAvailable(editor, undo);
  }

  private @Nullable UndoClientState getClientState() {
    ClientId clientId = ClientId.getCurrentOrNull();
    if (clientId != null) {
      ClientSession appSession = ClientSessionsManager.getAppSession(clientId);
      if (appSession != null && appSession.isController()) {
        // IJPL-168172: If current session is a controller, return a local client state instead
        try (AccessToken ignored = ClientId.withExplicitClientId(ClientId.getLocalId())) {
          return getComponentManager().getService(UndoClientState.class);
        }
      }
    }
    return getComponentManager().getService(UndoClientState.class);
  }

  private void undoOrRedo(@Nullable FileEditor editor, boolean isUndo) {
    UndoClientState state = getClientState(editor);
    if (state != null) {
      String commandName = getUndoOrRedoActionNameAndDescription(editor, isUndo).getSecond();
      Disposable disposable = Disposer.newDisposable();
      Runnable beforeUndoRedoStarted = () -> notifyUndoRedoStarted(editor, disposable, isUndo);
      try {
        state.undoOrRedo(editor, commandName, beforeUndoRedoStarted, isUndo);
      } finally {
        Disposer.dispose(disposable);
      }
    }
  }

  private @Nullable UndoClientState getClientState(@Nullable FileEditor editor) {
    UndoClientState state = getClientState();
    if (myProject == null || editor == null) {
      return state;
    }
    try (AccessToken ignored = ClientId.withExplicitClientId(ClientFileEditorManager.getClientId(editor))) {
      UndoClientState editorState = getClientState();
      LOG.assertTrue(
        state == editorState,
        "Using editor belonging to '" +
        (editorState != null ? editorState.getClientId().getValue() : "null") + "' under '" +
        (state != null ? state.getClientId().getValue() : "null") + "'"
      );
    }
    return state;
  }

  private @Unmodifiable @NotNull List<UndoClientState> getAllClientStates() {
    return getComponentManager().getServices(UndoClientState.class, ClientKind.ALL);
  }

  private @NotNull List<UndoProvider> getUndoProviders() {
    return myProject == null
           ? UndoProvider.EP_NAME.getExtensionList()
           : UndoProvider.PROJECT_EP_NAME.getExtensionList(myProject);
  }

  private @NotNull ComponentManager getComponentManager() {
    return myProject != null ? myProject : ApplicationManager.getApplication();
  }

  @TestOnly
  private void flushMergers() {
    assert myProject == null || !myProject.isDisposed() : myProject;
    // Run dummy command in order to flush all mergers...
    //noinspection HardCodedStringLiteral
    CommandProcessor.getInstance().executeCommand(myProject, EmptyRunnable.getInstance(), "Dummy", null);
  }

  @Override
  public String toString() {
    return "UndoManager for " + ObjectUtils.notNull(myProject, "application");
  }
}
