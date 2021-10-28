// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpMeasurer.Activities;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.text.FileDropHandler;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.*;
import com.intellij.testFramework.LightVirtualFileBase;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.DirtyUI;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo;
import com.intellij.util.Alarm;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ArrayListSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ContainerEvent;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.Reference;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.intellij.openapi.wm.ToolWindowId.PROJECT_VIEW;

@DirtyUI
public class EditorsSplitters extends IdePanePanel implements UISettingsListener, Disposable {
  private static final Key<Activity> OPEN_FILES_ACTIVITY = Key.create("open.files.activity");
  private static final Logger LOG = Logger.getInstance(EditorsSplitters.class);
  @NonNls private static final String PINNED = "pinned";
  private static final String CURRENT_IN_TAB = "current-in-tab";

  private static final Key<Boolean> OPENED_IN_BULK = Key.create("EditorSplitters.opened.in.bulk");
  @NonNls public static final String SPLITTER_KEY = "EditorsSplitters";

  private EditorWindow myCurrentWindow;
  private long myLastFocusGainedTime = 0L;
  private final Set<EditorWindow> myWindows = new CopyOnWriteArraySet<>();

  private final FileEditorManagerImpl myManager;
  private Element mySplittersElement;  // temporarily used during initialization
  int myInsideChange;
  private final MyFocusWatcher myFocusWatcher;
  private final Alarm myIconUpdaterAlarm;
  private final UIBuilder myUIBuilder = new UIBuilder();

  EditorsSplitters(@NotNull FileEditorManagerImpl manager) {
    super(new BorderLayout());

    myIconUpdaterAlarm = new Alarm(this);

    setBackground(JBColor.namedColor("Editor.background", IdeBackgroundUtil.getIdeBackgroundColor()));
    PropertyChangeListener l = e -> {
      String propName = e.getPropertyName();
      if ("Editor.background".equals(propName) || "Editor.foreground".equals(propName) || "Editor.shortcutForeground".equals(propName)) {
        repaint();
      }
    };

    UIManager.getDefaults().addPropertyChangeListener(l);
    Disposer.register(this, () -> UIManager.getDefaults().removePropertyChangeListener(l));

    myManager = manager;

    myFocusWatcher = new MyFocusWatcher();
    Disposer.register(this, () -> myFocusWatcher.deinstall(this));

    setFocusTraversalPolicy(new MyFocusTraversalPolicy());
    setTransferHandler(new MyTransferHandler());
    clear();

    MessageBusConnection busConnection = ApplicationManager.getApplication().getMessageBus().connect(this);
    busConnection.subscribe(KeymapManagerListener.TOPIC, new KeymapManagerListener() {
      @Override
      public void activeKeymapChanged(@Nullable Keymap keymap) {
        invalidate();
        repaint();
      }
    });
  }

  @Override
  public void dispose() {
    setDropTarget(null);
  }

  @NotNull
  public FileEditorManagerImpl getManager() {
    return myManager;
  }

  public void clear() {
    for (EditorWindow window : myWindows) {
      window.dispose();
    }
    removeAll();
    myWindows.clear();
    setCurrentWindow(null);
    repaint (); // revalidate doesn't repaint correctly after "Close All"
  }

  void startListeningFocus() {
    myFocusWatcher.install(this);
  }

  public @Nullable VirtualFile getCurrentFile() {
    if (myCurrentWindow != null) {
      return myCurrentWindow.getSelectedFile();
    }
    return null;
  }

  private boolean showEmptyText() {
    return myCurrentWindow == null || myCurrentWindow.getFiles().length == 0;
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (showEmptyText()) {
      Graphics2D gg = IdeBackgroundUtil.withFrameBackground(g, this);
      super.paintComponent(gg);
      g.setColor(StartupUiUtil.isUnderDarcula() ? JBColor.border() : new Color(0, 0, 0, 50));
      g.drawLine(0, 0, getWidth(), 0);
    }
  }

