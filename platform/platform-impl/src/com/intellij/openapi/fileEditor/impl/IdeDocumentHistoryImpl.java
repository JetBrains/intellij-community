// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.InplaceCommentAppender;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.CommandMerger;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorEventListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.ExternalChangeAction;
import com.intellij.reference.SoftReference;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.io.*;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

@State(name = "IdeDocumentHistory", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE), reportStatistic = false)
public class IdeDocumentHistoryImpl extends IdeDocumentHistory
  implements Disposable, PersistentStateComponent<IdeDocumentHistoryImpl.RecentlyChangedFilesState> {
  private static final Logger LOG = Logger.getInstance(IdeDocumentHistoryImpl.class);

  private static final int BACK_QUEUE_LIMIT = Registry.intValue("editor.navigation.history.stack.size");
  private static final int CHANGE_QUEUE_LIMIT = Registry.intValue("editor.navigation.history.stack.size");

  private final Project project;

  private FileDocumentManager myFileDocumentManager;

  private final LinkedList<PlaceInfo> backPlaces = new LinkedList<>(); // LinkedList of PlaceInfo's
  private final LinkedList<PlaceInfo> forwardPlaces = new LinkedList<>(); // LinkedList of PlaceInfo's
  private boolean myBackInProgress;
  private boolean forwardInProgress;
  private Object myCurrentCommandGroupId;
  private Reference<Object> lastGroupId; // weak reference to avoid memory leaks when clients pass some exotic objects as commandId
  private boolean registeredBackPlaceInLastGroup;

  // change's navigation
  private final LinkedList<PlaceInfo> changePlaces = new LinkedList<>(); // LinkedList of PlaceInfo's
  private int currentIndex;

  private PlaceInfo commandStartPlace;
  private boolean currentCommandIsNavigation;
  private boolean currentCommandHasChanges;
  private final Set<VirtualFile> myChangedFilesInCurrentCommand = new HashSet<>();
  private boolean currentCommandHasMoves;
  private boolean reallyExcludeCurrentCommandFromNavigation;

  private final SynchronizedClearableLazy<PersistentHashMap<String, Long>> recentFileTimestampMap;

  private final RecentlyChangedFilesState state = new RecentlyChangedFilesState();

  public IdeDocumentHistoryImpl(@NotNull Project project) {
    this.project = project;

    MessageBusConnection busConnection = project.getMessageBus().connect(this);
    busConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFileDeleteEvent) {
            removeInvalidFilesFromStacks();
            return;
          }
        }
      }
    });
    busConnection.subscribe(CommandListener.TOPIC, new CommandListener() {
      @Override
      public void commandStarted(@NotNull CommandEvent event) {
        onCommandStarted(event.getCommandGroupId());
      }

      @Override
      public void commandFinished(@NotNull CommandEvent event) {
        onCommandFinished(event.getProject(), event.getCommandGroupId());
      }
    });

    EditorEventListener listener = new EditorEventListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        Document document = e.getDocument();
        VirtualFile file = getFileDocumentManager().getFile(document);
        if (file != null &&
            !(file instanceof LightVirtualFile) &&
            !ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction.class)) {
          ThreadingAssertions.assertEventDispatchThread();
          currentCommandHasChanges = true;
          myChangedFilesInCurrentCommand.add(file);
        }
      }

      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
        if (e.getOldPosition().line == e.getNewPosition().line) {
          return;
        }

        Document document = e.getEditor().getDocument();
        if (getFileDocumentManager().getFile(document) != null) {
          currentCommandHasMoves = true;
        }
      }
    };

    recentFileTimestampMap = new SynchronizedClearableLazy<>(() -> initRecentFilesTimestampMap(this.project));

    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
    multicaster.addDocumentListener(listener, this);
    multicaster.addCaretListener(listener, this);

    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull FileEditorProvider provider, @NotNull PluginDescriptor pluginDescriptor) {
        String editorTypeId = provider.getEditorTypeId();
        Predicate<PlaceInfo> clearStatePredicate = e -> editorTypeId.equals(e.getEditorTypeId());
        if (changePlaces.removeIf(clearStatePredicate)) {
          currentIndex = changePlaces.size();
        }
        backPlaces.removeIf(clearStatePredicate);
        forwardPlaces.removeIf(clearStatePredicate);
        if (commandStartPlace != null && commandStartPlace.getEditorTypeId().equals(editorTypeId)) {
          commandStartPlace = null;
        }
      }
    }, this);
  }

  protected FileEditorManagerEx getFileEditorManager() {
    return FileEditorManagerEx.Companion.getInstanceExIfCreated(project);
  }

  private static @NotNull PersistentHashMap<String, Long> initRecentFilesTimestampMap(@NotNull Project project) {
    Path file = ProjectUtil.getProjectCachePath(project, "recentFilesTimeStamps.dat");
    try {
      return IOUtil.openCleanOrResetBroken(() -> createMap(file), file);
    }
    catch (IOException e) {
      LOG.error("Cannot create PersistentHashMap in " + file, e);
      throw new RuntimeException(e);
    }
  }

  private static @NotNull PersistentHashMap<String, Long> createMap(@NotNull Path file) throws IOException {
    return new PersistentHashMap<>(file,
                                   EnumeratorStringDescriptor.INSTANCE,
                                   EnumeratorLongDescriptor.INSTANCE,
                                   256,
                                   0,
                                   new StorageLockContext());
  }

  private void registerViewed(@NotNull VirtualFile file) {
    if (ApplicationManager.getApplication().isUnitTestMode() || !UISettings.getInstance().getShowInplaceComments()) {
      return;
    }

    try {
      recentFileTimestampMap.getValue().put(file.getPath(), System.currentTimeMillis());
    }
    catch (IOException e) {
      LOG.info("Cannot put a timestamp from a persistent hash map", e);
    }
  }

  public static void appendTimestamp(@NotNull Project project,
                                     @NotNull InplaceCommentAppender appender,
                                     @NotNull VirtualFile file) {
    if (!UISettings.getInstance().getShowInplaceComments()) {
      return;
    }

    try {
      Long timestamp = ((IdeDocumentHistoryImpl)getInstance(project)).recentFileTimestampMap.getValue().get(file.getPath());
      if (timestamp != null) {
        appender.append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        appender.append(DateFormatUtil.formatPrettyDateTime(timestamp), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
      }
    }
    catch (IOException e) {
      LOG.info("Cannot get a timestamp from a persistent hash map", e);
    }
  }

  static final class RecentlyChangedFilesState {
    @XCollection(style = XCollection.Style.v2)
    public final List<String> changedPaths = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      return changedPaths.equals(((RecentlyChangedFilesState)o).changedPaths);
    }

    @Override
    public int hashCode() {
      return changedPaths.hashCode();
    }
  }

  @Override
  public RecentlyChangedFilesState getState() {
    synchronized (state) {
      RecentlyChangedFilesState stateSnapshot = new RecentlyChangedFilesState();
      stateSnapshot.changedPaths.addAll(state.changedPaths);
      return stateSnapshot;
    }
  }

  @Override
  public void loadState(@NotNull RecentlyChangedFilesState state) {
    synchronized (this.state) {
      this.state.changedPaths.clear();
      this.state.changedPaths.addAll(state.changedPaths);
    }
  }

  @Override
  public final void onSelectionChanged() {
    if (!reallyExcludeCurrentCommandFromNavigation) {
      currentCommandIsNavigation = true;
    }
    currentCommandHasMoves = true;
  }

  @Override
  public void reallyExcludeCurrentCommandAsNavigation() {
    reallyExcludeCurrentCommandFromNavigation = true;
    currentCommandIsNavigation = false;
  }

  final void onCommandStarted(Object commandGroupId) {
    myCurrentCommandGroupId = commandGroupId;
    commandStartPlace = getCurrentPlaceInfo();
    currentCommandIsNavigation = false;
    currentCommandHasChanges = false;
    currentCommandHasMoves = false;
    reallyExcludeCurrentCommandFromNavigation = false;
    myChangedFilesInCurrentCommand.clear();
  }

  private @Nullable PlaceInfo getCurrentPlaceInfo() {
    FileEditorWithProvider selectedEditorWithProvider = getSelectedEditor();
    if (selectedEditorWithProvider == null) {
      return null;
    }
    return createPlaceInfo(selectedEditorWithProvider.getFileEditor(), selectedEditorWithProvider.getProvider());
  }

  private static @Nullable PlaceInfo getPlaceInfoFromFocus(Project project) {
    FileEditor fileEditor = new FocusBasedCurrentEditorProvider().getCurrentEditor(project);
    if (fileEditor instanceof TextEditor && fileEditor.isValid()) {
      VirtualFile file = fileEditor.getFile();
      if (file != null) {
        return new PlaceInfo(file,
                             fileEditor.getState(FileEditorStateLevel.NAVIGATION),
                             TextEditorProvider.getInstance().getEditorTypeId(),
                             null, false,
                             getCaretPosition(fileEditor), System.currentTimeMillis());
      }
    }
    return null;
  }

  final void onCommandFinished(Project project, Object commandGroupId) {
    Object lastGroupId = SoftReference.dereference(this.lastGroupId);
    if (!CommandMerger.canMergeGroup(commandGroupId, lastGroupId)) registeredBackPlaceInLastGroup = false;
    if (commandGroupId != lastGroupId) {
      this.lastGroupId = commandGroupId == null ? null : new WeakReference<>(commandGroupId);
    }

    if (commandStartPlace != null && currentCommandIsNavigation && currentCommandHasMoves) {
      if (!myBackInProgress) {
        if (!registeredBackPlaceInLastGroup) {
          registeredBackPlaceInLastGroup = true;
          putLastOrMerge(commandStartPlace, BACK_QUEUE_LIMIT, false, commandGroupId);
          registerViewed(commandStartPlace.myFile);
        }
        if (!forwardInProgress) {
          forwardPlaces.clear();
        }
      }
      removeInvalidFilesFromStacks();
    }

    if (currentCommandHasChanges) {
      setCurrentChangePlace(project == this.project);
    }
    else if (currentCommandHasMoves) {
      currentIndex = changePlaces.size();
    }
  }

  @Override
  public final void includeCurrentCommandAsNavigation() {
    if (!reallyExcludeCurrentCommandFromNavigation) {
      currentCommandIsNavigation = true;
    }
  }

  @Override
  public void setCurrentCommandHasMoves() {
    currentCommandHasMoves = true;
  }

  @Override
  public final void includeCurrentPlaceAsChangePlace() {
    setCurrentChangePlace(false);
  }

  private void setCurrentChangePlace(boolean acceptPlaceFromFocus) {
    PlaceInfo placeInfo = getCurrentPlaceInfo();
    if (placeInfo != null && !myChangedFilesInCurrentCommand.contains(placeInfo.getFile())) {
      placeInfo = null;
    }
    if (placeInfo == null && acceptPlaceFromFocus) {
      placeInfo = getPlaceInfoFromFocus(project);
    }
    if (placeInfo != null && !myChangedFilesInCurrentCommand.contains(placeInfo.getFile())) {
      placeInfo = null;
    }
    if (placeInfo == null) {
      return;
    }

    int limit = UISettings.getInstance().getRecentFilesLimit() + 1;
    synchronized (state) {
      String path = placeInfo.getFile().getPath();
      List<String> changedPaths = state.changedPaths;
      changedPaths.remove(path);
      changedPaths.add(path);
      while (changedPaths.size() > limit) {
        changedPaths.remove(0);
      }
    }

    putLastOrMerge(placeInfo, CHANGE_QUEUE_LIMIT, true, myCurrentCommandGroupId);
    currentIndex = changePlaces.size();
  }

  @Override
  public @NotNull List<VirtualFile> getChangedFiles() {
    List<VirtualFile> files = new ArrayList<>();
    List<String> paths;
    synchronized (state) {
      paths = state.changedPaths.isEmpty() ? Collections.emptyList() : new ArrayList<>(state.changedPaths);
    }

    LocalFileSystem lfs = LocalFileSystem.getInstance();
    for (String path : paths) {
      VirtualFile file = lfs.findFileByPath(path);
      if (file != null) {
        files.add(file);
      }
    }
    return files;
  }

  boolean isRecentlyChanged(@NotNull VirtualFile file) {
    synchronized (state) {
      return state.changedPaths.contains(file.getPath());
    }
  }

  @Override
  public final void clearHistory() {
    backPlaces.clear();
    forwardPlaces.clear();
    changePlaces.clear();

    lastGroupId = null;

    currentIndex = 0;
    commandStartPlace = null;
  }

  @Override
  public final void back() {
    removeInvalidFilesFromStacks();
    if (backPlaces.isEmpty()) {
      return;
    }

    PlaceInfo info = backPlaces.removeLast();
    project.getMessageBus().syncPublisher(RecentPlacesListener.TOPIC).recentPlaceRemoved(info, false);

    PlaceInfo current = getCurrentPlaceInfo();
    if (current != null) {
      forwardPlaces.add(current);
    }

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

    forwardInProgress = true;
    try {
      executeCommand(() -> gotoPlaceInfo(target), "", null);
    }
    finally {
      forwardInProgress = false;
    }
  }

  private PlaceInfo getTargetForwardInfo() {
    if (forwardPlaces.isEmpty()) return null;

    PlaceInfo target = forwardPlaces.removeLast();
    PlaceInfo current = getCurrentPlaceInfo();

    while (!forwardPlaces.isEmpty()) {
      if (current != null && isSame(current, target)) {
        target = forwardPlaces.removeLast();
      }
      else {
        break;
      }
    }
    return target;
  }

  @Override
  public final boolean isBackAvailable() {
    return !backPlaces.isEmpty();
  }

  @Override
  public final boolean isForwardAvailable() {
    return !forwardPlaces.isEmpty();
  }

  @Override
  public final void navigatePreviousChange() {
    removeInvalidFilesFromStacks();
    if (currentIndex == 0) return;
    PlaceInfo currentPlace = getCurrentPlaceInfo();
    for (int i = currentIndex - 1; i >= 0; i--) {
      PlaceInfo info = changePlaces.get(i);
      if (currentPlace == null || !isSame(currentPlace, info)) {
        executeCommand(() -> gotoPlaceInfo(info, true), "", null);
        currentIndex = i;
        break;
      }
    }
  }

  @Override
  public @NotNull List<PlaceInfo> getBackPlaces() {
    return Collections.unmodifiableList(backPlaces);
  }

  @Override
  public List<PlaceInfo> getChangePlaces() {
    return Collections.unmodifiableList(changePlaces);
  }

  @Override
  public void removeBackPlace(@NotNull PlaceInfo placeInfo) {
    removePlaceInfo(placeInfo, backPlaces, false);
  }

  @Override
  public void removeChangePlace(@NotNull PlaceInfo placeInfo) {
    removePlaceInfo(placeInfo, changePlaces, true);
  }

  private void removePlaceInfo(@NotNull PlaceInfo placeInfo, @NotNull Collection<PlaceInfo> places, boolean changed) {
    boolean removed = places.remove(placeInfo);
    if (removed) {
      project.getMessageBus().syncPublisher(RecentPlacesListener.TOPIC).recentPlaceRemoved(placeInfo, changed);
    }
  }

  @Override
  public final boolean isNavigatePreviousChangeAvailable() {
    return currentIndex > 0;
  }

  void removeInvalidFilesFromStacks() {
    removeInvalidFilesFrom(backPlaces);

    removeInvalidFilesFrom(forwardPlaces);
    if (removeInvalidFilesFrom(changePlaces)) {
      currentIndex = changePlaces.size();
    }
  }

  @Override
  public void navigateNextChange() {
    removeInvalidFilesFromStacks();
    if (currentIndex >= changePlaces.size()) return;
    PlaceInfo currentPlace = getCurrentPlaceInfo();
    for (int i = currentIndex; i < changePlaces.size(); i++) {
      PlaceInfo info = changePlaces.get(i);
      if (currentPlace == null || !isSame(currentPlace, info)) {
        executeCommand(() -> gotoPlaceInfo(info), "", null);
        currentIndex = i + 1;
        break;
      }
    }
  }

  @Override
  public boolean isNavigateNextChangeAvailable() {
    return currentIndex < changePlaces.size();
  }

  private boolean removeInvalidFilesFrom(@NotNull List<PlaceInfo> backPlaces) {
    return backPlaces
      .removeIf(info -> (info.myFile instanceof OptionallyIncluded &&
                         !((OptionallyIncluded)info.myFile).isIncludedInDocumentHistory(project)) ||
                        !info.myFile.isValid());
  }

  public interface OptionallyIncluded {
    boolean isIncludedInDocumentHistory(@NotNull Project project);
  }

  public interface SkipFromDocumentHistory extends OptionallyIncluded {
    @Override
    default boolean isIncludedInDocumentHistory(@NotNull Project project) {
      return false;
    }
  }

  @Override
  public void gotoPlaceInfo(@NotNull PlaceInfo info) {
    gotoPlaceInfo(info, ToolWindowManager.getInstance(project).isEditorComponentActive());
  }

  @Override
  public void gotoPlaceInfo(@NotNull PlaceInfo info, boolean requestFocus) {
    FileEditorManagerEx editorManager = getFileEditorManager();
    FileEditorOpenOptions openOptions = new FileEditorOpenOptions()
      .withUsePreviewTab(info.isPreviewTab())
      .withRequestFocus(requestFocus)
      .withReuseOpen()
      .withOpenMode(info.getOpenMode());
    var editorsWithProviders = editorManager.openFile(info.getFile(), info.getWindow(), openOptions);

    editorManager.setSelectedEditor(info.getFile(), info.getEditorTypeId());

    var list = editorsWithProviders.getAllEditorsWithProviders();
    for (FileEditorWithProvider item : list) {
      String typeId = item.getProvider().getEditorTypeId();
      if (typeId.equals(info.getEditorTypeId())) {
        item.getFileEditor().setState(info.getNavigationState());
      }
    }
  }

  /**
   * @return currently selected FileEditor or null.
   */
  protected @Nullable FileEditorWithProvider getSelectedEditor() {
    FileEditorManagerEx editorManager = getFileEditorManager();
    VirtualFile file = editorManager == null ? null : editorManager.getCurrentFile();
    return file == null ? null : editorManager.getSelectedEditorWithProvider(file);
  }

  // used by Rider
  @SuppressWarnings("WeakerAccess")
  protected PlaceInfo createPlaceInfo(@NotNull FileEditor fileEditor, FileEditorProvider fileProvider) {
    if (!fileEditor.isValid()) {
      return null;
    }

    FileEditorManagerEx editorManager = getFileEditorManager();
    VirtualFile file = fileEditor.getFile();
    LOG.assertTrue(file != null, fileEditor.getClass().getName() + " getFile() returned null");

    if (file instanceof OptionallyIncluded && !((OptionallyIncluded)file).isIncludedInDocumentHistory(project)) {
      return null;
    }

    FileEditorState state = fileEditor.getState(FileEditorStateLevel.NAVIGATION);

    EditorWindow window = editorManager == null ? null : editorManager.getCurrentWindow();
    EditorComposite composite = window != null ? window.getComposite(file) : null;
    return new PlaceInfo(file, state, fileProvider.getEditorTypeId(), window, composite != null && composite.isPreview(),
                         getCaretPosition(fileEditor), System.currentTimeMillis());
  }

  private static @Nullable RangeMarker getCaretPosition(@NotNull FileEditor fileEditor) {
    if (!(fileEditor instanceof TextEditor)) {
      return null;
    }

    Editor editor = ((TextEditor)fileEditor).getEditor();
    int offset = editor.getCaretModel().getOffset();

    return editor.getDocument().createRangeMarker(offset, offset);
  }

  private void putLastOrMerge(@NotNull PlaceInfo next, int limit, boolean isChanged, Object groupId) {
    LinkedList<PlaceInfo> list = isChanged ? changePlaces : backPlaces;
    MessageBus messageBus = project.getMessageBus();
    RecentPlacesListener listener = messageBus.syncPublisher(RecentPlacesListener.TOPIC);
    if (!list.isEmpty()) {
      PlaceInfo prev = list.getLast();
      if (isSame(prev, next)) {
        PlaceInfo removed = list.removeLast();
        listener.recentPlaceRemoved(removed, isChanged);
      }
    }

    list.add(next);
    listener.recentPlaceAdded(next, isChanged, groupId);
    if (list.size() > limit) {
      PlaceInfo first = list.removeFirst();
      listener.recentPlaceRemoved(first, isChanged);
    }
  }

  private FileDocumentManager getFileDocumentManager() {
    if (myFileDocumentManager == null) {
      myFileDocumentManager = FileDocumentManager.getInstance();
    }
    return myFileDocumentManager;
  }

  public static final class PlaceInfo {
    private final VirtualFile myFile;
    private final FileEditorState myNavigationState;
    private final String myEditorTypeId;
    private final Reference<EditorWindow> myWindow;
    private final boolean myIsPreviewTab;
    private final @Nullable RangeMarker myCaretPosition;
    private final long myTimeStamp;

    public PlaceInfo(@NotNull VirtualFile file,
                     @NotNull FileEditorState navigationState,
                     @NotNull String editorTypeId,
                     @Nullable EditorWindow window,
                     @Nullable RangeMarker caretPosition) {
      this(file, navigationState, editorTypeId, window, false, caretPosition, -1);
    }

    public PlaceInfo(@NotNull VirtualFile file,
                     @NotNull FileEditorState navigationState,
                     @NotNull String editorTypeId,
                     @Nullable EditorWindow window,
                     boolean isPreviewTab,
                     @Nullable RangeMarker caretPosition,
                     long stamp) {
      myNavigationState = navigationState;
      myFile = file;
      myEditorTypeId = editorTypeId;
      myWindow = new WeakReference<>(window);
      myIsPreviewTab = isPreviewTab;
      myCaretPosition = caretPosition;
      myTimeStamp = stamp;
    }

    public EditorWindow getWindow() {
      return myWindow.get();
    }

    public @NotNull FileEditorState getNavigationState() {
      return myNavigationState;
    }

    public @NotNull VirtualFile getFile() {
      return myFile;
    }

    public @NotNull String getEditorTypeId() {
      return myEditorTypeId;
    }

    @Override
    public String toString() {
      return getFile().getName() + " " + getNavigationState();
    }

    public @Nullable RangeMarker getCaretPosition() {
      return myCaretPosition;
    }

    public long getTimeStamp() {
      return myTimeStamp;
    }

    public boolean isPreviewTab() {
      return myIsPreviewTab;
    }

    @ApiStatus.Internal
    public @Nullable FileEditorManagerImpl.OpenMode getOpenMode() {
      if (myNavigationState instanceof FileEditorStateWithPreferredOpenMode) {
        return ((FileEditorStateWithPreferredOpenMode)myNavigationState).getOpenMode();
      }
      return null;
    }
  }

  @Override
  public final void dispose() {
    lastGroupId = null;
    PersistentHashMap<String, Long> map = recentFileTimestampMap.getValueIfInitialized();
    if (map != null) {
      try {
        map.close();
      }
      catch (IOException e) {
        LOG.info("Cannot close persistent viewed files timestamps hash map", e);
      }
    }
  }

  protected void executeCommand(Runnable runnable, @NlsContexts.Command String name, Object groupId) {
    CommandProcessor.getInstance().executeCommand(project, runnable, name, groupId);
  }

  @Override
  public boolean isSame(@NotNull PlaceInfo first, @NotNull PlaceInfo second) {
    if (first.getFile().equals(second.getFile())) {
      FileEditorState firstState = first.getNavigationState();
      FileEditorState secondState = second.getNavigationState();
      return firstState.equals(secondState) || firstState.canBeMergedWith(secondState, FileEditorStateLevel.NAVIGATION);
    }

    return false;
  }

  /**
   * {@link RecentPlacesListener} listens recently viewed or changed place adding and removing events.
   */
  public interface RecentPlacesListener {
    @Topic.ProjectLevel
    Topic<RecentPlacesListener> TOPIC = new Topic<>(RecentPlacesListener.class, Topic.BroadcastDirection.NONE);

    /**
     * Fires on new place info adding into {@link #changePlaces} or {@link #backPlaces} infos a list
     *
     * @param changePlace new place info
     * @param isChanged   true if place info was added into the changed infos list {@link #changePlaces};
     *                    false if place info was added into the back infos list {@link #backPlaces}
     */
    @Deprecated
    void recentPlaceAdded(@NotNull PlaceInfo changePlace, boolean isChanged);

    /**
     * Fires on a place info removing from the {@link #changePlaces} or the {@link #backPlaces} infos list
     *
     * @param changePlace place info that was removed
     * @param isChanged   true if place info was removed from the changed infos list {@link #changePlaces};
     *                    false if place info was removed from the back infos list {@link #backPlaces}
     */
    void recentPlaceRemoved(@NotNull PlaceInfo changePlace, boolean isChanged);

    /**
     * Fires on new place info adding into {@link #changePlaces} or {@link #backPlaces} infos a list
     *
     * @param changePlace new place info
     * @param isChanged   true if place info was added into the changed infos list {@link #changePlaces};
     *                    false if place info was added into the back infos list {@link #backPlaces}
     * @param groupId     groupId of the command that caused the change place addition
     */
    default void recentPlaceAdded(@NotNull PlaceInfo changePlace, boolean isChanged, @Nullable Object groupId) {
      recentPlaceAdded(changePlace, isChanged);
    }
  }
}
