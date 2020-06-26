// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.ide.DataManager;
import com.intellij.idea.ActionsBundle;
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
import com.intellij.openapi.fileEditor.impl.FocusBasedCurrentEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.ExternalChangeAction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.List;
import java.util.*;

// Android team doesn't want to use new mockito for now, so, class cannot be final
public final class UndoManagerImpl extends UndoManager {
  private static final Logger LOG = Logger.getInstance(UndoManagerImpl.class);

  @TestOnly
  public static boolean ourNeverAskUser;

  private static final int COMMANDS_TO_KEEP_LIVE_QUEUES = 100;
  private static final int COMMAND_TO_RUN_COMPACT = 20;
  private static final int FREE_QUEUES_LIMIT = 30;

  private final @Nullable ProjectEx myProject;

  private CurrentEditorProvider myEditorProvider;

  private final UndoRedoStacksHolder myUndoStacksHolder = new UndoRedoStacksHolder(true);
  private final UndoRedoStacksHolder myRedoStacksHolder = new UndoRedoStacksHolder(false);

  private final CommandMerger myMerger;

  private CommandMerger myCurrentMerger;
  private Project myCurrentActionProject = DummyProject.getInstance();

  private int myCommandTimestamp = 1;

  private int myCommandLevel;
  private enum OperationState { NONE, UNDO, REDO }
  private OperationState myCurrentOperationState = OperationState.NONE;

  private DocumentReference myOriginatorReference;