  public void writeExternal(@NotNull Element element) {
    if (getComponentCount() == 0) {
      return;
    }

    JPanel panel = (JPanel)getComponent(0);
    if (panel.getComponentCount() != 0) {
      try {
        element.addContent(writePanel(panel.getComponent(0)));
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  private @NotNull Element writePanel(@NotNull Component comp) {
    if (comp instanceof Splitter) {
      Splitter splitter = (Splitter)comp;
      Element res = new Element("splitter");
      res.setAttribute("split-orientation", splitter.getOrientation() ? "vertical" : "horizontal");
      res.setAttribute("split-proportion", Float.toString(splitter.getProportion()));
      Element first = new Element("split-first");
      first.addContent(writePanel(splitter.getFirstComponent().getComponent(0)));
      Element second = new Element("split-second");
      second.addContent(writePanel(splitter.getSecondComponent().getComponent(0)));
      res.addContent(first);
      res.addContent(second);
      return res;
    }
    else if (comp instanceof JBTabs) {
      Element result = new Element("leaf");
      Integer limit = ClientProperty.get(((JBTabs)comp).getComponent(), JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY);
      if (limit != null) {
        result.setAttribute(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY.toString(), String.valueOf(limit));
      }

      EditorWindow window = findWindowWith(comp);
      if (window != null) {
        writeWindow(result, window);
      }
      return result;
    }
    else {
      throw new IllegalArgumentException(comp.getClass().getName());
    }
  }

  private void writeWindow(@NotNull Element result, @NotNull EditorWindow window) {
    List<EditorComposite> composites = window.getAllComposites();
    for (int i = 0; i < composites.size(); i++) {
      VirtualFile file = window.getFileAt(i);
      result.addContent(writeComposite(composites.get(i), window.isFilePinned(file), window.getSelectedComposite()));
    }
  }

  private @NotNull Element writeComposite(@NotNull EditorComposite composite, boolean pinned, @Nullable EditorComposite selectedEditor) {
    Element fileElement = new Element("file");
    composite.currentStateAsHistoryEntry().writeExternal(fileElement, getManager().getProject());
    fileElement.setAttribute(PINNED, Boolean.toString(pinned));
    fileElement.setAttribute(CURRENT_IN_TAB, Boolean.toString(composite.equals(selectedEditor)));
    return fileElement;
  }

  @Nullable Ref<JPanel> restoreEditors() {
    Element element = mySplittersElement;
    if (element == null) {
      return null;
    }

    myManager.getProject().putUserData(OPEN_FILES_ACTIVITY,
                                       StartUpMeasurer.startActivity(Activities.EDITOR_RESTORING_TILL_PAINT, ActivityCategory.DEFAULT));
    Activity restoringEditors = StartUpMeasurer.startActivity(Activities.EDITOR_RESTORING);
    JPanel component = myUIBuilder.process(element, getTopPanel());
    if (component != null) {
      component.setFocusable(false);
    }
    restoringEditors.end();
    return new Ref<>(component);
  }

  void addSelectedEditorsTo(@NotNull Collection<? super FileEditor> result) {
    for (EditorWindow window : myWindows) {
      EditorComposite composite = window.getSelectedComposite();
      if (composite != null) {
        FileEditor editor = composite.getSelectedEditor();
        if (!result.contains(editor)) {
          result.add(editor);
        }
      }
    }
    EditorWindow currentWindow = getCurrentWindow();
    if (currentWindow != null && !myWindows.contains(currentWindow)) {
      EditorComposite composite = currentWindow.getSelectedComposite();
      if (composite != null) {
        FileEditor editor = composite.getSelectedEditor();
        if (!result.contains(editor)) {
          result.add(editor);
        }
      }
    }
  }

  public void closeAllFiles() {
    ArrayList<EditorWindow> windows = new ArrayList<>(myWindows);
    clear();
    for (EditorWindow window : windows) {
      for (VirtualFile file : window.getFiles()) {
        window.closeFile(file, false, false);
      }
    }
  }

  public static void stopOpenFilesActivity(@NotNull Project project) {
    Activity activity = project.getUserData(OPEN_FILES_ACTIVITY);
    if (activity != null) {
      activity.end();
      project.putUserData(OPEN_FILES_ACTIVITY, null);
    }
  }

  public void openFiles() {
    Ref<JPanel> componentRef = restoreEditors();
    if (componentRef == null) {
      return;
    }

    ApplicationManager.getApplication().invokeAndWait(() -> doOpenFiles(componentRef.get()), ModalityState.any());
  }

  void doOpenFiles(@Nullable JPanel component) {
    if (component != null) {
      removeAll();
      add(component, BorderLayout.CENTER);
      mySplittersElement = null;
    }

    // clear empty splitters
    for (EditorWindow window : getWindows()) {
      if (window.getTabCount() == 0) {
        window.removeFromSplitter();
      }
    }
  }

  public void readExternal(@NotNull Element element) {
    mySplittersElement = element;
  }

  @NotNull List<VirtualFile> getOpenFileList() {
    List<VirtualFile> files = new ArrayList<>();
    for (EditorWindow myWindow : myWindows) {
      for (EditorComposite composite : myWindow.getAllComposites()) {
        VirtualFile file = composite.getFile();
        if (!files.contains(file)) {
          files.add(file);
        }
      }
    }
    return files;
  }

  /**
   * @deprecated Use {@link #getOpenFileList()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public @NotNull VirtualFile @NotNull [] getOpenFiles() {
    return VfsUtilCore.toVirtualFileArray(getOpenFileList());
  }

  public VirtualFile @NotNull [] getSelectedFiles() {
    Set<VirtualFile> files = new ArrayListSet<>();
    for (EditorWindow window : myWindows) {
      VirtualFile file = window.getSelectedFile();
      if (file != null) {
        files.add(file);
      }
    }
    VirtualFile[] virtualFiles = VfsUtilCore.toVirtualFileArray(files);
    VirtualFile currentFile = getCurrentFile();
    if (currentFile != null) {
      for (int i = 0; i != virtualFiles.length; ++i) {
        if (Comparing.equal(virtualFiles[i], currentFile)) {
          virtualFiles[i] = virtualFiles[0];
          virtualFiles[0] = currentFile;
          break;
        }
      }
    }
    return virtualFiles;
  }

  public FileEditor @NotNull [] getSelectedEditors() {
    Set<EditorWindow> windows = new HashSet<>(myWindows);
    EditorWindow currentWindow = getCurrentWindow();
    if (currentWindow != null) {
      windows.add(currentWindow);
    }
    List<FileEditor> editors = new ArrayList<>();
    for (EditorWindow window : windows) {
      @Nullable EditorComposite composite = window.getSelectedComposite();
      if (composite != null) {
        editors.add(composite.getSelectedEditor());
      }
    }
    return editors.toArray(FileEditor.EMPTY_ARRAY);
  }

  public void updateFileIcon(@NotNull VirtualFile file) {
    updateFileIconLater(file);
  }

  void updateFileIconImmediately(@NotNull VirtualFile file, @NotNull Icon icon) {
    Collection<EditorWindow> windows = findWindows(file);
    for (EditorWindow window : windows) {
      window.updateFileIcon(file, icon);
    }
  }

  private final Set<VirtualFile> myFilesToUpdateIconsFor = new HashSet<>();

  void updateFileIconLater(@NotNull VirtualFile file) {
    myFilesToUpdateIconsFor.add(file);
    myIconUpdaterAlarm.cancelAllRequests();
    myIconUpdaterAlarm.addRequest(() -> {
      if (myManager.getProject().isDisposed()) return;
      for (VirtualFile file1 : myFilesToUpdateIconsFor) {
        updateFileIconAsynchronously(file1);
      }
      myFilesToUpdateIconsFor.clear();
    }, 200, ModalityState.stateForComponent(this));
  }

  private void updateFileIconAsynchronously(VirtualFile file) {
    ReadAction
      .nonBlocking(() -> IconUtil.computeFileIcon(file, Iconable.ICON_FLAG_READ_STATUS, myManager.getProject()))
      .coalesceBy(this, "icon", file)
      .expireWith(this)
      .expireWhen(() -> !file.isValid())
      .finishOnUiThread(ModalityState.any(), icon -> updateFileIconImmediately(file, icon))
      .submit(NonUrgentExecutor.getInstance());
  }

  void updateFileColor(@NotNull VirtualFile file) {
    Collection<EditorWindow> windows = findWindows(file);
    if (windows.isEmpty()) {
      return;
    }

    EditorColorsScheme colorScheme = EditorColorsManager.getInstance().getSchemeForCurrentUITheme();
    for (EditorWindow window : windows) {
      EditorComposite composite = window.getComposite(file);
      LOG.assertTrue(composite != null);
      int index = window.findCompositeIndex(composite);
      LOG.assertTrue(index != -1);
      window.setForegroundAt(index, getManager().getFileColor(file));
      TextAttributes attributes = getManager().isProblem(file) ? colorScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES) : null;
      if (composite.isPreview()) {
        var italic = new TextAttributes(null, null, null, null, Font.ITALIC);
        attributes = (attributes == null) ? italic : TextAttributes.merge(italic, attributes);
      }
      window.setTextAttributes(index, attributes);
    }
  }

  public void trimToSize() {
    for (EditorWindow window : myWindows) {
      window.trimToSize(window.getSelectedFile(), true);
    }
  }

  void updateTabsLayout(@NotNull TabsLayoutInfo newTabsLayoutInfo) {
    EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows[i].updateTabsLayout(newTabsLayoutInfo);
    }
  }

  public void setTabsPlacement(int tabPlacement) {
    EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows[i].setTabsPlacement(tabPlacement);
    }
  }

  void setTabLayoutPolicy(int scrollTabLayout) {
    EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows[i].setTabLayoutPolicy(scrollTabLayout);
    }
  }

  void updateFileName(@Nullable VirtualFile updatedFile) {
    for (EditorWindow window : getWindows()) {
      for (VirtualFile file : window.getFiles()) {
        if (updatedFile == null || file.getName().equals(updatedFile.getName())) {
          window.updateFileName(file);
        }
      }
    }

    Project project = myManager.getProject();
    IdeFrameEx frame = getFrame(project);
    if (frame == null) {
      return;
    }
    VirtualFile file = getCurrentFile();
    if (file == null) {
      frame.setFileTitle(null, null);
    }
    else  {
      Path ioFile = null;
      try {
        ioFile = file instanceof LightVirtualFileBase ? null : Paths.get(file.getPresentableUrl());
      }
      catch (InvalidPathException ignored) {
      }
      Path finalIoFile = ioFile;
      ReadAction.nonBlocking(() -> FrameTitleBuilder.getInstance().getFileTitle(project, file))
        .expireWith(this)
        .coalesceBy(this)
        .finishOnUiThread(ModalityState.any(), (@NlsContexts.TabTitle String title) -> {
          frame.setFileTitle(title, finalIoFile);
        })
        .submit(AppExecutorUtil.getAppExecutorService());
    }
  }

  protected @Nullable IdeFrameEx getFrame(@NotNull Project project) {
    ProjectFrameHelper frame = WindowManagerEx.getInstanceEx().getFrameHelper(project);
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode()
                   || ApplicationManager.getApplication().isHeadlessEnvironment()
                   || frame != null);
    return frame;
  }

  boolean isInsideChange() {
    return myInsideChange > 0;
  }

  private void setCurrentWindow(@Nullable EditorWindow currentWindow) {
    if (currentWindow != null && !myWindows.contains(currentWindow)) {
      throw new IllegalArgumentException(currentWindow + " is not a member of this container");
    }
    myCurrentWindow = currentWindow;
  }

  void updateFileBackgroundColor(@NotNull VirtualFile file) {
    EditorWindow[] windows = getWindows();
    for (int i = 0; i != windows.length; ++ i) {
      windows [i].updateFileBackgroundColor(file);
    }
  }

  int getSplitCount() {
    if (getComponentCount() > 0) {
      JPanel panel = (JPanel) getComponent(0);
      return getSplitCount(panel);
    }
    return 0;
  }

  private static int getSplitCount(@NotNull JComponent component) {
    if (component.getComponentCount() > 0) {
      JComponent firstChild = (JComponent)component.getComponent(0);
      if (firstChild instanceof Splitter) {
        Splitter splitter = (Splitter)firstChild;
        return getSplitCount(splitter.getFirstComponent()) + getSplitCount(splitter.getSecondComponent());
      }
      return 1;
    }
    return 0;
  }

  protected void afterFileClosed(@NotNull VirtualFile file) {
  }

  protected void afterFileOpen(@NotNull VirtualFile file) {
  }

  @Nullable
  JBTabs getTabsAt(@NotNull RelativePoint point) {
    Point thisPoint = point.getPoint(this);
    Component c = SwingUtilities.getDeepestComponentAt(this, thisPoint.x, thisPoint.y);
    while (c != null) {
      if (c instanceof JBTabs) {
        return (JBTabs)c;
      }
      c = c.getParent();
    }

    return null;
  }

  boolean isEmptyVisible() {
    EditorWindow[] windows = getWindows();
    for (EditorWindow each : windows) {
      if (!each.isEmptyVisible()) {
        return false;
      }
    }
    return true;
  }

  private @Nullable VirtualFile findNextFile(@NotNull VirtualFile file) {
    EditorWindow[] windows = getWindows(); // TODO: use current file as base
    for (int i = 0; i != windows.length; ++i) {
      VirtualFile[] files = windows[i].getFiles();
      for (VirtualFile fileAt : files) {
        if (!Comparing.equal(fileAt, file)) {
          return fileAt;
        }
      }
    }
    return null;
  }

  void closeFile(@NotNull VirtualFile file, boolean moveFocus) {
    List<EditorWindow> windows = findWindows(file);
    boolean isProjectOpen = myManager.getProject().isOpen();
    if (windows.isEmpty()) {
      return;
    }

    VirtualFile nextFile = findNextFile(file);
    for (EditorWindow window : windows) {
      LOG.assertTrue(window.getSelectedComposite() != null);
      window.closeFile(file, false, moveFocus);
      if (window.getTabCount() == 0 && nextFile != null && isProjectOpen && !FileEditorManagerImpl.forbidSplitFor(nextFile)) {
        EditorComposite newComposite = myManager.newEditorComposite(nextFile);
        if (newComposite != null) {
          window.setComposite(newComposite, moveFocus);
        }
      }
    }
    // cleanup windows with no tabs
    for (EditorWindow window : windows) {
      if (!isProjectOpen || window.isDisposed()) {
        // call to window.unsplit() which might make its sibling disposed
        continue;
      }
      if (window.getTabCount() == 0) {
        window.unsplit(false);
      }
    }
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    for (EditorWindow window : myWindows) {
      window.updateTabsVisibility(uiSettings);
    }

    if (!myManager.getProject().isOpen()) {
      return;
    }

    for (VirtualFile file : getOpenFileList()) {
      updateFileBackgroundColor(file);
      updateFileIcon(file);
      updateFileColor(file);
    }
  }

  private final class MyFocusTraversalPolicy extends IdeFocusTraversalPolicy {
    @Override
    public Component getDefaultComponent(Container focusCycleRoot) {
      if (myCurrentWindow != null) {
        EditorComposite selectedComposite = myCurrentWindow.getSelectedComposite();
        if (selectedComposite != null) {
          JComponent focusComponent = selectedComposite.getFocusComponent();
          if (focusComponent != null) {
            return IdeFocusTraversalPolicy.getPreferredFocusedComponent(focusComponent, this);
          }
        }
      }
      return IdeFocusTraversalPolicy.getPreferredFocusedComponent(EditorsSplitters.this, this);
    }

    @Override
    protected @NotNull Project getProject() {
      return myManager.getProject();
    }
  }

  public @Nullable JPanel getTopPanel() {
    return getComponentCount() > 0 ? (JPanel)getComponent(0) : null;
  }

  public EditorWindow getCurrentWindow() {
    return myCurrentWindow;
  }

  public long getLastFocusGainedTime() {
    return myLastFocusGainedTime;
  }

  @NotNull
  public EditorWindow getOrCreateCurrentWindow(@NotNull VirtualFile file) {
    List<EditorWindow> windows = findWindows(file);
    if (getCurrentWindow() == null) {
      Iterator<EditorWindow> iterator = myWindows.iterator();
      if (!windows.isEmpty()) {
        setCurrentWindow(windows.get(0), false);
      }
      else if (iterator.hasNext()) {
        setCurrentWindow(iterator.next(), false);
      }
      else {
        createCurrentWindow();
      }
    }
    else if (!windows.isEmpty()) {
      if (!windows.contains(getCurrentWindow())) {
        setCurrentWindow(windows.get(0), false);
      }
    }
    return getCurrentWindow();
  }

  void createCurrentWindow() {
    LOG.assertTrue(myCurrentWindow == null);
    setCurrentWindow(createEditorWindow());
    add(myCurrentWindow.myPanel, BorderLayout.CENTER);
  }

  @NotNull
  private EditorWindow createEditorWindow() {
    return new EditorWindow(this, this);
  }

  /**
   * sets the window passed as a current ('focused') window among all splitters. All file openings will be done inside this
   * current window
   * @param window a window to be set as current
   * @param requestFocus whether to request focus to the editor currently selected in this window
   */
  void setCurrentWindow(@Nullable EditorWindow window, boolean requestFocus) {
    EditorComposite newComposite = window == null ? null : window.getSelectedComposite();

    Runnable fireRunnable = () -> getManager().fireSelectionChanged(newComposite);

    setCurrentWindow(window);

    getManager().updateFileName(window == null ? null : window.getSelectedFile());

    if (window != null) {
      EditorComposite selectedComposite = window.getSelectedComposite();
      if (selectedComposite != null) {
        fireRunnable.run();
      }

      if (requestFocus) {
        window.requestFocus(true);
      }
    }
    else {
      fireRunnable.run();
    }
  }

  void addWindow(@NotNull EditorWindow window) {
    myWindows.add(window);
  }

  void removeWindow(@NotNull EditorWindow window) {
    myWindows.remove(window);
    if (myCurrentWindow == window) {
      myCurrentWindow = null;
    }
  }

  boolean containsWindow(@NotNull EditorWindow window) {
    return myWindows.contains(window);
  }

  /**
   * @deprecated Use {@link #getAllComposites()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
  public @NotNull List<EditorWithProviderComposite> getEditorComposites() {
    return ContainerUtil.filterIsInstance(getAllComposites(), EditorWithProviderComposite.class);
  }

  public @NotNull List<EditorComposite> getAllComposites() {
    return ContainerUtil.flatMap(myWindows, it -> it.getAllComposites());
  }

  //---------------------------------------------------------

  /**
   * @deprecated Use {@link #getAllComposites(VirtualFile)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
  public @NotNull List<EditorWithProviderComposite> findEditorComposites(@NotNull VirtualFile file) {
    return ContainerUtil.filterIsInstance(getAllComposites(file), EditorWithProviderComposite.class);
  }

  public @NotNull List<EditorComposite> getAllComposites(@NotNull VirtualFile file) {
    return ContainerUtil.mapNotNull(myWindows, it -> it.getComposite(file));
  }

  private @NotNull List<EditorWindow> findWindows(@NotNull VirtualFile file) {
    List<EditorWindow> result = new ArrayList<>(myWindows.size());
    for (EditorWindow window : myWindows) {
      if (window.getComposite(file) != null) {
        result.add(window);
      }
    }
    return result;
  }

  public EditorWindow @NotNull [] getWindows() {
    return myWindows.toArray(new EditorWindow[0]);
  }

  @NotNull
  List<EditorWindow> getOrderedWindows() {
    List<EditorWindow> result = new ArrayList<>();
    // Collector for windows in tree ordering:
    class WindowCollector {
      private void collect(@NotNull JPanel panel){
        Component comp = panel.getComponent(0);
        if (comp instanceof Splitter) {
          Splitter splitter = (Splitter)comp;
          collect((JPanel)splitter.getFirstComponent());
          collect((JPanel)splitter.getSecondComponent());
        }
        else if (comp instanceof JPanel || comp instanceof JBTabs) {
          EditorWindow window = findWindowWith(comp);
          if (window != null) {
            result.add(window);
          }
        }
      }
    }

    // get root component and traverse splitters tree:
    if (getComponentCount() != 0) {
      Component comp = getComponent(0);
      LOG.assertTrue(comp instanceof JPanel);
      JPanel panel = (JPanel)comp;
      if (panel.getComponentCount() != 0) {
        new WindowCollector().collect (panel);
      }
    }

    LOG.assertTrue(result.size() == myWindows.size());
    return result;
  }

  private @Nullable EditorWindow findWindowWith(@NotNull Component component) {
    for (EditorWindow window : myWindows) {
      if (SwingUtilities.isDescendingFrom(component, window.myPanel)) {
        return window;
      }
    }
    return null;
  }

  public boolean isFloating() {
    return false;
  }

  public static boolean isOpenedInBulk(@NotNull VirtualFile file) {
    return file.getUserData(OPENED_IN_BULK) != null;
  }

  private final class MyFocusWatcher extends FocusWatcher {
    @Override
    protected void focusedComponentChanged(Component component, AWTEvent cause) {
      if (cause instanceof FocusEvent && cause.getID() == FocusEvent.FOCUS_GAINED){
        if (((FocusEvent)cause).getCause() == FocusEvent.Cause.ACTIVATION) {
          // Window activation mistakenly puts focus to editor as 'last focused component in this window'
          // even if you activate the window by clicking some other place (e.g. Project View)
          SwingUtilities.invokeLater(() -> {
            if (component.isFocusOwner()) {
              myLastFocusGainedTime = System.currentTimeMillis();
            }
          });
        } else {
          myLastFocusGainedTime = System.currentTimeMillis();
        }
      }

      EditorWindow newWindow = null;

      if (component != null) {
        newWindow = findWindowWith(component);
      }
      else if (cause instanceof ContainerEvent && cause.getID() == ContainerEvent.COMPONENT_REMOVED) {
        // do not change current window in case of child removal as in JTable.removeEditor
        // otherwise Escape in a toolwindow will not focus editor with JTable content
        return;
      }

      setCurrentWindow(newWindow);
      setCurrentWindow(newWindow, false);
    }
  }

  private final class MyTransferHandler extends TransferHandler {
    private final FileDropHandler myFileDropHandler = new FileDropHandler(null);

    @Override
    public boolean importData(JComponent comp, Transferable t) {
      if (myFileDropHandler.canHandleDrop(t.getTransferDataFlavors())) {
        myFileDropHandler.handleDrop(t, myManager.getProject(), myCurrentWindow);
        return true;
      }
      return false;
    }

    @Override
    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
      return myFileDropHandler.canHandleDrop(transferFlavors);
    }
  }

  private abstract static class ConfigTreeReader<T> {
    public final @Nullable T process(@NotNull Element element, @Nullable T context) {
      Element splitterElement = element.getChild("splitter");
      if (splitterElement != null) {
        Element first = splitterElement.getChild("split-first");
        Element second = splitterElement.getChild("split-second");
        return processSplitter(splitterElement, first, second, context);
      }

      Element leaf = element.getChild("leaf");
      if (leaf == null) {
        return null;
      }

      List<Element> fileElements = leaf.getChildren("file");
      List<Element> children;
      if (fileElements.isEmpty()) {
        children = Collections.emptyList();
      }
      else {
        children = new ArrayList<>(fileElements.size());
        // trim to EDITOR_TAB_LIMIT, ignoring CLOSE_NON_MODIFIED_FILES_FIRST policy
        int toRemove = fileElements.size() - EditorWindow.getTabLimit();
        for (Element fileElement : fileElements) {
          if (toRemove <= 0 || Boolean.parseBoolean(fileElement.getAttributeValue(PINNED))) {
            children.add(fileElement);
          }
          else {
            toRemove--;
          }
        }
      }

      return processFiles(children, StringUtil.parseInt(leaf.getAttributeValue(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY.toString()), -1), context);
    }

    abstract @Nullable T processFiles(@NotNull List<? extends Element> fileElements, int tabSizeLimit, @Nullable T context);

    abstract @Nullable T processSplitter(@NotNull Element element, @Nullable Element firstChild, @Nullable Element secondChild, @Nullable T context);
  }

  private final class UIBuilder extends ConfigTreeReader<JPanel> {
    @Override
    protected JPanel processFiles(@NotNull List<? extends Element> fileElements, int tabSizeLimit, JPanel context) {
      Ref<EditorWindow> windowRef = new Ref<>();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        EditorWindow editorWindow = context == null ? createEditorWindow() : findWindowWith(context);
        windowRef.set(editorWindow);
        if (editorWindow != null) {
          setCurrentWindow(editorWindow, false);
          if (tabSizeLimit != 1) {
            editorWindow.getTabbedPane().getComponent().putClientProperty(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY, tabSizeLimit);
          }
        }
      });

      EditorWindow window = windowRef.get();
      LOG.assertTrue(window != null);
      VirtualFile focusedFile = null;
      FileEditorManagerImpl fileEditorManager = getManager();
      for (int i = 0; i < fileElements.size(); i++) {
        Element file = fileElements.get(i);
        Element historyElement = file.getChild(HistoryEntry.TAG);
        String fileName = historyElement.getAttributeValue(HistoryEntry.FILE_ATTR);
        Activity activity = StartUpMeasurer.startActivity(PathUtil.getFileName(fileName), ActivityCategory.REOPENING_EDITOR);
        HistoryEntry entry = HistoryEntry.createLight(fileEditorManager.getProject(), historyElement);
        VirtualFile virtualFile = entry.getFile();
        if (virtualFile == null) {
          if (ApplicationManager.getApplication().isUnitTestMode()) {
            LOG.error(new InvalidDataException("No file exists: " + entry.getFilePointer().getUrl()));
          }
        }
        else {
          FileEditorOpenOptions openOptions = new FileEditorOpenOptions()
            .withSelectAsCurrent(false)
            .withPin(Boolean.valueOf(file.getAttributeValue(PINNED)))
            .withIndex(i)
            .withReopeningOnStartup();
          try {
            virtualFile.putUserData(OPENED_IN_BULK, Boolean.TRUE);
            Document document = ReadAction.compute(() -> {
              return virtualFile.isValid() ? FileDocumentManager.getInstance().getDocument(virtualFile) : null;
            });

            boolean isCurrentTab = Boolean.parseBoolean(file.getAttributeValue(CURRENT_IN_TAB));

            fileEditorManager.openFileImpl4(window, virtualFile, entry, openOptions);
            // This is just to make sure document reference is kept on stack till this point
            // so that document is available for folding state deserialization in HistoryEntry constructor
            // and that document will be created only once during file opening
            Reference.reachabilityFence(document);
            if (isCurrentTab) {
              focusedFile = virtualFile;
            }
          }
          catch (InvalidDataException e) {
            if (ApplicationManager.getApplication().isUnitTestMode()) {
              LOG.error(e);
            }
          }
          finally {
            virtualFile.putUserData(OPENED_IN_BULK, null);
          }
        }
        activity.end();
      }

      if (focusedFile == null) {
        ToolWindowManager manager = ToolWindowManager.getInstance(getManager().getProject());
        manager.invokeLater(() -> {
          if (manager.getActiveToolWindowId() == null) {
            ToolWindow toolWindow = manager.getToolWindow(PROJECT_VIEW);
            if (toolWindow != null) {
              toolWindow.activate(null);
            }
          }
        });
      }
      else {
        fileEditorManager.addSelectionRecord(focusedFile, window);
        VirtualFile finalFocusedFile = focusedFile;
        UIUtil.invokeLaterIfNeeded(() -> {
          EditorComposite composite = window.getComposite(finalFocusedFile);
          if (composite != null) {
            window.setComposite(composite, true);
          }
        });
      }
      return window.myPanel;
    }

    @Override
    protected JPanel processSplitter(@NotNull Element splitterElement, Element firstChild, Element secondChild, JPanel context) {
      if (context == null) {
        boolean orientation = "vertical".equals(splitterElement.getAttributeValue("split-orientation"));
        float proportion = Float.parseFloat(splitterElement.getAttributeValue("split-proportion"));
        JPanel firstComponent = process(firstChild, null);
        JPanel secondComponent = process(secondChild, null);
        Ref<JPanel> panelRef = new Ref<>();
        ApplicationManager.getApplication().invokeAndWait(() -> {
          JPanel panel = new JPanel(new BorderLayout());
          panel.setOpaque(false);
          Splitter splitter = new OnePixelSplitter(orientation, proportion, 0.1f, 0.9f);
          splitter.putClientProperty(SPLITTER_KEY, Boolean.TRUE);
          panel.add(splitter, BorderLayout.CENTER);
          splitter.setFirstComponent(firstComponent);
          splitter.setSecondComponent(secondComponent);
          panelRef.set(panel);
        });
        return panelRef.get();
      }

      Ref<JPanel> firstComponent = new Ref<>();
      Ref<JPanel> secondComponent = new Ref<>();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        if (context.getComponent(0) instanceof Splitter) {
          Splitter splitter = (Splitter)context.getComponent(0);
          firstComponent.set((JPanel)splitter.getFirstComponent());
          secondComponent.set((JPanel)splitter.getSecondComponent());
        }
        else {
          firstComponent.set(context);
          secondComponent.set(context);
        }
      });
      process(firstChild, firstComponent.get());
      process(secondChild, secondComponent.get());
      return context;
    }
  }

  private static @Nullable EditorsSplitters getSplittersToFocus(@Nullable Project project) {
    Window activeWindow = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();

    if (activeWindow instanceof FloatingDecorator) {
      IdeFrame lastFocusedFrame = IdeFocusManager.findInstanceByComponent(activeWindow).getLastFocusedFrame();
      JComponent frameComponent = lastFocusedFrame != null ? lastFocusedFrame.getComponent() : null;
      Window lastFocusedWindow = frameComponent == null ? null : SwingUtilities.getWindowAncestor(frameComponent);
      activeWindow = ObjectUtils.notNull(lastFocusedWindow, activeWindow);
      if (project == null) {
        project = lastFocusedFrame == null ? null : lastFocusedFrame.getProject();
      }
      FileEditorManagerEx fileEditorManager = project == null || project.isDisposed() ? null : FileEditorManagerEx.getInstanceEx(project);
      if (fileEditorManager == null) {
        return null;
      }
      EditorsSplitters splitters = fileEditorManager.getSplittersFor(activeWindow);
      return splitters != null ? splitters : fileEditorManager.getSplitters();
    }

    if (activeWindow instanceof IdeFrame.Child) {
      if (project == null) {
        project = ((IdeFrame.Child)activeWindow).getProject();
      }
      return getSplittersForProject(WindowManager.getInstance().getFrame(project), project);
    }

    IdeFrame frame = FocusManagerImpl.getInstance().getLastFocusedFrame();
    if (frame instanceof IdeFrameImpl && ((IdeFrameImpl)frame).isActive()) {
      return getSplittersForProject(activeWindow, frame.getProject());
    }

    return null;
  }

  private static @Nullable EditorsSplitters getSplittersForProject(@Nullable Window activeWindow, @Nullable Project project) {
    FileEditorManagerEx fileEditorManager = project == null || project.isDisposed() ? null : FileEditorManagerEx.getInstanceEx(project);
    if (fileEditorManager == null) {
      return null;
    }
    EditorsSplitters splitters = activeWindow == null ? null : fileEditorManager.getSplittersFor(activeWindow);
    return splitters == null ? fileEditorManager.getSplitters() : splitters;
  }

  public static @Nullable JComponent findDefaultComponentInSplitters(@Nullable Project project)  {
    EditorsSplitters splittersToFocus = getSplittersToFocus(project);
    if (splittersToFocus == null) {
      return null;
    }

    EditorWindow window = splittersToFocus.getCurrentWindow();
    EditorComposite composite = window == null ? null : window.getSelectedComposite();
    if (composite != null) {
      return composite.getPreferredFocusedComponent();
    }
    return null;
  }

  @Nullable
  public EditorWindow openInRightSplit(@NotNull VirtualFile file) {
    return openInRightSplit(file, true);
  }

  @Nullable
  public EditorWindow openInRightSplit(@NotNull VirtualFile file, boolean requestFocus) {
    EditorWindow window = getCurrentWindow();

    if (window == null) {
      return null;
    }
    Container parent = window.myPanel.getParent();
    if (parent instanceof Splitter) {
      JComponent component = ((Splitter)parent).getSecondComponent();
      if (component != window.myPanel) {
        //reuse
        EditorWindow rightSplitWindow = findWindowWith(component);
        if (rightSplitWindow != null) {
          myManager.openFileWithProviders(file, requestFocus, rightSplitWindow);
          return rightSplitWindow;
        }
      }
    }

    return window.split(SwingConstants.VERTICAL, true, file, requestFocus);
  }

  public static boolean focusDefaultComponentInSplittersIfPresent(@NotNull Project project) {
    JComponent defaultFocusedComponentInEditor = findDefaultComponentInSplitters(project);
    if (defaultFocusedComponentInEditor != null) {
      // not requestFocus because if floating or windowed tool window is deactivated (or, ESC pressed to focus editor), then we should focus our window
      defaultFocusedComponentInEditor.requestFocus();
      return true;
    }
    return false;
  }
}
