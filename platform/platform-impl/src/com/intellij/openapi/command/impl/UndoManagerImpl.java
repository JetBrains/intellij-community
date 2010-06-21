/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.*;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.FragmentContent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class UndoManagerImpl extends UndoManager implements ProjectComponent, ApplicationComponent, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.impl.UndoManagerImpl");

  public static final int GLOBAL_UNDO_LIMIT = 10;
  public static final int LOCAL_UNDO_LIMIT = 100;
  private static final int COMMANDS_TO_KEEP_LIVE_QUEUES = 100;
  private static final int COMMAND_TO_RUN_COMPACT = 20;
  private static final int FREE_QUEUES_LIMIT = 30;

  private final ProjectEx myProject;

  private int myCommandLevel = 0;

  private static final int NONE = 0;
  private static final int UNDO = 1;
  private static final int REDO = 2;
  private int myCurrentOperationState = NONE;

  private final CommandMerger myMerger;

  private final UndoRedoStacksHolder myUndoStacksHolder = new UndoRedoStacksHolder(true);
  private final UndoRedoStacksHolder myRedoStacksHolder = new UndoRedoStacksHolder(false);

  private CommandMerger myCurrentMerger;
  private CurrentEditorProvider myCurrentEditorProvider;

  private Project myCurrentActionProject = DummyProject.getInstance();
  private int myCommandTimestamp = 1;
  private final CommandProcessor myCommandProcessor;
  private final StartupManager myStartupManager;
  private UndoProvider[] myUndoProviders;

  public UndoManagerImpl(Application application, CommandProcessor commandProcessor) {
    this(application, null, commandProcessor, null);
  }

  public UndoManagerImpl(Application application,
                         ProjectEx project,
                         CommandProcessor commandProcessor,
                         StartupManager startupManager) {
    myProject = project;
    myCommandProcessor = commandProcessor;
    myStartupManager = startupManager;

    init(application);

    myMerger = new CommandMerger(this);
  }

  private void init(Application application) {
    if (myProject == null || application.isUnitTestMode() && !myProject.isDefault()) {
      initialize();
    }
  }

  @NotNull
  public String getComponentName() {
    return "UndoManager";
  }

  public Project getProject() {
    return myProject;
  }

  public void initComponent() {
  }

  private void initialize() {
    if (myProject == null) {
      runStartupActivity();
    }
    else {
      myStartupManager.registerStartupActivity(new Runnable() {
        public void run() {
          runStartupActivity();
        }
      });
    }
  }

  private void runStartupActivity() {
    myCurrentEditorProvider = new FocusBasedCurrentEditorProvider();
    CommandListener commandListener = new CommandAdapter() {
      private boolean myFakeCommandStarted = false;

      public void commandStarted(CommandEvent event) {
        onCommandStarted(event.getProject(), event.getUndoConfirmationPolicy());
      }

      public void commandFinished(CommandEvent event) {
        onCommandFinished(event.getProject(), event.getCommandName(), event.getCommandGroupId());
      }

      public void undoTransparentActionStarted() {
        if (!isInsideCommand()) {
          myFakeCommandStarted = true;
          onCommandStarted(myProject, UndoConfirmationPolicy.DEFAULT);
        }
      }

      public void undoTransparentActionFinished() {
        if (myFakeCommandStarted) {
          myFakeCommandStarted = false;
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

  private void onCommandStarted(final Project project, UndoConfirmationPolicy undoConfirmationPolicy) {
    if (myCommandLevel == 0) {
      for (UndoProvider undoProvider : myUndoProviders) {
        undoProvider.commandStarted(project);
      }
      myCurrentActionProject = project;
    }

    commandStarted(undoConfirmationPolicy);

    LOG.assertTrue(myCommandLevel == 0 || !(myCurrentActionProject instanceof DummyProject));
  }

  @TestOnly
  public void dropHistoryInTests() {
    flushMergers();
    LOG.assertTrue(myCommandLevel == 0);

    myUndoStacksHolder.clearAllStacksInTests();
    myRedoStacksHolder.clearAllStacksInTests();
  }

  public void markCurrentCommandAsGlobal() {
    myCurrentMerger.markAsGlobal();
  }

  private void flushMergers() {
    // Run dummy command in order to flush all mergers...
    CommandProcessor.getInstance()
      .executeCommand(myProject, EmptyRunnable.getInstance(), CommonBundle.message("drop.undo.history.command.name"), null);
  }

  public void dispose() {
  }

  public void disposeComponent() {
  }

  public void setCurrentEditorProvider(CurrentEditorProvider p) {
    myCurrentEditorProvider = p;
  }

  public CurrentEditorProvider getCurrentEditorProvider() {
    return myCurrentEditorProvider;
  }

  public void projectOpened() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      initialize();
    }
  }

  public void projectClosed() {
  }

  @TestOnly
  public void flushCurrentCommandMerger() {
    myMerger.flushCurrentCommand();
  }

  private void clearUndoRedoQueue(DocumentReference docRef) {
    myMerger.flushCurrentCommand();
    disposeCurrentMerger();

    myUndoStacksHolder.clearStacks(false, Collections.singleton(docRef));
    myRedoStacksHolder.clearStacks(false, Collections.singleton(docRef));
  }

  @TestOnly
  public void clearUndoRedoQueueInTests(VirtualFile file) {
    clearUndoRedoQueue(DocumentReferenceManager.getInstance().create(file));
  }

  @TestOnly
  public void clearUndoRedoQueueInTests(Document document) {
    clearUndoRedoQueue(DocumentReferenceManager.getInstance().create(document));
  }

  protected void compact() {
    if (myCurrentOperationState == NONE && myCommandTimestamp % COMMAND_TO_RUN_COMPACT == 0) {
      doCompact();
    }
  }

  private void doCompact() {
    Collection<DocumentReference> refs = collectReferencesWithoutMergers();

    Collection<DocumentReference> openDocs = new HashSet<DocumentReference>();
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
    Arrays.sort(backSorted, new Comparator<DocumentReference>() {
      public int compare(DocumentReference a, DocumentReference b) {
        return getLastCommandTimestamp(a) - getLastCommandTimestamp(b);
      }
    });

    for (int i = 0; i < backSorted.length - FREE_QUEUES_LIMIT; i++) {
      DocumentReference each = backSorted[i];
      if (getLastCommandTimestamp(each) + COMMANDS_TO_KEEP_LIVE_QUEUES > myCommandTimestamp) break;
      clearUndoRedoQueue(each);
    }
  }

  private Collection<DocumentReference> collectReferencesWithoutMergers() {
    Set<DocumentReference> result = new THashSet<DocumentReference>();
    myUndoStacksHolder.collectAllAffectedDocuments(result);
    myRedoStacksHolder.collectAllAffectedDocuments(result);
    return result;
  }

  private int getLastCommandTimestamp(DocumentReference ref) {
    return Math.max(myUndoStacksHolder.getLastCommandTimestamp(ref), myRedoStacksHolder.getLastCommandTimestamp(ref));
  }

  public void undoableActionPerformed(UndoableAction action) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myCurrentOperationState != NONE) return;

    if (myCommandLevel == 0) {
      LOG.assertTrue(action instanceof NonUndoableAction,
                     "Undoable actions allowed inside commands only (see com.intellij.openapi.command.CommandProcessor.executeCommand())");
      commandStarted(UndoConfirmationPolicy.DEFAULT);
      myCurrentMerger.addAction(action, false);
      commandFinished("", null);
      return;
    }

    myCurrentMerger.addAction(action, CommandProcessor.getInstance().isUndoTransparentActionInProgress());
  }

  @Override
  public void nonundoableActionPerformed(final DocumentReference ref, final boolean isGlobal) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    undoableActionPerformed(new NonUndoableAction(ref, isGlobal));
  }

  public boolean isUndoInProgress() {
    return myCurrentOperationState == UNDO;
  }

  public boolean isRedoInProgress() {
    return myCurrentOperationState == REDO;
  }

  public void undo(@Nullable FileEditor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(isUndoAvailable(editor));

    myCurrentOperationState = UNDO;
    undoOrRedo(editor);
  }

  public void redo(@Nullable FileEditor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(isRedoAvailable(editor));

    myCurrentOperationState = REDO;
    undoOrRedo(editor);
  }

  private void undoOrRedo(final FileEditor editor) {
    final RuntimeException[] exception = new RuntimeException[1];
    Runnable executeUndoOrRedoAction = new Runnable() {
      public void run() {
        try {
          if (isUndoInProgress()) {
            myMerger.undoOrRedo(editor, true);
          }
          else {
            myMerger.undoOrRedo(editor, false);
          }
        }
        catch (RuntimeException ex) {
          exception[0] = ex;
        }
        finally {
          myCurrentOperationState = NONE;
        }
      }
    };

    String name = getUndoOrRedoActionNameAndDescription(editor, isUndoInProgress()).second;
    CommandProcessor.getInstance()
      .executeCommand(myProject, executeUndoOrRedoAction, name, null, myMerger.getUndoConfirmationPolicy());
    if (exception[0] != null) throw exception[0];
  }

  public boolean isUndoAvailable(@Nullable FileEditor editor) {
    return isUndoOrRedoAvailable(editor, true);
  }

  public boolean isRedoAvailable(@Nullable FileEditor editor) {
    return isUndoOrRedoAvailable(editor, false);
  }

  private boolean isUndoOrRedoAvailable(@Nullable FileEditor editor, boolean undo) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    Collection<DocumentReference> refs = getDocRefs(editor);
    if (refs == null) return false;
    return isUndoOrRedoAvailable(refs, undo);
  }

  private static Collection<DocumentReference> getDocRefs(FileEditor editor) {
    if (editor instanceof TextEditor && ((TextEditor)editor).getEditor().isViewer()) return null;
    return getDocumentReferences(editor);
  }

  public boolean isUndoOrRedoAvailable(DocumentReference ref) {
    Set<DocumentReference> refs = Collections.singleton(ref);
    return isUndoOrRedoAvailable(refs, true) || isUndoOrRedoAvailable(refs, false);
  }

  private boolean isUndoOrRedoAvailable(Collection<DocumentReference> refs, boolean isUndo) {
    if (isUndo && myMerger.isUndoAvailable(refs)) return true;
    UndoRedoStacksHolder stackHolder = getStackHolder(isUndo);
    return stackHolder.hasActions(refs);
  }

  private UndoRedoStacksHolder getStackHolder(boolean isUndo) {
    return isUndo ? myUndoStacksHolder : myRedoStacksHolder;
  }

  public Pair<String, String> getUndoActionNameAndDescription(FileEditor editor) {
    return getUndoOrRedoActionNameAndDescription(editor, true);
  }

  public Pair<String, String> getRedoActionNameAndDescription(FileEditor editor) {
    return getUndoOrRedoActionNameAndDescription(editor, false);
  }

  private Pair<String, String> getUndoOrRedoActionNameAndDescription(FileEditor editor, boolean undo) {
    String desc = isUndoOrRedoAvailable(editor, undo) ? doFormatAvailableUndoRedoAction(editor, undo) : null;
    if (desc == null) desc = "";
    String shortActionName = StringUtil.first(desc, 30, true);

    if (desc.length() == 0) {
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

  static Set<DocumentReference> getDocumentReferences(FileEditor editor) {
    Set<DocumentReference> result = new THashSet<DocumentReference>();
    Document[] documents = editor == null ? null : TextEditorProvider.getDocuments(editor);
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

  public boolean isActive() {
    return Comparing.equal(myProject, myCurrentActionProject);
  }

  private void commandStarted(UndoConfirmationPolicy undoConfirmationPolicy) {
    if (myCommandLevel == 0) {
      myCurrentMerger = new CommandMerger(this);
    }
    LOG.assertTrue(myCurrentMerger != null, String.valueOf(myCommandLevel));
    myCurrentMerger.setBeforeState(getCurrentState());
    myCurrentMerger.mergeUndoConfirmationPolicy(undoConfirmationPolicy);

    myCommandLevel++;
  }

  private EditorAndState getCurrentState() {
    FileEditor editor = myCurrentEditorProvider.getCurrentEditor();
    if (editor == null) {
      return null;
    }
    return new EditorAndState(editor, editor.getState(FileEditorStateLevel.UNDO));
  }

  private void commandFinished(String commandName, Object groupId) {
    if (myCommandLevel == 0) return; // possible if command listener was added within command
    myCommandLevel--;
    if (myCommandLevel > 0) return;
    myCurrentMerger.setAfterState(getCurrentState());
    myMerger.commandFinished(commandName, groupId, myCurrentMerger);

    disposeCurrentMerger();
  }

  private void disposeCurrentMerger() {
    LOG.assertTrue(myCommandLevel == 0);
    if (myCurrentMerger != null) {
      myCurrentMerger = null;
    }
  }

  public UndoRedoStacksHolder getUndoStacksHolder() {
    return myUndoStacksHolder;
  }

  public UndoRedoStacksHolder getRedoStacksHolder() {
    return myRedoStacksHolder;
  }

  public boolean isInsideCommand() {
    return myCommandLevel > 0;
  }

  public int nextCommandTimestamp() {
    return ++myCommandTimestamp;
  }

  static Document getOriginal(Document document) {
    Document result = document.getUserData(FragmentContent.ORIGINAL_DOCUMENT);
    return result == null ? document : result;
  }

  public static boolean isCopy(Document d) {
    return d.getUserData(FragmentContent.ORIGINAL_DOCUMENT) != null;
  }
}