  public static boolean isRefresh() {
    return ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction.class);
  }

  public static int getGlobalUndoLimit() {
    return Registry.intValue("undo.globalUndoLimit");
  }

  public static int getDocumentUndoLimit() {
    return Registry.intValue("undo.documentUndoLimit");
  }

  private UndoManagerImpl(@Nullable ComponentManager componentManager) {
    myProject = componentManager instanceof ProjectEx ? (ProjectEx)componentManager : null;
    myMerger = new CommandMerger(this);

    if (myProject != null && myProject.isDefault()) {
      return;
    }

    myEditorProvider = new FocusBasedCurrentEditorProvider();

    MessageBus messageBus = myProject == null ? ApplicationManager.getApplication().getMessageBus() : myProject.getMessageBus();
    messageBus.connect().subscribe(CommandListener.TOPIC, new CommandListener() {
      private boolean myStarted;

      @Override
      public void commandStarted(@NotNull CommandEvent event) {
        if (myProject != null && myProject.isDisposed() || myStarted) {
          return;
        }
        onCommandStarted(event.getProject(), event.getUndoConfirmationPolicy(), event.shouldRecordActionForOriginalDocument());
      }

      @Override
      public void commandFinished(@NotNull CommandEvent event) {
        if (myProject != null && myProject.isDisposed() || myStarted) {
          return;
        }
        onCommandFinished(event.getProject(), event.getCommandName(), event.getCommandGroupId());
      }

      @Override
      public void undoTransparentActionStarted() {
        if (myProject != null && myProject.isDisposed()) {
          return;
        }
        if (!isInsideCommand()) {
          myStarted = true;
          onCommandStarted(myProject, UndoConfirmationPolicy.DEFAULT, true);
        }
      }

      @Override
      public void undoTransparentActionFinished() {
        if (myProject != null && myProject.isDisposed()) {
          return;
        }
        if (myStarted) {
          myStarted = false;
          onCommandFinished(myProject, "", null);
        }
      }
    });
  }

  public @Nullable Project getProject() {
    return myProject;
  }

  public boolean isActive() {
    return Comparing.equal(myProject, myCurrentActionProject) || myProject == null && myCurrentActionProject.isDefault();
  }

  @ApiStatus.Internal
  public boolean isInsideCommand() {
    return myCommandLevel > 0;
  }

  private @NotNull List<UndoProvider> getUndoProviders() {
    return myProject == null ? UndoProvider.EP_NAME.getExtensionList() : UndoProvider.PROJECT_EP_NAME.getExtensionList(myProject);
  }

  private void onCommandStarted(final Project project, UndoConfirmationPolicy undoConfirmationPolicy, boolean recordOriginalReference) {
    if (myCommandLevel == 0) {
      for (UndoProvider undoProvider : getUndoProviders()) {
        undoProvider.commandStarted(project);
      }
      myCurrentActionProject = project;
    }

    commandStarted(undoConfirmationPolicy, myProject == project && recordOriginalReference);

    LOG.assertTrue(myCommandLevel == 0 || !(myCurrentActionProject instanceof DummyProject));
  }

  private void onCommandFinished(final Project project, final String commandName, final Object commandGroupId) {
    commandFinished(commandName, commandGroupId);
    if (myCommandLevel == 0) {
      for (UndoProvider undoProvider : getUndoProviders()) {
        undoProvider.commandFinished(project);
      }
      myCurrentActionProject = DummyProject.getInstance();
    }
    LOG.assertTrue(myCommandLevel == 0 || !(myCurrentActionProject instanceof DummyProject));
  }

  private void commandStarted(UndoConfirmationPolicy undoConfirmationPolicy, boolean recordOriginalReference) {
    if (myCommandLevel == 0) {
      myCurrentMerger = new CommandMerger(this, CommandProcessor.getInstance().isUndoTransparentActionInProgress());

      if (recordOriginalReference && myProject != null) {
        Editor editor = null;
        final Application application = ApplicationManager.getApplication();
        if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
          editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext());
        }
        else {
          Component component = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);
          if (component != null) {
            editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext(component));
          }
        }

        if (editor != null) {
          Document document = editor.getDocument();
          VirtualFile file = FileDocumentManager.getInstance().getFile(document);
          if (file != null && file.isValid()) {
            myOriginatorReference = DocumentReferenceManager.getInstance().create(file);
          }
        }
      }
    }
    LOG.assertTrue(myCurrentMerger != null, String.valueOf(myCommandLevel));
    myCurrentMerger.setBeforeState(getCurrentState());
    myCurrentMerger.mergeUndoConfirmationPolicy(undoConfirmationPolicy);

    myCommandLevel++;
  }

  private void commandFinished(String commandName, Object groupId) {
    if (myCommandLevel == 0) return; // possible if command listener was added within command
    myCommandLevel--;
    if (myCommandLevel > 0) return;

    if (myProject != null && myCurrentMerger.hasActions() && !myCurrentMerger.isTransparent() && myCurrentMerger.isPhysical() &&
        myOriginatorReference != null) {
      addDocumentAsAffected(myOriginatorReference);
    }
    myOriginatorReference = null;

    myCurrentMerger.setAfterState(getCurrentState());
    myMerger.commandFinished(commandName, groupId, myCurrentMerger);

    disposeCurrentMerger();
  }

  public void addDocumentAsAffected(@NotNull Document document) {
    addDocumentAsAffected(DocumentReferenceManager.getInstance().create(document));
  }

  private void addDocumentAsAffected(@NotNull DocumentReference documentReference) {
    if (myCurrentMerger.hasChangesOf(documentReference, true)) {
      return;
    }

    DocumentReference[] refs = {documentReference};
    myCurrentMerger.addAction(new MentionOnlyUndoableAction(refs));
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

  private void disposeCurrentMerger() {
    LOG.assertTrue(myCommandLevel == 0);
    if (myCurrentMerger != null) {
      myCurrentMerger = null;
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
    ApplicationManager.getApplication().assertIsWriteThread();
    if (myProject != null && myProject.isDisposed() || myCurrentOperationState != OperationState.NONE) {
      return;
    }

    if (myCommandLevel == 0) {
      LOG.assertTrue(action instanceof NonUndoableAction,
                     "Undoable actions allowed inside commands only (see com.intellij.openapi.command.CommandProcessor.executeCommand())");
      commandStarted(UndoConfirmationPolicy.DEFAULT, false);
      myCurrentMerger.addAction(action);
      commandFinished("", null);
      return;
    }

    if (isRefresh()) myOriginatorReference = null;

    myCurrentMerger.addAction(action);
  }

  public void markCurrentCommandAsGlobal() {
    myCurrentMerger.markAsGlobal();
  }

  void addAffectedDocuments(Document @NotNull ... docs) {
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
    myCurrentMerger.addAdditionalAffectedDocuments(refs);
  }

  public void addAffectedFiles(VirtualFile @NotNull ... files) {
    if (!isInsideCommand()) {
      LOG.error("Must be called inside command");
      return;
    }
    List<DocumentReference> refs = new ArrayList<>(files.length);
    for (VirtualFile each : files) {
      refs.add(DocumentReferenceManager.getInstance().create(each));
    }
    myCurrentMerger.addAdditionalAffectedDocuments(refs);
  }

  public void invalidateActionsFor(@NotNull DocumentReference ref) {
    ApplicationManager.getApplication().assertIsWriteThread();
    myMerger.invalidateActionsFor(ref);
    if (myCurrentMerger != null) myCurrentMerger.invalidateActionsFor(ref);
    myUndoStacksHolder.invalidateActionsFor(ref);
    myRedoStacksHolder.invalidateActionsFor(ref);
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
    myCurrentOperationState = isUndo ? OperationState.UNDO : OperationState.REDO;
    try {
      final RuntimeException[] exception = new RuntimeException[1];
      Runnable executeUndoOrRedoAction = () -> {
        try {
          CopyPasteManager.getInstance().stopKillRings();
          myMerger.undoOrRedo(editor, isUndo);
        }
        catch (RuntimeException ex) {
          exception[0] = ex;
        }
      };
      String name = getUndoOrRedoActionNameAndDescription(editor, isUndoInProgress()).second;
      CommandProcessor.getInstance()
        .executeCommand(myProject, executeUndoOrRedoAction, name, null, myMerger.getUndoConfirmationPolicy());
      if (exception[0] != null) throw exception[0];
    }
    finally {
      myCurrentOperationState = OperationState.NONE;
    }
  }

  @Override
  public boolean isUndoInProgress() {
    return myCurrentOperationState == OperationState.UNDO;
  }

  @Override
  public boolean isRedoInProgress() {
    return myCurrentOperationState == OperationState.REDO;
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

  private boolean isUndoOrRedoAvailable(@NotNull Collection<? extends DocumentReference> refs, boolean isUndo) {
    if (isUndo && myMerger.isUndoAvailable(refs)) return true;
    UndoRedoStacksHolder stackHolder = getStackHolder(isUndo);
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

  static @NotNull Set<DocumentReference> getDocumentReferences(@NotNull FileEditor editor) {
    Set<DocumentReference> result = new HashSet<>();

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

  private @NotNull UndoRedoStacksHolder getStackHolder(boolean isUndo) {
    return isUndo ? myUndoStacksHolder : myRedoStacksHolder;
  }

  @Override
  public @NotNull Pair<String, String> getUndoActionNameAndDescription(FileEditor editor) {
    return getUndoOrRedoActionNameAndDescription(editor, true);
  }

  @Override
  public @NotNull Pair<String, String> getRedoActionNameAndDescription(FileEditor editor) {
    return getUndoOrRedoActionNameAndDescription(editor, false);
  }

  private @NotNull Pair<String, String> getUndoOrRedoActionNameAndDescription(@Nullable FileEditor editor, boolean undo) {
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
    Collection<DocumentReference> refs = getDocRefs(editor);
    if (refs == null) return null;
    if (isUndo && myMerger.isUndoAvailable(refs)) return myMerger.getCommandName();
    return getStackHolder(isUndo).getLastAction(refs).getCommandName();
  }

  @NotNull
  UndoRedoStacksHolder getUndoStacksHolder() {
    return myUndoStacksHolder;
  }

  @NotNull
  UndoRedoStacksHolder getRedoStacksHolder() {
    return myRedoStacksHolder;
  }

  int nextCommandTimestamp() {
    return ++myCommandTimestamp;
  }

  private static @NotNull Document getOriginal(@NotNull Document document) {
    Document result = document.getUserData(ORIGINAL_DOCUMENT);
    return result == null ? document : result;
  }

  static boolean isCopy(@NotNull Document d) {
    return d.getUserData(ORIGINAL_DOCUMENT) != null;
  }

  protected void compact() {
    if (myCurrentOperationState == OperationState.NONE && myCommandTimestamp % COMMAND_TO_RUN_COMPACT == 0) {
      doCompact();
    }
  }

  private void doCompact() {
    Collection<DocumentReference> refs = collectReferencesWithoutMergers();

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
    Arrays.sort(backSorted, Comparator.comparingInt(this::getLastCommandTimestamp));

    for (int i = 0; i < backSorted.length - FREE_QUEUES_LIMIT; i++) {
      DocumentReference each = backSorted[i];
      if (getLastCommandTimestamp(each) + COMMANDS_TO_KEEP_LIVE_QUEUES > myCommandTimestamp) break;
      clearUndoRedoQueue(each);
    }
  }

  private int getLastCommandTimestamp(@NotNull DocumentReference ref) {
    return Math.max(myUndoStacksHolder.getLastCommandTimestamp(ref), myRedoStacksHolder.getLastCommandTimestamp(ref));
  }

  private @NotNull Collection<DocumentReference> collectReferencesWithoutMergers() {
    Set<DocumentReference> result = new HashSet<>();
    myUndoStacksHolder.collectAllAffectedDocuments(result);
    myRedoStacksHolder.collectAllAffectedDocuments(result);
    return result;
  }

  private void clearUndoRedoQueue(@NotNull DocumentReference docRef) {
    myMerger.flushCurrentCommand();
    disposeCurrentMerger();

    myUndoStacksHolder.clearStacks(false, Collections.singleton(docRef));
    myRedoStacksHolder.clearStacks(false, Collections.singleton(docRef));
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
    flushMergers();
    LOG.assertTrue(myCommandLevel == 0, myCommandLevel);

    myUndoStacksHolder.clearAllStacksInTests();
    myRedoStacksHolder.clearAllStacksInTests();
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
    myMerger.flushCurrentCommand();
  }

  @TestOnly
  public void clearUndoRedoQueueInTests(@NotNull VirtualFile file) {
    clearUndoRedoQueue(DocumentReferenceManager.getInstance().create(file));
  }

  @TestOnly
  public void clearUndoRedoQueueInTests(@NotNull Document document) {
    clearUndoRedoQueue(DocumentReferenceManager.getInstance().create(document));
  }

  @Override
  public String toString() {
    return "UndoManager for " + ObjectUtils.notNull(myProject, "application");
  }
}
