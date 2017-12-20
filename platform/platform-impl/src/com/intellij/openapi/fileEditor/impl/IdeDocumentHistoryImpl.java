/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.CommandMerger;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.ExternalChangeAction;
import com.intellij.testFramework.LightVirtualFile;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;

@State(name = "IdeDocumentHistory", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class IdeDocumentHistoryImpl extends IdeDocumentHistory implements ProjectComponent, Disposable, PersistentStateComponent<IdeDocumentHistoryImpl.RecentlyChangedFilesState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl");

  private static final int BACK_QUEUE_LIMIT = Registry.intValue("editor.navigation.history.stack.size");
  private static final int CHANGE_QUEUE_LIMIT = Registry.intValue("editor.navigation.history.stack.size");

  private final Project myProject;

  private final EditorFactory myEditorFactory;
  private FileDocumentManager myFileDocumentManager;
  private FileEditorManagerEx myEditorManager;
  private final VirtualFileManager myVfManager;
  private final CommandProcessor myCmdProcessor;
  private final ToolWindowManager myToolWindowManager;

  private final LinkedList<PlaceInfo> myBackPlaces = new LinkedList<>(); // LinkedList of PlaceInfo's
  private final LinkedList<PlaceInfo> myForwardPlaces = new LinkedList<>(); // LinkedList of PlaceInfo's
  private boolean myBackInProgress;
  private boolean myForwardInProgress;
  private Object myLastGroupId;

  // change's navigation
  private final LinkedList<PlaceInfo> myChangePlaces = new LinkedList<>(); // LinkedList of PlaceInfo's
  private int myStartIndex;
  private int myCurrentIndex;
  private PlaceInfo myCurrentChangePlace;

  private PlaceInfo myCommandStartPlace;
  private boolean myCurrentCommandIsNavigation;
  private boolean myCurrentCommandHasChanges;
  private final Set<VirtualFile> myChangedFilesInCurrentCommand = new THashSet<>();
  private boolean myCurrentCommandHasMoves;

  private final CommandListener myCommandListener = new CommandListener() {
    @Override
    public void commandStarted(CommandEvent event) {
      onCommandStarted();
    }

    @Override
    public void commandFinished(CommandEvent event) {
      onCommandFinished(event.getCommandGroupId());
    }
  };

  private RecentlyChangedFilesState myRecentlyChangedFiles = new RecentlyChangedFilesState();

  public IdeDocumentHistoryImpl(@NotNull Project project,
                                @NotNull EditorFactory editorFactory,
                                @NotNull FileEditorManager editorManager,
                                @NotNull VirtualFileManager vfManager,
                                @NotNull CommandProcessor cmdProcessor,
                                @NotNull ToolWindowManager toolWindowManager) {
    myProject = project;
    myEditorFactory = editorFactory;
    myEditorManager = (FileEditorManagerEx)editorManager;
    myVfManager = vfManager;
    myCmdProcessor = cmdProcessor;
    myToolWindowManager = toolWindowManager;
  }

  @Override
  public final void projectOpened() {
    myEditorManager = (FileEditorManagerEx)FileEditorManager.getInstance(myProject);
    EditorEventMulticaster eventMulticaster = myEditorFactory.getEventMulticaster();

    eventMulticaster.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent e) {
        onDocumentChanged(e);
      }
    }, myProject);

    eventMulticaster.addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(CaretEvent e) {
        onCaretPositionChanged(e);
      }
    }, myProject);

    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent e) {
        onSelectionChanged();
      }
    });

    VirtualFileListener fileListener = new VirtualFileListener() {
      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        onFileDeleted();
      }
    };
    myVfManager.addVirtualFileListener(fileListener,myProject);
    myCmdProcessor.addCommandListener(myCommandListener,myProject);
  }

  public static class RecentlyChangedFilesState {
    // don't make it private, see: IDEA-130363 Recently Edited Files list should survive restart
    @SuppressWarnings("WeakerAccess") public List<String> CHANGED_PATHS = new ArrayList<>();

    public void register(VirtualFile file) {
      final String path = file.getPath();
      CHANGED_PATHS.remove(path);
      CHANGED_PATHS.add(path);
      trimToSize();
    }

    private void trimToSize(){
      final int limit = UISettings.getInstance().getRecentFilesLimit() + 1;
      while(CHANGED_PATHS.size()>limit){
        CHANGED_PATHS.remove(0);
      }
    }
  }

  @Override
  public RecentlyChangedFilesState getState() {
    return myRecentlyChangedFiles;
  }

  @Override
  public void loadState(RecentlyChangedFilesState state) {
    myRecentlyChangedFiles = state;
  }

  final void onFileDeleted() {
    removeInvalidFilesFromStacks();
  }

  public final void onSelectionChanged() {
    myCurrentCommandIsNavigation = true;
    myCurrentCommandHasMoves = true;
  }

  private void onCaretPositionChanged(CaretEvent e) {
    if (e.getOldPosition().line == e.getNewPosition().line) return;
    Document document = e.getEditor().getDocument();
    if (getFileDocumentManager().getFile(document) != null) {
      myCurrentCommandHasMoves = true;
    }
  }

  private void onDocumentChanged(DocumentEvent e) {
    Document document = e.getDocument();
    final VirtualFile file = getFileDocumentManager().getFile(document);
    if (file != null && !(file instanceof LightVirtualFile) && !ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction.class)) {
      if (!ApplicationManager.getApplication().isDispatchThread()) LOG.error("Document update for physical file not in EDT: " + file);
      myCurrentCommandHasChanges = true;
      myChangedFilesInCurrentCommand.add(file);
    }
  }

  final void onCommandStarted() {
    myCommandStartPlace = getCurrentPlaceInfo();
    myCurrentCommandIsNavigation = false;
    myCurrentCommandHasChanges = false;
    myCurrentCommandHasMoves = false;
    myChangedFilesInCurrentCommand.clear();
  }

  private PlaceInfo getCurrentPlaceInfo() {
    final Pair<FileEditor,FileEditorProvider> selectedEditorWithProvider = getSelectedEditor();
    if (selectedEditorWithProvider != null) {
      return createPlaceInfo(selectedEditorWithProvider.getFirst (), selectedEditorWithProvider.getSecond ());
    }
    return null;
  }

  final void onCommandFinished(Object commandGroupId) {
    if (myCommandStartPlace != null) {
      if (myCurrentCommandIsNavigation && myCurrentCommandHasMoves) {
        if (!myBackInProgress) {
          if (!CommandMerger.canMergeGroup(commandGroupId, myLastGroupId)) {
            putLastOrMerge(myBackPlaces, myCommandStartPlace, BACK_QUEUE_LIMIT);
          }
          if (!myForwardInProgress) {
            myForwardPlaces.clear();
          }
        }
        removeInvalidFilesFromStacks();
      }
    }
    myLastGroupId = commandGroupId;

    if (myCurrentCommandHasChanges) {
      setCurrentChangePlace();
    }
    else if (myCurrentCommandHasMoves) {
      pushCurrentChangePlace();
    }
  }

  @Override
  public final void includeCurrentCommandAsNavigation() {
    myCurrentCommandIsNavigation = true;
  }

  @Override
  public final void includeCurrentPlaceAsChangePlace() {
    setCurrentChangePlace();
    pushCurrentChangePlace();
  }

  private void setCurrentChangePlace() {
    final PlaceInfo placeInfo = getCurrentPlaceInfo();
    if (placeInfo == null) {
      return;
    }

    final VirtualFile file = placeInfo.getFile();
    if (myChangedFilesInCurrentCommand.contains(file)) {
      myRecentlyChangedFiles.register(file);

      myCurrentChangePlace = placeInfo;
      if (!myChangePlaces.isEmpty()) {
        final PlaceInfo lastInfo = myChangePlaces.getLast();
        if (isSame(placeInfo, lastInfo)) {
          myChangePlaces.removeLast();
        }
      }
      myCurrentIndex = myStartIndex + myChangePlaces.size();
    }
  }

  private void pushCurrentChangePlace() {
    if (myCurrentChangePlace != null) {
      myChangePlaces.add(myCurrentChangePlace);
      if (myChangePlaces.size() > CHANGE_QUEUE_LIMIT) {
        myChangePlaces.removeFirst();
        myStartIndex++;
      }
      myCurrentChangePlace = null;
    }
    myCurrentIndex = myStartIndex + myChangePlaces.size();
  }

  @Override
  public VirtualFile[] getChangedFiles() {
    List<VirtualFile> files = new ArrayList<>();

    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final List<String> paths = myRecentlyChangedFiles.CHANGED_PATHS;
    for (String path : paths) {
      final VirtualFile file = lfs.findFileByPath(path);
      if (file != null) {
        files.add(file);
      }
    }

    return VfsUtilCore.toVirtualFileArray(files);
  }

  public boolean isRecentlyChanged(@NotNull VirtualFile file) {
    return myRecentlyChangedFiles.CHANGED_PATHS.contains(file.getPath());
  }

  @Override
  public final void clearHistory() {
    myBackPlaces.clear();
    myForwardPlaces.clear();
    myChangePlaces.clear();

    myLastGroupId = null;

    myStartIndex = 0;
    myCurrentIndex = 0;
    myCurrentChangePlace = null;
    myCommandStartPlace = null;
  }

  @Override
  public final void back() {
    removeInvalidFilesFromStacks();
    if (myBackPlaces.isEmpty()) return;
    final PlaceInfo info = myBackPlaces.removeLast();

    PlaceInfo current = getCurrentPlaceInfo();
    if (current != null) myForwardPlaces.add(current);

    myBackInProgress = true;
    try {
      executeCommand(() -> gotoPlaceInfo(info), "", null);
    }
    finally {
      myBackInProgress = false;
    }
  }

  @Override
  public final void forward() {
    removeInvalidFilesFromStacks();

    final PlaceInfo target = getTargetForwardInfo();
    if (target == null) return;

    myForwardInProgress = true;
    try {
      executeCommand(() -> gotoPlaceInfo(target), "", null);
    } finally {
      myForwardInProgress = false;
    }
  }

  private PlaceInfo getTargetForwardInfo() {
    if (myForwardPlaces.isEmpty()) return null;

    PlaceInfo target = myForwardPlaces.removeLast();
    PlaceInfo current = getCurrentPlaceInfo();

    while (!myForwardPlaces.isEmpty()) {
      if (current != null && isSame(current, target)) {
        target = myForwardPlaces.removeLast();
      }
      else {
        break;
      }
    }
    return target;
  }

  @Override
  public final boolean isBackAvailable() {
    return !myBackPlaces.isEmpty();
  }

  @Override
  public final boolean isForwardAvailable() {
    return !myForwardPlaces.isEmpty();
  }

  @Override
  public final void navigatePreviousChange() {
    removeInvalidFilesFromStacks();
    if (myCurrentIndex == myStartIndex) return;
    int index = myCurrentIndex - 1;
    final PlaceInfo info = myChangePlaces.get(index - myStartIndex);

    executeCommand(() -> gotoPlaceInfo(info), "", null);
    myCurrentIndex = index;
  }

  @Override
  public final boolean isNavigatePreviousChangeAvailable() {
    return myCurrentIndex > myStartIndex;
  }

  private void removeInvalidFilesFromStacks() {
    removeInvalidFilesFrom(myBackPlaces);

    removeInvalidFilesFrom(myForwardPlaces);
    if (removeInvalidFilesFrom(myChangePlaces)) {
      myCurrentIndex = myStartIndex + myChangePlaces.size();
    }
  }

  @Override
  public void navigateNextChange() {
    removeInvalidFilesFromStacks();
    if (myCurrentIndex >= myStartIndex + myChangePlaces.size() - 1) return;
    int index = myCurrentIndex + 1;
    final PlaceInfo info = myChangePlaces.get(index - myStartIndex);

    executeCommand(() -> gotoPlaceInfo(info), "", null);
    myCurrentIndex = index;
  }

  @Override
  public boolean isNavigateNextChangeAvailable() {
    return myCurrentIndex < myStartIndex + myChangePlaces.size() - 1;
  }

  private static boolean removeInvalidFilesFrom(@NotNull List<PlaceInfo> backPlaces) {
    boolean removed = false;
    for (Iterator<PlaceInfo> iterator = backPlaces.iterator(); iterator.hasNext();) {
      PlaceInfo info = iterator.next();
      final VirtualFile file = info.myFile;
      if (!file.isValid()) {
        iterator.remove();
        removed = true;
      }
    }

    return removed;
  }

  private void gotoPlaceInfo(@NotNull PlaceInfo info) { // TODO: Msk
    final boolean wasActive = myToolWindowManager.isEditorComponentActive();
    EditorWindow wnd = info.getWindow();
    final Pair<FileEditor[],FileEditorProvider[]> editorsWithProviders = wnd != null && wnd.isValid()
                           ? myEditorManager.openFileWithProviders(info.getFile(), wasActive, wnd)
                           : myEditorManager.openFileWithProviders(info.getFile(), wasActive, false);

    myEditorManager.setSelectedEditor(info.getFile(), info.getEditorTypeId());

    final FileEditor[] editors = editorsWithProviders.getFirst();
    final FileEditorProvider[] providers = editorsWithProviders.getSecond();
    for (int i = 0; i < editors.length; i++) {
      String typeId = providers [i].getEditorTypeId();
      if (typeId.equals(info.getEditorTypeId())) {
        editors[i].setState(info.getNavigationState());
      }
    }
  }

  /**
   * @return currently selected FileEditor or null.
   */
  protected Pair<FileEditor,FileEditorProvider> getSelectedEditor() {
    VirtualFile file = myEditorManager.getCurrentFile();
    return file != null ? myEditorManager.getSelectedEditorWithProvider(file) : null;
  }

  private PlaceInfo createPlaceInfo(@NotNull final FileEditor fileEditor, final FileEditorProvider fileProvider) {
    if (!fileEditor.isValid()) return null;

    final VirtualFile file = myEditorManager.getFile(fileEditor);
    LOG.assertTrue(file != null);

    final FileEditorState state = fileEditor.getState(FileEditorStateLevel.NAVIGATION);

    return new PlaceInfo(file, state, fileProvider.getEditorTypeId(), myEditorManager.getCurrentWindow());
  }


  @Override
  @NotNull
  public final String getComponentName() {
    return "IdeDocumentHistory";
  }

  private static void putLastOrMerge(@NotNull LinkedList<PlaceInfo> list, @NotNull PlaceInfo next, int limitSizeLimit) {
    if (!list.isEmpty()) {
      PlaceInfo prev = list.getLast();
      if (isSame(prev, next)) {
        list.removeLast();
      }
    }

    list.add(next);
    if (list.size() > limitSizeLimit) {
      list.removeFirst();
    }
  }

  private FileDocumentManager getFileDocumentManager() {
    if (myFileDocumentManager == null) {
      myFileDocumentManager = FileDocumentManager.getInstance();
    }
    return myFileDocumentManager;
  }

  private static final class PlaceInfo {
    private final VirtualFile myFile;
    private final FileEditorState myNavigationState;
    private final String myEditorTypeId;
    private final Reference<EditorWindow> myWindow;

    PlaceInfo(@NotNull VirtualFile file,
              @NotNull FileEditorState navigationState,
              @NotNull String editorTypeId,
              @Nullable EditorWindow window) {
      myNavigationState = navigationState;
      myFile = file;
      myEditorTypeId = editorTypeId;
      myWindow = new WeakReference<>(window);
    }

    public EditorWindow getWindow() {
      return myWindow.get();
    }

    @NotNull
    private FileEditorState getNavigationState() {
      return myNavigationState;
    }

    @NotNull
    public VirtualFile getFile() {
      return myFile;
    }

    @NotNull
    public String getEditorTypeId() {
      return myEditorTypeId;
    }

    @Override
    public String toString() {
      return getFile().getName() + " " + getNavigationState();
    }
  }

  @NotNull
  @TestOnly
  List<PlaceInfo> getBackPlaces() {
    return myBackPlaces;
  }

  @Override
  public final void dispose() {
    myLastGroupId = null;
  }

  protected void executeCommand(Runnable runnable, String name, Object groupId) {
    myCmdProcessor.executeCommand(myProject, runnable, name, groupId);
  }

  private static boolean isSame(@NotNull PlaceInfo first, @NotNull PlaceInfo second) {
    if (first.getFile().equals(second.getFile())) {
      FileEditorState firstState = first.getNavigationState();
      FileEditorState secondState = second.getNavigationState();
      return firstState.equals(secondState) || firstState.canBeMergedWith(secondState, FileEditorStateLevel.NAVIGATION);
    }

    return false;
  }
}
