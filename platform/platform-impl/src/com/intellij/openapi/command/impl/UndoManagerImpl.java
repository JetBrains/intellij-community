package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.*;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.FragmentContent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

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

  private final UndoRedoStacksHolder myUndoStacksHolder = new UndoRedoStacksHolder();
  private final UndoRedoStacksHolder myRedoStacksHolder = new UndoRedoStacksHolder();

  private CommandMerger myCurrentMerger;
  private CurrentEditorProvider myCurrentEditorProvider;

  private Project myCurrentActionProject = DummyProject.getInstance();
  private int myCommandTimestamp = 1;
  private final CommandProcessor myCommandProcessor;
  private final EditorFactory myEditorFactory;
  private final VirtualFileManager myVirtualFileManager;
  private final StartupManager myStartupManager;
  private UndoProvider[] myUndoProviders;

  public UndoManagerImpl(ProjectEx project,
                         Application application,
                         CommandProcessor commandProcessor,
                         EditorFactory editorFactory,
                         VirtualFileManager virtualFileManager,
                         StartupManager startupManager) {
    myProject = project;
    myCommandProcessor = commandProcessor;
    myEditorFactory = editorFactory;
    myVirtualFileManager = virtualFileManager;
    myStartupManager = startupManager;

    init(application);

    myMerger = new CommandMerger(this, myEditorFactory);
    Disposer.register(this, myMerger);
  }

  public UndoManagerImpl(Application application,
                         CommandProcessor commandProcessor,
                         EditorFactory editorFactory,
                         VirtualFileManager virtualFileManager) {
    this(null, application, commandProcessor, editorFactory, virtualFileManager, null);
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

    Disposer.register(this, new DocumentUndoProvider(myProject, myEditorFactory));

    myUndoProviders = myProject == null
                      ? Extensions.getExtensions(UndoProvider.EP_NAME)
                      : Extensions.getExtensions(UndoProvider.PROJECT_EP_NAME, myProject);
    for (UndoProvider undoProvider : myUndoProviders) {
      if (undoProvider instanceof Disposable) {
        Disposer.register(this, (Disposable)undoProvider);
      }
    }

    //myVirtualFileManager.addVirtualFileListener(new MyFileListener(), this);
  }

  private void onCommandFinished(final Project project, final String commandName, final Object commandGroupId) {
    commandFinished(commandName, commandGroupId);
    if (myCommandLevel == 0) {
      for (UndoProvider undoProvider : myUndoProviders) {
        undoProvider.commandFinished(project);
      }
      myCurrentActionProject = DummyProject.getInstance();
      if (myProject == null) myMerger.clearDocumentRefs(); //do not leak document refs at app level
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

  public void dropHistory() {
    dropMergers();

    LOG.assertTrue(myCommandLevel == 0);

    myUndoStacksHolder.dropHistory();
    myRedoStacksHolder.dropHistory();
  }

  public void markCommandAsNonUndoable(@Nullable final VirtualFile affectedFile) {
    final DocumentReference[] refs = affectedFile == null
                                     ? null : new DocumentReference[]{DocumentReferenceManager.getInstance().create(affectedFile)};
    undoableActionPerformed(new NonUndoableAction() {
      public boolean shouldConfirmUndo() {
        return false;
      }

      public DocumentReference[] getAffectedDocuments() {
        return refs;
      }
    });
  }

  @Override
  public void markCurrentCommandAsComplex() {
    myCurrentMerger.markAsComplex();
  }

  public void invalidateAllComplexCommands() {
    dropMergers();

    myUndoStacksHolder.invalidateAllComplexCommands();
    myRedoStacksHolder.invalidateAllComplexCommands();
  }

  private void dropMergers() {
    // Run dummy command in order to drop all mergers...
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

  public void clearUndoRedoQueue(FileEditor editor) {
    LOG.assertTrue(myCommandLevel == 0);
    myMerger.flushCurrentCommand();
    disposeCurrentMerger();

    myUndoStacksHolder.clearEditorStack(editor);
    myRedoStacksHolder.clearEditorStack(editor);
  }

  public void clearUndoRedoQueue(Document document) {
    clearUndoRedoQueue(DocumentReferenceManager.getInstance().create(document));
  }

  private void clearUndoRedoQueue(DocumentReference docRef) {
    myMerger.flushCurrentCommand();
    disposeCurrentMerger();

    myUndoStacksHolder.clearFileStack(docRef);
    myRedoStacksHolder.clearFileStack(docRef);
  }

  public void clearUndoRedoQueue(VirtualFile file) {
    clearUndoRedoQueue(DocumentReferenceManager.getInstance().create(file));
  }

  public void compact() {
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

  private int getLastCommandTimestamp(DocumentReference ref) {
    return Math.max(myUndoStacksHolder.getLastCommandTimestamp(ref), myRedoStacksHolder.getLastCommandTimestamp(ref));
  }

  public void undoableActionPerformed(UndoableAction action) {
    if (myCurrentOperationState != NONE) return;

    if (myCommandLevel == 0) {
      LOG.assertTrue(action instanceof NonUndoableAction,
                     "Undoable actions allowed inside commands only (see com.intellij.openapi.command.CommandProcessor.executeCommand())");
      commandStarted(UndoConfirmationPolicy.DEFAULT);
      myCurrentMerger.add(action, false);
      commandFinished("", null);
      return;
    }

    myCurrentMerger.add(action, CommandProcessor.getInstance().isUndoTransparentActionInProgress());
  }

  public boolean isUndoInProgress() {
    return myCurrentOperationState == UNDO;
  }

  public boolean isRedoInProgress() {
    return myCurrentOperationState == REDO;
  }

  public void undo(@Nullable FileEditor editor) {
    LOG.assertTrue(isUndoAvailable(editor));
    myCurrentOperationState = UNDO;
    undoOrRedo(editor);
  }

  public void redo(@Nullable FileEditor editor) {
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

    CommandProcessor.getInstance()
      .executeCommand(myProject, executeUndoOrRedoAction, isUndoInProgress() ? CommonBundle.message("undo.command.name") : CommonBundle
        .message("redo.command.name"), null, myMerger.getUndoConfirmationPolicy());
    if (exception[0] != null) throw exception[0];
  }

  public boolean isUndoAvailable(@Nullable FileEditor editor) {
    return isUndoOrRedoAvailable(editor, myUndoStacksHolder, true);
  }

  public boolean isRedoAvailable(@Nullable FileEditor editor) {
    return isUndoOrRedoAvailable(editor, myRedoStacksHolder, false);
  }

  private boolean isUndoOrRedoAvailable(FileEditor editor, UndoRedoStacksHolder stackHolder, boolean shouldCheckMerger) {
    if (editor instanceof TextEditor) {
      Editor activeEditor = ((TextEditor)editor).getEditor();
      if (activeEditor.isViewer()) {
        return false;
      }
    }

    Set<DocumentReference> refs = getDocumentReferences(editor);

    if (refs.isEmpty()) {
      if (shouldCheckMerger) {
        if (myMerger != null && myMerger.isComplex() && !myMerger.isEmpty()) return true;
      }
      return !stackHolder.getGlobalStack().isEmpty();
    }

    for (DocumentReference each : refs) {
      if (shouldCheckMerger) {
        if (myMerger != null && (myMerger.hasChangesOf(each) || (myMerger.isComplex() && myMerger.getAffectedDocuments().isEmpty()))) {
          return true;
        }
      }
      if (stackHolder.hasUndoableActions(each)) return true;
    }
    return false;
  }

  public static Set<DocumentReference> getDocumentReferences(FileEditor editor) {
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
      myCurrentMerger = new CommandMerger(this, EditorFactory.getInstance());
      Disposer.register(this, myCurrentMerger);
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

  @TestOnly
  public void clearHistory() {
    if (myCurrentMerger != null) myCurrentMerger.flushCurrentCommand();
    if (myMerger != null) myMerger.flushCurrentCommand();
  }

  private void disposeCurrentMerger() {
    LOG.assertTrue(myCommandLevel == 0);
    if (myCurrentMerger != null) {
      Disposer.dispose(myCurrentMerger);
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

  public boolean documentWasChanged(DocumentReference ref) {
    return collectReferencesWithoutRedo().contains(ref);
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

  private Collection<DocumentReference> collectReferencesWithoutRedo() {
    return doCollectReferences(false, true);
  }

  private Collection<DocumentReference> collectReferencesWithoutMergers() {
    return doCollectReferences(true, false);
  }

  private Collection<DocumentReference> doCollectReferences(boolean collectRedo, boolean collectMergers) {
    Set<DocumentReference> result = new THashSet<DocumentReference>();
    result.addAll(myUndoStacksHolder.getAffectedDocuments());
    result.addAll(myUndoStacksHolder.getGlobalStackAffectedDocuments());
    if (collectRedo) {
      result.addAll(myRedoStacksHolder.getAffectedDocuments());
      result.addAll(myRedoStacksHolder.getGlobalStackAffectedDocuments());
    }
    if (collectMergers) {
      if (myMerger != null) result.addAll(myMerger.getAffectedDocuments());
      if (myCurrentMerger != null) result.addAll(myCurrentMerger.getAffectedDocuments());
    }
    return result;
  }
}
