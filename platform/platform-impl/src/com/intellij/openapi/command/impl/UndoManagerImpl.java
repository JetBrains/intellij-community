/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.DataManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.*;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.ExternalChangeAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.*;
import java.util.List;

public class UndoManagerImpl extends UndoManager implements ProjectComponent, ApplicationComponent, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.impl.UndoManagerImpl");

  private static final int COMMANDS_TO_KEEP_LIVE_QUEUES = 100;
  private static final int COMMAND_TO_RUN_COMPACT = 20;
  private static final int FREE_QUEUES_LIMIT = 30;

  @Nullable private final ProjectEx myProject;
  private final CommandProcessor myCommandProcessor;
  private final StartupManager myStartupManager;

  private UndoProvider[] myUndoProviders;
  private CurrentEditorProvider myEditorProvider;

  private final UndoRedoStacksHolder myUndoStacksHolder = new UndoRedoStacksHolder(true);
  private final UndoRedoStacksHolder myRedoStacksHolder = new UndoRedoStacksHolder(false);

  private final CommandMerger myMerger;

  private CommandMerger myCurrentMerger;
  private Project myCurrentActionProject = DummyProject.getInstance();

  private int myCommandTimestamp = 1;

  private int myCommandLevel;
  private static final int NONE = 0;
  private static final int UNDO = 1;
  private static final int REDO = 2;
  private int myCurrentOperationState = NONE;

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

  public UndoManagerImpl(Application application, CommandProcessor commandProcessor) {
    this(application, null, commandProcessor, null);
  }

  public UndoManagerImpl(Application application,
                         @Nullable ProjectEx project,
                         CommandProcessor commandProcessor,
                         StartupManager startupManager) {
    myProject = project;
    myCommandProcessor = commandProcessor;
    myStartupManager = startupManager;

    init(application);

    myMerger = new CommandMerger(this);
  }

  private void init(@NotNull Application application) {
    if (myProject == null || application.isUnitTestMode() && !myProject.isDefault()) {
      initialize();
    }
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "UndoManager";
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void projectOpened() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      initialize();
    }
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void dispose() {
  }

  private void initialize() {
    if (myProject == null) {
      runStartupActivity();
    }
    else {
      myStartupManager.registerStartupActivity(() -> runStartupActivity());
    }
  }

  private void runStartupActivity() {
    myEditorProvider = new FocusBasedCurrentEditorProvider();
    CommandListener commandListener = new CommandAdapter() {
      private boolean myStarted;

      @Override
      public void commandStarted(CommandEvent event) {
        onCommandStarted(event.getProject(), event.getUndoConfirmationPolicy());
      }

      @Override
      public void commandFinished(CommandEvent event) {
        onCommandFinished(event.getProject(), event.getCommandName(), event.getCommandGroupId());
      }

      @Override
      public void undoTransparentActionStarted() {
        if (!isInsideCommand()) {
          myStarted = true;
          onCommandStarted(myProject, UndoConfirmationPolicy.DEFAULT);
        }
      }

      @Override
      public void undoTransparentActionFinished() {
        if (myStarted) {
          myStarted = false;
          onCommandFinished(myProject, "", null);
        }
      }
    };
    myCommandProcessor.addCommandListener(commandListener, this);

    Disposer.register(this, new DocumentUndoProvider(myProject));

    myUndoProviders = myProject == null
                      ? Extensions.getExtensions(UndoProvider.EP_NAME)
                      : Extensions.getExtensions(UndoProvider.PROJECT_EP_NAME, myProject);
    for (UndoProvider undoProvider : myUndoProviders) {
      if (undoProvider instanceof Disposable) {
        Disposer.register(this, (Disposable)undoProvider);
      }
    }
  }

  public boolean isActive() {
    return Comparing.equal(myProject, myCurrentActionProject);
  }

  private boolean isInsideCommand() {
    return myCommandLevel > 0;
  }

  private void onCommandStarted(final Project project, UndoConfirmationPolicy undoConfirmationPolicy) {
    if (myCommandLevel == 0) {
      for (UndoProvider undoProvider : myUndoProviders) {
        undoProvider.commandStarted(project);
      }
      myCurrentActionProject = project;
    }

    commandStarted(undoConfirmationPolicy, myProject == project);

    LOG.assertTrue(myCommandLevel == 0 || !(myCurrentActionProject instanceof DummyProject));
  }

  private void onCommandFinished(final Project project, final String commandName, final Object commandGroupId) {
    commandFinished(commandName, commandGroupId);
    if (myCommandLevel == 0) {
      for (UndoProvider undoProvider : myUndoProviders) {
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

    if (myProject != null && myCurrentMerger.hasActions() && !myCurrentMerger.isTransparent() && myCurrentMerger.isPhysical()) {
      addFocusedDocumentAsAffected();
    }
    myOriginatorReference = null;

    myCurrentMerger.setAfterState(getCurrentState());
    myMerger.commandFinished(commandName, groupId, myCurrentMerger);

    disposeCurrentMerger();
  }

  private void addFocusedDocumentAsAffected() {
    if (myOriginatorReference == null || myCurrentMerger.hasChangesOf(myOriginatorReference, true)) return;

    final DocumentReference[] refs = {myOriginatorReference};
    myCurrentMerger.addAction(new BasicUndoableAction() {
      @Override
      public void undo() throws UnexpectedUndoException {
      }

      @Override
      public void redo() throws UnexpectedUndoException {
      }

      @Override
      public DocumentReference[] getAffectedDocuments() {
        return refs;
      }
    });
  }

  private EditorAndState getCurrentState() {
    FileEditor editor = myEditorProvider.getCurrentEditor();
    if (editor == null) {
      return null;
    }
    if (Registry.is("editor.new.rendering") && !editor.isValid()) {
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
  public void nonundoableActionPerformed(@NotNull final DocumentReference ref, final boolean isGlobal) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    undoableActionPerformed(new NonUndoableAction(ref, isGlobal));
  }

  @Override
  public void undoableActionPerformed(@NotNull UndoableAction action) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myCurrentOperationState != NONE) return;

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

  void addAffectedDocuments(@NotNull Document... docs) {
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

  public void addAffectedFiles(@NotNull VirtualFile... files) {
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
    ApplicationManager.getApplication().assertIsDispatchThread();
    myMerger.invalidateActionsFor(ref);
    if (myCurrentMerger != null) myCurrentMerger.invalidateActionsFor(ref);
    myUndoStacksHolder.invalidateActionsFor(ref);
    myRedoStacksHolder.invalidateActionsFor(ref);
  }

  @Override
  public void undo(@Nullable FileEditor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(isUndoAvailable(editor));
    undoOrRedo(editor, true);
  }

  @Override
  public void redo(@Nullable FileEditor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(isRedoAvailable(editor));
    undoOrRedo(editor, false);
  }

  private void undoOrRedo(final FileEditor editor, final boolean isUndo) {
    myCurrentOperationState = isUndo ? UNDO : REDO;

    final RuntimeException[] exception = new RuntimeException[1];
    Runnable executeUndoOrRedoAction = () -> {
      try {
        if (myProject != null) {
          PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        }
        CopyPasteManager.getInstance().stopKillRings();
        myMerger.undoOrRedo(editor, isUndo);
      }
      catch (RuntimeException ex) {
        exception[0] = ex;
      }
      finally {
        myCurrentOperationState = NONE;
      }
    };

    String name = getUndoOrRedoActionNameAndDescription(editor, isUndoInProgress()).second;
    CommandProcessor.getInstance()
      .executeCommand(myProject, executeUndoOrRedoAction, name, null, myMerger.getUndoConfirmationPolicy());
    if (exception[0] != null) throw exception[0];
  }

  @Override
  public boolean isUndoInProgress() {
    return myCurrentOperationState == UNDO;
  }

  @Override
  public boolean isRedoInProgress() {
    return myCurrentOperationState == REDO;
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
    ApplicationManager.getApplication().assertIsDispatchThread();

    Collection<DocumentReference> refs = getDocRefs(editor);
    return refs != null && isUndoOrRedoAvailable(refs, undo);
  }

  boolean isUndoOrRedoAvailable(@NotNull DocumentReference ref) {
    Set<DocumentReference> refs = Collections.singleton(ref);
    return isUndoOrRedoAvailable(refs, true) || isUndoOrRedoAvailable(refs, false);
  }

  private boolean isUndoOrRedoAvailable(@NotNull Collection<DocumentReference> refs, boolean isUndo) {
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

  @NotNull
  static Set<DocumentReference> getDocumentReferences(@NotNull FileEditor editor) {
    Set<DocumentReference> result = new THashSet<>();

    if (editor instanceof DocumentReferenceProvider) {
      result.addAll(((DocumentReferenceProvider)editor).getDocumentReferences());
      return result;
    }

    Document[] documents = TextEditorProvider.getDocuments(editor);
    if (documents != null) {
      for (Document each : documents) {
        Document original = getOriginal(each);
        // KirillK : in AnAction.update we may have an editor with an invalid file
        VirtualFile f = FileDocumentManager.getInstance().getFile(each);
        if (f != null && !f.isValid()) continue;
        result.add(DocumentReferenceManager.getInstance().create(original));
      }
    }
    return result;
  }

  @NotNull
  private UndoRedoStacksHolder getStackHolder(boolean isUndo) {
    return isUndo ? myUndoStacksHolder : myRedoStacksHolder;
  }

  @NotNull
  @Override
  public Pair<String, String> getUndoActionNameAndDescription(FileEditor editor) {
    return getUndoOrRedoActionNameAndDescription(editor, true);
  }

  @NotNull
  @Override
  public Pair<String, String> getRedoActionNameAndDescription(FileEditor editor) {
    return getUndoOrRedoActionNameAndDescription(editor, false);
  }

  @NotNull
  private Pair<String, String> getUndoOrRedoActionNameAndDescription(FileEditor editor, boolean undo) {
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

  @Nullable
  private String doFormatAvailableUndoRedoAction(FileEditor editor, boolean isUndo) {
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

  @NotNull
  private static Document getOriginal(@NotNull Document document) {
    Document result = document.getUserData(ORIGINAL_DOCUMENT);
    return result == null ? document : result;
  }

  static boolean isCopy(@NotNull Document d) {
    return d.getUserData(ORIGINAL_DOCUMENT) != null;
  }

  protected void compact() {
    if (myCurrentOperationState == NONE && myCommandTimestamp % COMMAND_TO_RUN_COMPACT == 0) {
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
        if (document != null && EditorFactory.getInstance().getEditors(document, myProject).length > 0) {
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

    DocumentReference[] backSorted = refs.toArray(new DocumentReference[refs.size()]);
    Arrays.sort(backSorted, (a, b) -> getLastCommandTimestamp(a) - getLastCommandTimestamp(b));

    for (int i = 0; i < backSorted.length - FREE_QUEUES_LIMIT; i++) {
      DocumentReference each = backSorted[i];
      if (getLastCommandTimestamp(each) + COMMANDS_TO_KEEP_LIVE_QUEUES > myCommandTimestamp) break;
      clearUndoRedoQueue(each);
    }
  }

  private int getLastCommandTimestamp(@NotNull DocumentReference ref) {
    return Math.max(myUndoStacksHolder.getLastCommandTimestamp(ref), myRedoStacksHolder.getLastCommandTimestamp(ref));
  }

  @NotNull
  private Collection<DocumentReference> collectReferencesWithoutMergers() {
    Set<DocumentReference> result = new THashSet<>();
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
  @NotNull
  public CurrentEditorProvider getEditorProvider() {
    return myEditorProvider;
  }

  @TestOnly
  public void dropHistoryInTests() {
    flushMergers();
    LOG.assertTrue(myCommandLevel == 0);

    myUndoStacksHolder.clearAllStacksInTests();
    myRedoStacksHolder.clearAllStacksInTests();
  }

  @TestOnly
  private void flushMergers() {
    // Run dummy command in order to flush all mergers...
    CommandProcessor.getInstance()
      .executeCommand(myProject, EmptyRunnable.getInstance(), CommonBundle.message("drop.undo.history.command.name"), null);
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
}
