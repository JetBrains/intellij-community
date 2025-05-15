// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.IdeBundle;
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
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ExternalChangeAction;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.*;

import java.util.*;

public class UndoManagerImpl extends UndoManager {
  private static final Logger LOG = Logger.getInstance(UndoManagerImpl.class);

  @SuppressWarnings("StaticNonFinalField")
  @TestOnly
  public static boolean ourNeverAskUser;

  private final @Nullable Project myProject;

  private @Nullable CurrentEditorProvider myOverriddenEditorProvider;

  private final SharedAdjustableUndoableActionsHolder myAdjustableUndoableActionsHolder = new SharedAdjustableUndoableActionsHolder();
  private final SharedUndoRedoStacksHolder mySharedUndoStacksHolder = new SharedUndoRedoStacksHolder(true, myAdjustableUndoableActionsHolder);
  private final SharedUndoRedoStacksHolder mySharedRedoStacksHolder = new SharedUndoRedoStacksHolder(false, myAdjustableUndoableActionsHolder);

  public static boolean isRefresh() {
    return ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction.class);
  }

  public static int getGlobalUndoLimit() {
    return Registry.intValue("undo.globalUndoLimit");
  }

  public static int getDocumentUndoLimit() {
    return Registry.intValue("undo.documentUndoLimit");
  }

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
    myProject = componentManager instanceof Project ? (Project)componentManager : null;
  }

  public @Nullable Project getProject() {
    return myProject;
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

  private @Nullable UndoClientState getClientState(@Nullable FileEditor editor) {
    UndoClientState state = getClientState();
    if (myProject == null || editor == null) return state;

    try (AccessToken ignored = ClientId.withExplicitClientId(ClientFileEditorManager.getClientId(editor))) {
      UndoClientState editorState = getClientState();
      LOG.assertTrue(state == editorState,
                     "Using editor belonging to '" + (editorState != null ? editorState.getClientId().getValue() : "null") +
                     "' under '" + (state != null ? state.getClientId().getValue() : "null") + "'");
    }

    return state;
  }

  private List<UndoClientState> getAllClientStates() {
    return getComponentManager().getServices(UndoClientState.class, ClientKind.ALL);
  }

  private ComponentManager getComponentManager() {
    return myProject != null ? myProject : ApplicationManager.getApplication();
  }

  void trimSharedStacks(@NotNull DocumentReference docRef) {
    trimSharedStacks(Set.of(docRef));
  }

  void trimSharedStacks(@NotNull Set<DocumentReference> docRefs) {
    mySharedRedoStacksHolder.trimStacks(docRefs);
    mySharedUndoStacksHolder.trimStacks(docRefs);
  }

  public boolean isActive() {
    UndoClientState state = getClientState();
    if (state == null) {
      return false;
    }
    return Comparing.equal(myProject, state.getCurrentProject()) || myProject == null && state.getCurrentProject().isDefault();
  }

  @ApiStatus.Internal
  public boolean isInsideCommand() {
    UndoClientState state = getClientState();
    return state != null && state.isInsideCommand();
  }

  private @NotNull List<UndoProvider> getUndoProviders() {
    return myProject == null ? UndoProvider.EP_NAME.getExtensionList() : UndoProvider.PROJECT_EP_NAME.getExtensionList(myProject);
  }

  void onCommandStarted(final Project project, UndoConfirmationPolicy undoConfirmationPolicy, boolean recordOriginalReference) {
    UndoClientState state = getClientState();
    if (state == null || !state.isInsideCommand()) {
      for (UndoProvider undoProvider : getUndoProviders()) {
        undoProvider.commandStarted(project);
      }
      if (state != null) {
        state.setCurrentProject(project);
      }
    }

    if (state != null) {
      state.commandStarted(myProject, getEditorProvider(), undoConfirmationPolicy, myProject == project && recordOriginalReference);
    }

    LOG.assertTrue(state == null || !state.isInsideCommand() || !(state.getCurrentProject() instanceof DummyProject));
  }

  void onCommandFinished(final Project project, final @NlsContexts.Command String commandName, final Object commandGroupId) {
    UndoClientState state = getClientState();
    if (state != null) {
      state.commandFinished(myProject, getEditorProvider(), commandName, commandGroupId);
    }
    if (state == null || !state.isInsideCommand()) {
      for (UndoProvider undoProvider : getUndoProviders()) {
        undoProvider.commandFinished(project);
      }
      if (state != null) {
        state.setCurrentProject(DummyProject.getInstance());
      }
    }
    LOG.assertTrue(state == null || !state.isInsideCommand() || !(state.getCurrentProject() instanceof DummyProject));
  }

  public void addDocumentAsAffected(@NotNull Document document) {
    UndoClientState state = getClientState();
    if (state != null) {
      state.addDocumentAsAffected(DocumentReferenceManager.getInstance().create(document));
    }
  }

  @Override
  public void nonundoableActionPerformed(final @NotNull DocumentReference ref, final boolean isGlobal) {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    if (myProject != null && myProject.isDisposed()) return;
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
      state.addUndoableAction(myProject, getEditorProvider(), action);
    }
  }

  public void markCurrentCommandAsGlobal() {
    UndoClientState state = getClientState();
    if (state == null) {
      return;
    }
    if (state.getCurrentCommandMerger() == null) {
      LOG.error("Must be called inside command");
      return;
    }
    state.getCurrentCommandMerger().markAsGlobal();
  }

  void addAffectedDocuments(Document @NotNull ... docs) {
    UndoClientState state = getClientState();
    if (state == null) {
      return;
    }
    if (!isInsideCommand()) {
      LOG.error("Must be called inside command");
      return;
    }
    List<DocumentReference> refs = new ArrayList<>(docs.length);
    for (Document each : docs) {
      // is document's file still valid
      VirtualFile file = FileDocumentManager.getInstance().getFile(each);
      if (file != null && !file.isValid()) continue;

      refs.add(DocumentReferenceManager.getInstance().create(each));
    }
    state.getCurrentCommandMerger().addAdditionalAffectedDocuments(refs);
  }

  public void addAffectedFiles(VirtualFile @NotNull ... files) {
    UndoClientState state = getClientState();
    if (state == null) {
      return;
    }
    if (!isInsideCommand()) {
      LOG.error("Must be called inside command");
      return;
    }
    List<DocumentReference> refs = new ArrayList<>(files.length);
    for (VirtualFile each : files) {
      refs.add(DocumentReferenceManager.getInstance().create(each));
    }
    state.getCurrentCommandMerger().addAdditionalAffectedDocuments(refs);
  }

  public void invalidateActionsFor(@NotNull DocumentReference ref) {
    for (UndoClientState state : getAllClientStates()) {
      ApplicationManager.getApplication().assertWriteIntentLockAcquired();
      state.invalidateActions(ref);
    }
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

  @ApiStatus.Internal
  public @Nullable ResetUndoHistoryToken createResetUndoHistoryToken(@NotNull FileEditor editor) {
    Collection<DocumentReference> references = UndoDocumentUtil.getDocumentReferences(editor);
    if (references.size() != 1)
      return null;

    DocumentReference reference = references.iterator().next();
    LocalUndoRedoSnapshot snapshot = getUndoRedoSnapshotForDocument(reference);
    if (snapshot == null) return null;

    return new ResetUndoHistoryToken(this, snapshot, reference);
  }

  @Nullable LocalUndoRedoSnapshot getUndoRedoSnapshotForDocument(DocumentReference reference) {
    HashMap<ClientId, PerClientLocalUndoRedoSnapshot> map = new HashMap<>();
    for (UndoClientState state : getAllClientStates()) {
      PerClientLocalUndoRedoSnapshot perClientSnapshot = state.getUndoRedoSnapshotForDocument(reference, myAdjustableUndoableActionsHolder);
      if (perClientSnapshot == null)
        return null;

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

  private void undoOrRedo(final FileEditor editor, final boolean isUndo) {
    UndoClientState state = getClientState(editor);
    if (state == null) {
      return;
    }
    Disposable disposable = Disposer.newDisposable();
    state.startUndoOrRedo(isUndo);
    try {
      RuntimeException[] exception = new RuntimeException[1];
      String name = getUndoOrRedoActionNameAndDescription(editor, state.isUndoInProgress()).getSecond();
      CommandProcessor.getInstance().executeCommand(
        myProject,
        () -> {
          notifyUndoRedoStarted(editor, isUndo, disposable);
          try {
            CopyPasteManager.getInstance().stopKillRings();
            state.getCommandMerger().undoOrRedo(editor, isUndo);
          }
          catch (RuntimeException ex) {
            exception[0] = ex;
          }
        },
        name,
        null,
        state.getCommandMerger().getUndoConfirmationPolicy()
      );
      if (exception[0] != null) {
        throw exception[0];
      }
    }
    finally {
      state.finishUndoOrRedo();
      Disposer.dispose(disposable);
    }
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
  public boolean isUndoAvailable(@Nullable FileEditor editor) {
    return isUndoOrRedoAvailable(editor, true);
  }

  @Override
  public boolean isRedoAvailable(@Nullable FileEditor editor) {
    return isUndoOrRedoAvailable(editor, false);
  }

  boolean isUndoOrRedoAvailable(@Nullable FileEditor editor, boolean undo) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Collection<DocumentReference> refs = UndoDocumentUtil.getDocRefs(editor);
    return refs != null && isUndoOrRedoAvailable(getClientState(editor), refs, undo);
  }

  boolean isUndoOrRedoAvailable(@NotNull DocumentReference ref) {
    Set<DocumentReference> refs = Collections.singleton(ref);
    return isUndoOrRedoAvailable(getClientState(), refs, true) ||
           isUndoOrRedoAvailable(getClientState(), refs, false);
  }

  /**
   * In case of global group blocking undo we can perform undo locally and separate undone changes from others stacks
   */
  boolean splitGlobalCommand(@NotNull UndoRedo undoRedo) {
    UndoableGroup group = undoRedo.myUndoableGroup;
    Collection<DocumentReference> refs = undoRedo.getDocRefs();
    if (refs == null || refs.size() != 1) return false;
    DocumentReference docRef = refs.iterator().next();

    UndoClientState clientState = getClientState(undoRedo.myEditor);
    if (clientState == null) return false;
    UndoRedoStacksHolder stackHolder = getStackHolder(clientState, true);

    UndoRedoList<UndoableGroup> stack = stackHolder.getStack(docRef);
    if (stack.getLast() == group) {
      Pair<List<UndoableAction>, List<UndoableAction>> actions = separateLocalAndNonLocalActions(group.getActions(), docRef);
      if (actions.first.isEmpty()) return false;

      stack.removeLast();

      UndoableGroup replacingGroup = new UndoableGroup(IdeBundle.message("undo.command.local.name") + group.getCommandName(),
                                                       false,
                                                       group.getCommandTimestamp(),
                                                       group.getStateBefore(),
                                                       group.getStateAfter(),
                                                       // only action that changes file locally
                                                       actions.first,
                                                       stackHolder, getProject(), group.getConfirmationPolicy(), group.isTransparent(),
                                                       group.isValid());
      stack.add(replacingGroup);

      UndoableGroup groupWithoutLocalChanges = new UndoableGroup(group.getCommandName(),
                                                                 group.isGlobal(),
                                                                 group.getCommandTimestamp(),
                                                                 group.getStateBefore(),
                                                                 group.getStateAfter(),
                                                                 // all action except local
                                                                 actions.second,
                                                                 stackHolder, getProject(), group.getConfirmationPolicy(), group.isTransparent(),
                                                                 group.isValid());

      if (stackHolder.replaceOnStacks(group, groupWithoutLocalChanges)) {
        replacingGroup.setOriginalContext(new UndoableGroup.UndoableGroupOriginalContext(group, groupWithoutLocalChanges));
      }

      return true;
    }

    return false;
  }

  private static Pair<List<UndoableAction>, List<UndoableAction>> separateLocalAndNonLocalActions(@NotNull List<? extends UndoableAction> actions,
                                                                                                  @NotNull DocumentReference affectedDocument) {
    List<UndoableAction> localActions = new SmartList<>();
    List<UndoableAction> nonLocalActions = new SmartList<>();
    for (UndoableAction action : actions) {
      DocumentReference[] affectedDocuments = action.getAffectedDocuments();
      if (affectedDocuments != null && affectedDocuments.length == 1 && affectedDocuments[0].equals(affectedDocument)) {
        localActions.add(action);
      }
      else {
        nonLocalActions.add(action);
      }
    }

    return new Pair<>(localActions, nonLocalActions);
  }

  /**
   * If we redo group that was splitted before, we gather that group into global cammand(as it was before splitting)
   * and recover that command on all stacks
   */
  void gatherGlobalCommand(@NotNull UndoRedo undoRedo) {
    UndoableGroup group = undoRedo.myUndoableGroup;
    UndoableGroup.UndoableGroupOriginalContext context = group.getGroupOriginalContext();
    if (context == null) return;

    Collection<DocumentReference> refs = undoRedo.getDocRefs();
    if (refs.size() > 1) return;
    DocumentReference docRef = refs.iterator().next();

    UndoClientState clientState = getClientState(undoRedo.myEditor);
    if (clientState == null) return;
    UndoRedoStacksHolder stackHolder = getStackHolder(clientState, true);
    UndoRedoList<UndoableGroup> stack = stackHolder.getStack(docRef);
    if (stack.getLast() != group) return;

    boolean shouldGatherGroup = stackHolder.replaceOnStacks(context.getCurrentStackGroup(), context.getOriginalGroup());
    if (!shouldGatherGroup) return;

    stack.removeLast();
    stack.add(context.getOriginalGroup());
  }

  private static boolean isUndoOrRedoAvailable(@Nullable UndoClientState state,
                                               @NotNull Collection<? extends DocumentReference> refs,
                                               boolean isUndo) {
    if (state == null) return false;
    if (isUndo && state.getCommandMerger().isUndoAvailable(refs)) return true;
    UndoRedoStacksHolder stackHolder = getStackHolder(state, isUndo);
    return stackHolder.canBeUndoneOrRedone(refs);
  }

  private static @NotNull UndoRedoStacksHolder getStackHolder(@NotNull UndoClientState state, boolean isUndo) {
    return isUndo ? state.getUndoStacksHolder() : state.getRedoStacksHolder();
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
  public long getNextUndoNanoTime(@NotNull FileEditor editor) {
    return getNextNanoTime(editor, true);
  }

  @Override
  public long getNextRedoNanoTime(@NotNull FileEditor editor) {
    return getNextNanoTime(editor, false);
  }

  @Override
  public boolean isNextUndoAskConfirmation(@NotNull FileEditor editor) {
    return isNextAskConfirmation(editor, true);
  }

  @Override
  public boolean isNextRedoAskConfirmation(@NotNull FileEditor editor) {
    return isNextAskConfirmation(editor, false);
  }

  private long getNextNanoTime(@NotNull FileEditor editor, boolean isUndo) {
    UndoClientState clientState = getClientState(editor);
    Collection<DocumentReference> references = UndoDocumentUtil.getDocRefs(editor);
    if (clientState == null || references == null) {
      return -1L;
    }

    if (isUndo) {
      clientState.getCommandMerger().flushCurrentCommand();
    }

    @NotNull UndoRedoStacksHolder stack = getStackHolder(clientState, isUndo);
    UndoableGroup lastAction = stack.getLastAction(references);
    return lastAction == null ? -1L : lastAction.getGroupStartPerformedTimestamp();
  }

  private boolean isNextAskConfirmation(@NotNull FileEditor editor, boolean isUndo) {
    UndoClientState clientState = getClientState(editor);
    Collection<DocumentReference> references = UndoDocumentUtil.getDocRefs(editor);
    if (clientState == null || references == null) {
      return false;
    }

    if (isUndo) {
      clientState.getCommandMerger().flushCurrentCommand();
    }

    @NotNull UndoRedoStacksHolder stack = getStackHolder(clientState, isUndo);
    UndoableGroup lastAction = stack.getLastAction(references);
    return lastAction != null && lastAction.shouldAskConfirmation(!isUndo);
  }

  private @NotNull Pair<@NlsActions.ActionText String, @NlsActions.ActionDescription String> getUndoOrRedoActionNameAndDescription(
    @Nullable FileEditor editor,
    boolean undo
  ) {
    String desc = isUndoOrRedoAvailable(editor, undo) ? doFormatAvailableUndoRedoAction(editor, undo) : null;
    if (desc == null) desc = "";
    String shortActionName = StringUtil.first(desc, 30, true);

    if (desc.isEmpty()) {
      desc = undo
             ? ActionsBundle.message("action.undo.description.empty")
             : ActionsBundle.message("action.redo.description.empty");
    }

    return Pair.create((undo ? ActionsBundle.message("action.undo.text", shortActionName)
                             : ActionsBundle.message("action.redo.text", shortActionName)).trim(),
                       (undo ? ActionsBundle.message("action.undo.description", desc)
                             : ActionsBundle.message("action.redo.description", desc)).trim());
  }

  private @Nullable String doFormatAvailableUndoRedoAction(@Nullable FileEditor editor, boolean isUndo) {
    UndoClientState state = getClientState(editor);
    if (state == null) {
      return null;
    }
    Collection<DocumentReference> refs = UndoDocumentUtil.getDocRefs(editor);
    if (refs == null) return null;
    if (isUndo && state.getCommandMerger().isUndoAvailable(refs)) {
      return state.getCommandMerger().getCommandName();
    }
    return getStackHolder(state, isUndo).getLastAction(refs).getCommandName();
  }

  @NotNull
  SharedAdjustableUndoableActionsHolder getAdjustableUndoableActionsHolder() {
    return myAdjustableUndoableActionsHolder;
  }

  @NotNull
  SharedUndoRedoStacksHolder getSharedUndoStacksHolder() {
    return mySharedUndoStacksHolder;
  }

  @NotNull
  SharedUndoRedoStacksHolder getSharedRedoStacksHolder() {
    return mySharedRedoStacksHolder;
  }

  @TestOnly
  public void setOverriddenEditorProvider(@Nullable CurrentEditorProvider p) {
    myOverriddenEditorProvider = p;
  }

  public @NotNull CurrentEditorProvider getEditorProvider() {
    CurrentEditorProvider provider = myOverriddenEditorProvider;
    return (provider != null) ? provider : CurrentEditorProvider.getInstance();
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
  private void flushMergers() {
    assert myProject == null || !myProject.isDisposed() : myProject;
    // Run dummy command in order to flush all mergers...
    //noinspection HardCodedStringLiteral
    CommandProcessor.getInstance().executeCommand(myProject, EmptyRunnable.getInstance(), "Dummy", null);
  }

  @TestOnly
  public void flushCurrentCommandMerger() {
    UndoClientState state = getClientState();
    if (state != null) {
      state.getCommandMerger().flushCurrentCommand();
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

  @ApiStatus.Internal
  public void clearDocumentReferences(@NotNull Document document) {
    ThreadingAssertions.assertEventDispatchThread();
    for (UndoClientState state : getAllClientStates()) {
      state.getUndoStacksHolder().clearDocumentReferences(document);
      state.getRedoStacksHolder().clearDocumentReferences(document);
      state.getCommandMerger().clearDocumentReferences(document);
    }
    mySharedUndoStacksHolder.clearDocumentReferences(document);
    mySharedRedoStacksHolder.clearDocumentReferences(document);
  }

  @ApiStatus.Internal
  protected void notifyUndoRedoStarted(FileEditor editor, boolean isUndo, Disposable disposable) {
    ApplicationManager.getApplication()
      .getMessageBus()
      .syncPublisher(UndoRedoListener.Companion.getTOPIC())
      .undoRedoStarted(myProject, this, editor, isUndo, disposable);
  }

  @ApiStatus.Experimental
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
  protected boolean isSpeculativeUndoPossible(@Nullable FileEditor editor, boolean isUndo) {
    UndoClientState clientState = getClientState(editor);
    if (clientState != null && clientState.getCommandMerger().hasActions()) {
      return clientState.getCommandMerger().isSpeculativeUndoPossible();
    }
    UndoableGroup action = getLastAction(editor, isUndo);
    return action != null && action.isSpeculativeUndoPossible();
  }

  private @Nullable UndoableGroup getLastAction(@Nullable FileEditor editor, boolean isUndo) {
    UndoClientState clientState = getClientState(editor);
    Collection<DocumentReference> references = UndoDocumentUtil.getDocRefs(editor);
    if (clientState == null || references == null) {
      return null;
    }
    UndoRedoStacksHolder stacksHolder = getStackHolder(clientState, isUndo);
    UndoableGroup action = stacksHolder.getLastAction(references);
    return action;
  }

  @Override
  public String toString() {
    return "UndoManager for " + ObjectUtils.notNull(myProject, "application");
  }
}
