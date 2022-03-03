// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.ide.DataManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ExternalChangeAction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

// Android team doesn't want to use new mockito for now, so, class cannot be final
@ApiStatus.NonExtendable
public class UndoManagerImpl extends UndoManager {
  private static final Logger LOG = Logger.getInstance(UndoManagerImpl.class);

  @SuppressWarnings("StaticNonFinalField")
  @TestOnly
  public static boolean ourNeverAskUser;

  private static final int COMMANDS_TO_KEEP_LIVE_QUEUES = 100;
  private static final int COMMAND_TO_RUN_COMPACT = 20;
  private static final int FREE_QUEUES_LIMIT = 30;

  private final @Nullable Project myProject;

  private CurrentEditorProvider myEditorProvider;

  static final class ClientState implements Disposable {
    private final UndoRedoStacksHolder myUndoStacksHolder = new UndoRedoStacksHolder(true);
    private final UndoRedoStacksHolder myRedoStacksHolder = new UndoRedoStacksHolder(false);

    private final CommandMerger myMerger;
    private final UndoManagerImpl myManager;

    private CommandMerger myCurrentMerger;
    private Project myCurrentActionProject = DummyProject.getInstance();

    private int myCommandTimestamp = 1;

    private int myCommandLevel;

    private OperationState myCurrentOperationState = OperationState.NONE;

    private DocumentReference myOriginatorReference;

    @SuppressWarnings("unused")
    private ClientState() {
      myManager = getUndoManager(ApplicationManager.getApplication());
      myMerger = new CommandMerger(myManager);
    }

    @SuppressWarnings("unused")
    private ClientState(@NotNull Project project) {
      myManager = getUndoManager(project);
      myMerger = new CommandMerger(myManager);
    }

    private static @NotNull UndoManagerImpl getUndoManager(@NotNull ComponentManager manager) {
      return (UndoManagerImpl)manager.getService(UndoManager.class);
    }

    private int nextCommandTimestamp() {
      return ++myCommandTimestamp;
    }

    @Override
    public void dispose() {
      myManager.invalidate(this);
    }
  }

  private enum OperationState {NONE, UNDO, REDO}

  private final SharedUndoRedoStacksHolder mySharedUndoStacksHolder = new SharedUndoRedoStacksHolder(true);
  private final SharedUndoRedoStacksHolder mySharedRedoStacksHolder = new SharedUndoRedoStacksHolder(false);

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
  private UndoManagerImpl() {
    this(null);
  }

  private UndoManagerImpl(@Nullable Project project) {
    myProject = project;

    if (project != null && project.isDefault()) {
      return;
    }

    myEditorProvider = () -> ApplicationManager.getApplication().getService(CurrentEditorProvider.class).getCurrentEditor();
  }

  public @Nullable Project getProject() {
    return myProject;
  }

  private @Nullable ClientState getClientState() {
    return getComponentManager().getService(ClientState.class);
  }

  private List<ClientState> getAllClientStates() {
    return getComponentManager().getServices(ClientState.class, true);
  }

  private ComponentManager getComponentManager() {
    return myProject != null ? myProject : ApplicationManager.getApplication();
  }

  private void invalidate(@NotNull ClientState state) {
    state.myMerger.flushCurrentCommand(state.nextCommandTimestamp(), state.myUndoStacksHolder);
    Set<DocumentReference> affected = new HashSet<>();
    state.myRedoStacksHolder.collectAllAffectedDocuments(affected);
    state.myRedoStacksHolder.clearStacks(true, affected);
    state.myUndoStacksHolder.collectAllAffectedDocuments(affected);
    state.myUndoStacksHolder.clearStacks(true, affected);
    mySharedRedoStacksHolder.trimStacks(affected);
    mySharedUndoStacksHolder.trimStacks(affected);
  }

  public boolean isActive() {
    ClientState state = getClientState();
    if (state == null) {
      return false;
    }
    return Comparing.equal(myProject, state.myCurrentActionProject) || myProject == null && state.myCurrentActionProject.isDefault();
  }

  @ApiStatus.Internal
  public boolean isInsideCommand() {
    ClientState state = getClientState();
    if (state == null) {
      return false;
    }
    return state.myCommandLevel > 0;
  }

  private @NotNull List<UndoProvider> getUndoProviders() {
    return myProject == null ? UndoProvider.EP_NAME.getExtensionList() : UndoProvider.PROJECT_EP_NAME.getExtensionList(myProject);
  }

  private void onCommandStarted(final Project project, UndoConfirmationPolicy undoConfirmationPolicy, boolean recordOriginalReference) {
    ClientState state = getClientState();
    if (state == null || state.myCommandLevel == 0) {
      for (UndoProvider undoProvider : getUndoProviders()) {
        undoProvider.commandStarted(project);
      }
      if (state != null) {
        state.myCurrentActionProject = project;
      }
    }

    commandStarted(undoConfirmationPolicy, myProject == project && recordOriginalReference);

    LOG.assertTrue(state == null || state.myCommandLevel == 0 || !(state.myCurrentActionProject instanceof DummyProject));
  }

  private void onCommandFinished(final Project project, final @NlsContexts.Command String commandName, final Object commandGroupId) {
    ClientState state = getClientState();
    commandFinished(commandName, commandGroupId);
    if (state == null || state.myCommandLevel == 0) {
      for (UndoProvider undoProvider : getUndoProviders()) {
        undoProvider.commandFinished(project);
      }
      if (state != null) {
        state.myCurrentActionProject = DummyProject.getInstance();
      }
    }
    LOG.assertTrue(state == null || state.myCommandLevel == 0 || !(state.myCurrentActionProject instanceof DummyProject));
  }

  private void commandStarted(UndoConfirmationPolicy undoConfirmationPolicy, boolean recordOriginalReference) {
    ClientState state = getClientState();
    if (state == null) {
      return;
    }
    if (state.myCommandLevel == 0) {
      state.myCurrentMerger = new CommandMerger(this, CommandProcessor.getInstance().isUndoTransparentActionInProgress());

      if (recordOriginalReference && myProject != null) {
        Editor editor = null;
        final Application application = ApplicationManager.getApplication();
        if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
          editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext());
        }
        else {
          FileEditor fileEditor = myEditorProvider.getCurrentEditor();
          if (fileEditor instanceof TextEditor) {
            editor = ((TextEditor)fileEditor).getEditor();
          }
        }

        if (editor != null) {
          Document document = editor.getDocument();
          VirtualFile file = FileDocumentManager.getInstance().getFile(document);
          if (file != null && file.isValid()) {
            state.myOriginatorReference = DocumentReferenceManager.getInstance().create(file);
          }
        }
      }
    }
    LOG.assertTrue(state.myCurrentMerger != null, String.valueOf(state.myCommandLevel));
    state.myCurrentMerger.setBeforeState(getCurrentState());
    state.myCurrentMerger.mergeUndoConfirmationPolicy(undoConfirmationPolicy);

    state.myCommandLevel++;
  }

  private void commandFinished(@NlsContexts.Command String commandName, Object groupId) {
    ClientState state = getClientState();
    if (state == null) {
      return;
    }
    if (state.myCommandLevel == 0) return; // possible if command listener was added within command
    state.myCommandLevel--;
    if (state.myCommandLevel > 0) return;

    if (myProject != null && state.myCurrentMerger.hasActions() && !state.myCurrentMerger.isTransparent() && state.myCurrentMerger.isPhysical() &&
        state.myOriginatorReference != null) {
      addDocumentAsAffected(state.myOriginatorReference);
    }
    state.myOriginatorReference = null;

    state.myCurrentMerger.setAfterState(getCurrentState());
    state.myMerger.commandFinished(commandName, groupId, state.myCurrentMerger);

    disposeCurrentMerger(state);
  }

  public void addDocumentAsAffected(@NotNull Document document) {
    addDocumentAsAffected(DocumentReferenceManager.getInstance().create(document));
  }

  private void addDocumentAsAffected(@NotNull DocumentReference documentReference) {
    ClientState state = getClientState();
    if (state == null || state.myCurrentMerger.hasChangesOf(documentReference, true)) {
      return;
    }

    DocumentReference[] refs = {documentReference};
    state.myCurrentMerger.addAction(new MentionOnlyUndoableAction(refs));
  }

  private EditorAndState getCurrentState() {
    FileEditor editor = myEditorProvider.getCurrentEditor();
    if (editor == null) {
      return null;
    }
    if (!editor.isValid()) {
      return null;
    }
    return new EditorAndState(editor, editor.getState(FileEditorStateLevel.UNDO));
  }

  private static void disposeCurrentMerger(@NotNull ClientState state) {
    LOG.assertTrue(state.myCommandLevel == 0);
    if (state.myCurrentMerger != null) {
      state.myCurrentMerger = null;
    }
  }

  @Override
  public void nonundoableActionPerformed(final @NotNull DocumentReference ref, final boolean isGlobal) {
    ApplicationManager.getApplication().assertIsWriteThread();
    if (myProject != null && myProject.isDisposed()) return;
    undoableActionPerformed(new NonUndoableAction(ref, isGlobal));
  }

  @Override
  public void undoableActionPerformed(@NotNull UndoableAction action) {
    ClientState state = getClientState();
    if (state == null) {
      return;
    }
    ApplicationManager.getApplication().assertIsWriteThread();
    if (myProject != null && myProject.isDisposed() || state.myCurrentOperationState != OperationState.NONE) {
      return;
    }

    if (state.myCommandLevel == 0) {
      LOG.assertTrue(action instanceof NonUndoableAction,
                     "Undoable actions allowed inside commands only (see com.intellij.openapi.command.CommandProcessor.executeCommand())");
      commandStarted(UndoConfirmationPolicy.DEFAULT, false);
      state.myCurrentMerger.addAction(action);
      commandFinished("", null);
      return;
    }

    if (isRefresh()) state.myOriginatorReference = null;

    state.myCurrentMerger.addAction(action);
  }

  public void markCurrentCommandAsGlobal() {
    ClientState state = getClientState();
    if (state == null) {
      return;
    }
    if (state.myCurrentMerger == null) {
      LOG.error("Must be called inside command");
      return;
    }
    state.myCurrentMerger.markAsGlobal();
  }

  void addAffectedDocuments(Document @NotNull ... docs) {
    ClientState state = getClientState();
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
    state.myCurrentMerger.addAdditionalAffectedDocuments(refs);
  }

  public void addAffectedFiles(VirtualFile @NotNull ... files) {
    ClientState state = getClientState();
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
    state.myCurrentMerger.addAdditionalAffectedDocuments(refs);
  }

  public void invalidateActionsFor(@NotNull DocumentReference ref) {
    for (ClientState state : getAllClientStates()) {
      ApplicationManager.getApplication().assertIsWriteThread();
      state.myMerger.invalidateActionsFor(ref);
      if (state.myCurrentMerger != null) state.myCurrentMerger.invalidateActionsFor(ref);
      state.myUndoStacksHolder.invalidateActionsFor(ref);
      state.myRedoStacksHolder.invalidateActionsFor(ref);
    }
  }

  @Override
  public void undo(@Nullable FileEditor editor) {
    ApplicationManager.getApplication().assertIsWriteThread();
    LOG.assertTrue(isUndoAvailable(editor));
    undoOrRedo(editor, true);
  }

  @Override
  public void redo(@Nullable FileEditor editor) {
    ApplicationManager.getApplication().assertIsWriteThread();
    LOG.assertTrue(isRedoAvailable(editor));
    undoOrRedo(editor, false);
  }

  private void undoOrRedo(final FileEditor editor, final boolean isUndo) {
    ClientState state = getClientState();
    if (state == null) {
      return;
    }
    state.myCurrentOperationState = isUndo ? OperationState.UNDO : OperationState.REDO;
    try {
      final RuntimeException[] exception = new RuntimeException[1];
      Runnable executeUndoOrRedoAction = () -> {
        try {
          CopyPasteManager.getInstance().stopKillRings();
          state.myMerger.undoOrRedo(editor, isUndo);
        }
        catch (RuntimeException ex) {
          exception[0] = ex;
        }
      };
      String name = getUndoOrRedoActionNameAndDescription(editor, isUndoInProgress()).second;
      CommandProcessor.getInstance()
        .executeCommand(myProject, executeUndoOrRedoAction, name, null, state.myMerger.getUndoConfirmationPolicy());
      if (exception[0] != null) throw exception[0];
    }
    finally {
      state.myCurrentOperationState = OperationState.NONE;
    }
  }

  @Override
  public boolean isUndoInProgress() {
    ClientState state = getClientState();
    if (state == null) {
      return false;
    }
    return state.myCurrentOperationState == OperationState.UNDO;
  }

  @Override
  public boolean isRedoInProgress() {
    ClientState state = getClientState();
    if (state == null) {
      return false;
    }
    return state.myCurrentOperationState == OperationState.REDO;
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
    ApplicationManager.getApplication().assertIsWriteThread();

    Collection<DocumentReference> refs = getDocRefs(editor);
    return refs != null && isUndoOrRedoAvailable(refs, undo);
  }

  boolean isUndoOrRedoAvailable(@NotNull DocumentReference ref) {
    Set<DocumentReference> refs = Collections.singleton(ref);
    return isUndoOrRedoAvailable(refs, true) || isUndoOrRedoAvailable(refs, false);
  }

  /**
   * In case of global group blocking undo we can perform undo locally and separate undone changes from others stacks
   */
  public boolean splitGlobalCommand(@NotNull UndoRedo undoRedo) {
    var group = undoRedo.myUndoableGroup;
    Collection<DocumentReference> refs = undoRedo.getDocRefs();
    if (refs == null || refs.size() != 1) return false;
    var docRef = refs.iterator().next();

    var clientState = getClientState();
    if (clientState == null) return false;
    var stackHolder = getStackHolder(clientState, true);

    var stack = stackHolder.getStack(docRef);
    if (stack.getLast() == group) {
      var actions = separateLocalAndNonLocalActions(group.getActions(), docRef);
      if (actions.first.isEmpty()) return false;

      stack.removeLast();

      var replacingGroup = new UndoableGroup("Local " + group.getCommandName(),
                                             false,
                                             group.getCommandTimestamp(),
                                             group.getStateBefore(),
                                             group.getStateAfter(),
                                             // only action that changes file locally
                                             actions.first,
                                             stackHolder, getProject(), group.getConfirmationPolicy(), group.isTransparent(),
                                             group.isValid());
      stack.add(replacingGroup);

      var groupWithoutLocalChanges = new UndoableGroup(group.getCommandName(),
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
    var localActions = new SmartList<UndoableAction>();
    var nonLocalActions = new SmartList<UndoableAction>();
    for (UndoableAction action : actions) {
      var affectedDocuments = action.getAffectedDocuments();
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
  public void gatherGlobalCommand(@NotNull UndoRedo undoRedo) {
    var group = undoRedo.myUndoableGroup;
    UndoableGroup.UndoableGroupOriginalContext context = group.getGroupOriginalContext();
    if (context == null) return;

    Collection<DocumentReference> refs = undoRedo.getDocRefs();
    if (refs.size() > 1) return;
    var docRef = refs.iterator().next();

    var clientState = getClientState();
    if (clientState == null) return;
    var stackHolder = getStackHolder(clientState, true);
    var stack = stackHolder.getStack(docRef);
    if (stack.getLast() != group) return;

    var shouldGatherGroup = stackHolder.replaceOnStacks(context.getCurrentStackGroup(), context.getOriginalGroup());
    if (!shouldGatherGroup) return;

    stack.removeLast();
    stack.add(context.getOriginalGroup());
  }

  private boolean isUndoOrRedoAvailable(@NotNull Collection<? extends DocumentReference> refs, boolean isUndo) {
    ClientState state = getClientState();
    if (state == null) {
      return false;
    }
    if (isUndo && state.myMerger.isUndoAvailable(refs)) return true;
    UndoRedoStacksHolder stackHolder = getStackHolder(state, isUndo);
    return stackHolder.canBeUndoneOrRedone(refs);
  }

  private static Collection<DocumentReference> getDocRefs(@Nullable FileEditor editor) {
    if (editor instanceof TextEditor && ((TextEditor)editor).getEditor().isViewer()) {
      return null;
    }
    if (editor == null) {
      return Collections.emptyList();
    }
    return getDocumentReferences(editor);
  }

  static @NotNull Collection<DocumentReference> getDocumentReferences(@NotNull FileEditor editor) {
    ArrayList<DocumentReference> result = new ArrayList<>();

    if (editor instanceof DocumentReferenceProvider) {
      result.addAll(((DocumentReferenceProvider)editor).getDocumentReferences());
      return result;
    }

    Document[] documents = TextEditorProvider.getDocuments(editor);
    for (Document each : documents) {
      Document original = getOriginal(each);
      // KirillK : in AnAction.update we may have an editor with an invalid file
      VirtualFile f = FileDocumentManager.getInstance().getFile(each);
      if (f != null && !f.isValid()) continue;
      result.add(DocumentReferenceManager.getInstance().create(original));
    }
    return result;
  }

  private static @NotNull UndoRedoStacksHolder getStackHolder(@NotNull ClientState state, boolean isUndo) {
    return isUndo ? state.myUndoStacksHolder : state.myRedoStacksHolder;
  }

  @Override
  public @NotNull Pair<String, String> getUndoActionNameAndDescription(FileEditor editor) {
    return getUndoOrRedoActionNameAndDescription(editor, true);
  }

  @Override
  public @NotNull Pair<String, String> getRedoActionNameAndDescription(FileEditor editor) {
    return getUndoOrRedoActionNameAndDescription(editor, false);
  }

  private @NotNull Pair<@NlsActions.ActionText String, @NlsActions.ActionDescription String> getUndoOrRedoActionNameAndDescription(@Nullable FileEditor editor, boolean undo) {
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

  private @Nullable String doFormatAvailableUndoRedoAction(FileEditor editor, boolean isUndo) {
    ClientState state = getClientState();
    if (state == null) {
      return null;
    }
    Collection<DocumentReference> refs = getDocRefs(editor);
    if (refs == null) return null;
    if (isUndo && state.myMerger.isUndoAvailable(refs)) return state.myMerger.getCommandName();
    return getStackHolder(state, isUndo).getLastAction(refs).getCommandName();
  }

  @NotNull
  SharedUndoRedoStacksHolder getSharedUndoStacksHolder() {
    return mySharedUndoStacksHolder;
  }

  @NotNull
  SharedUndoRedoStacksHolder getSharedRedoStacksHolder() {
    return mySharedRedoStacksHolder;
  }

  @NotNull
  UndoRedoStacksHolder getUndoStacksHolder() {
    ClientState state = getClientState();
    LOG.assertTrue(state != null);
    return state.myUndoStacksHolder;
  }

  @NotNull
  UndoRedoStacksHolder getRedoStacksHolder() {
    ClientState state = getClientState();
    LOG.assertTrue(state != null);
    return state.myRedoStacksHolder;
  }

  int nextCommandTimestamp() {
    ClientState state = getClientState();
    LOG.assertTrue(state != null);
    return state.nextCommandTimestamp();
  }

  private static @NotNull Document getOriginal(@NotNull Document document) {
    Document result = document.getUserData(ORIGINAL_DOCUMENT);
    return result == null ? document : result;
  }

  static boolean isCopy(@NotNull Document d) {
    return d.getUserData(ORIGINAL_DOCUMENT) != null;
  }

  protected void compact() {
    ClientState state = getClientState();
    if (state != null && state.myCurrentOperationState == OperationState.NONE && state.myCommandTimestamp % COMMAND_TO_RUN_COMPACT == 0) {
      doCompact(state);
    }
  }

  private void doCompact(@NotNull ClientState state) {
    Collection<DocumentReference> refs = collectReferencesWithoutMergers(state);

    Collection<DocumentReference> openDocs = new HashSet<>();
    for (DocumentReference each : refs) {
      VirtualFile file = each.getFile();
      if (file == null) {
        Document document = each.getDocument();
        if (document != null && EditorFactory.getInstance().editors(document, myProject).findFirst().isPresent()) {
          openDocs.add(each);
        }
      }
      else {
        if (myProject != null && FileEditorManager.getInstance(myProject).isFileOpen(file)) {
          openDocs.add(each);
        }
      }
    }
    refs.removeAll(openDocs);

    if (refs.size() <= FREE_QUEUES_LIMIT) return;

    DocumentReference[] backSorted = refs.toArray(DocumentReference.EMPTY_ARRAY);
    Arrays.sort(backSorted, Comparator.comparingInt(ref -> getLastCommandTimestamp(state, ref)));

    for (int i = 0; i < backSorted.length - FREE_QUEUES_LIMIT; i++) {
      DocumentReference each = backSorted[i];
      if (getLastCommandTimestamp(state, each) + COMMANDS_TO_KEEP_LIVE_QUEUES > state.myCommandTimestamp) break;
      clearUndoRedoQueue(state, each);
    }
  }

  private static int getLastCommandTimestamp(@NotNull ClientState state, @NotNull DocumentReference ref) {
    return Math.max(state.myUndoStacksHolder.getLastCommandTimestamp(ref), state.myRedoStacksHolder.getLastCommandTimestamp(ref));
  }

  private static @NotNull Collection<DocumentReference> collectReferencesWithoutMergers(@NotNull ClientState state) {
    Set<DocumentReference> result = new HashSet<>();
    state.myUndoStacksHolder.collectAllAffectedDocuments(result);
    state.myRedoStacksHolder.collectAllAffectedDocuments(result);
    return result;
  }

  private void clearUndoRedoQueue(@NotNull ClientState state, @NotNull DocumentReference docRef) {
    state.myMerger.flushCurrentCommand();
    disposeCurrentMerger(state);

    Set<DocumentReference> set = Collections.singleton(docRef);
    state.myUndoStacksHolder.clearStacks(false, set);
    state.myRedoStacksHolder.clearStacks(false, set);
    mySharedUndoStacksHolder.trimStacks(set);
    mySharedRedoStacksHolder.trimStacks(set);
  }

  @TestOnly
  public void setEditorProvider(@NotNull CurrentEditorProvider p) {
    myEditorProvider = p;
  }

  @TestOnly
  public @NotNull CurrentEditorProvider getEditorProvider() {
    return myEditorProvider;
  }

  @TestOnly
  public void dropHistoryInTests() {
    ClientState state = getClientState();
    if (state == null) {
      return;
    }
    flushMergers();
    LOG.assertTrue(state.myCommandLevel == 0, state.myCommandLevel);

    state.myUndoStacksHolder.clearAllStacksInTests();
    state.myRedoStacksHolder.clearAllStacksInTests();
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
    ClientState state = getClientState();
    if (state == null) {
      return;
    }
    state.myMerger.flushCurrentCommand();
  }

  @TestOnly
  public void clearUndoRedoQueueInTests(@NotNull VirtualFile file) {
    ClientState state = getClientState();
    if (state == null) {
      return;
    }
    clearUndoRedoQueue(state, DocumentReferenceManager.getInstance().create(file));
  }

  @TestOnly
  public void clearUndoRedoQueueInTests(@NotNull Document document) {
    ClientState state = getClientState();
    if (state == null) {
      return;
    }
    clearUndoRedoQueue(state, DocumentReferenceManager.getInstance().create(document));
  }

  @ApiStatus.Internal
  public void clearDocumentReferences(@NotNull Document document) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    for (ClientState state : getAllClientStates()) {
      state.myUndoStacksHolder.clearDocumentReferences(document);
      state.myRedoStacksHolder.clearDocumentReferences(document);
      state.myMerger.clearDocumentReferences(document);
    }
    mySharedUndoStacksHolder.clearDocumentReferences(document);
    mySharedRedoStacksHolder.clearDocumentReferences(document);
  }

  @Override
  public String toString() {
    return "UndoManager for " + ObjectUtils.notNull(myProject, "application");
  }

  static final class MyCommandListener implements CommandListener {
    private boolean isStarted;
    private final Project project;
    private final UndoManagerImpl manager;

    @SuppressWarnings("unused")
    MyCommandListener(Project project) {
      this.project = project;
      manager = (UndoManagerImpl)project.getService(UndoManager.class);
    }

    @SuppressWarnings("unused")
    MyCommandListener() {
      project = null;
      manager = (UndoManagerImpl)ApplicationManager.getApplication().getService(UndoManager.class);
    }

    @Override
    public void commandStarted(@NotNull CommandEvent event) {
      if (!isStarted && (project == null || !project.isDisposed())) {
        manager.onCommandStarted(event.getProject(), event.getUndoConfirmationPolicy(), event.shouldRecordActionForOriginalDocument());
      }
    }

    @Override
    public void commandFinished(@NotNull CommandEvent event) {
      if (isStarted || (project != null && project.isDisposed())) {
        return;
      }
      manager.onCommandFinished(event.getProject(), event.getCommandName(), event.getCommandGroupId());
    }

    @Override
    public void undoTransparentActionStarted() {
      if ((project == null || !project.isDisposed()) && !manager.isInsideCommand()) {
        isStarted = true;
        manager.onCommandStarted(project, UndoConfirmationPolicy.DEFAULT, true);
      }
    }

    @Override
    public void undoTransparentActionFinished() {
      if (isStarted && (project == null || !project.isDisposed())) {
        isStarted = false;
        manager.onCommandFinished(project, "", null);
      }
    }
  }
}
