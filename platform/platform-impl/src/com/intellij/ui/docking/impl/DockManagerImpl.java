// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.docking.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.impl.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.toolWindow.*;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.DevicePoint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.ui.docking.*;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;

import static javax.swing.SwingConstants.CENTER;

@State(name = "DockManager", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
public final class DockManagerImpl extends DockManager implements PersistentStateComponent<Element> {
  private final Project myProject;

  private final Map<String, DockContainerFactory> myFactories = new HashMap<>();
  private final Set<DockContainer> myContainers = new HashSet<>();
  private final Map<String, DockWindow> myWindows = new HashMap<>();
  private final Map<DockContainer, DockWindow> containerToWindow = new HashMap<>();

  private MyDragSession myCurrentDragSession;

  private final BusyObject.Impl myBusyObject = new BusyObject.Impl() {
    @Override
    public boolean isReady() {
      return myCurrentDragSession == null;
    }
  };

  private int myWindowIdCounter = 1;

  private Element myLoadedState;

  public static final Key<Boolean> SHOW_NORTH_PANEL = Key.create("SHOW_NORTH_PANEL");

  public static final Key<String> WINDOW_DIMENSION_KEY = Key.create("WINDOW_DIMENSION_KEY");
  public static final Key<Boolean> REOPEN_WINDOW = Key.create("REOPEN_WINDOW");
  public static final Key<Boolean> ALLOW_DOCK_TOOL_WINDOWS = Key.create("ALLOW_DOCK_TOOL_WINDOWS");

  public DockManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void register(@NotNull DockContainer container, @NotNull Disposable parentDisposable) {
    myContainers.add(container);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myContainers.remove(container);
      }
    });
  }

  @Override
  public void register(@NotNull String id, @NotNull DockContainerFactory factory, @NotNull Disposable parentDisposable) {
    myFactories.put(id, factory);

    if (parentDisposable != myProject) {
      Disposer.register(parentDisposable, new Disposable() {
        @Override
        public void dispose() {
          myFactories.remove(id);
        }
      });
    }

    readStateFor(id);
  }

  public void readState() {
    for (String id : myFactories.keySet()) {
      readStateFor(id);
    }
  }

  @Override
  public @NotNull Set<DockContainer> getContainers() {
    Set<DockContainer> result = new HashSet<>(myContainers.size() + containerToWindow.size());
    result.addAll(myContainers);
    result.addAll(containerToWindow.keySet());
    return Collections.unmodifiableSet(result);
  }

  @Override
  public IdeFrame getIdeFrame(@NotNull DockContainer container) {
    Component parent = UIUtil.findUltimateParent(container.getContainerComponent());
    return parent instanceof IdeFrame ? (IdeFrame)parent : null;
  }

  @Override
  public String getDimensionKeyForFocus(@NotNull String key) {
    Component owner = IdeFocusManager.getInstance(myProject).getFocusOwner();
    if (owner == null) {
      return key;
    }

    DockWindow window = containerToWindow.get(getContainerFor(owner, dockContainer -> true));
    return window != null ? key + "#" + window.myId : key;
  }

  @Override
  public DockContainer getContainerFor(Component c) {
    return getContainerFor(c, dockContainer -> true);
  }

  @Contract("null, _ -> null")
  @Override
  public @Nullable DockContainer getContainerFor(@Nullable Component c, @NotNull Predicate<? super DockContainer> filter) {
    if (c == null) {
      return null;
    }

    for (DockContainer eachContainer : getAllContainers()) {
      if (SwingUtilities.isDescendingFrom(c, eachContainer.getContainerComponent()) && filter.test(eachContainer)) {
        return eachContainer;
      }
    }

    Component parent = UIUtil.findUltimateParent(c);
    for (DockContainer eachContainer : getAllContainers()) {
      if (parent == UIUtil.findUltimateParent(eachContainer.getContainerComponent()) && filter.test(eachContainer)) {
        return eachContainer;
      }
    }

    return null;
  }

  @Override
  public DragSession createDragSession(MouseEvent mouseEvent, @NotNull DockableContent content) {
    stopCurrentDragSession();

    for (DockContainer each : getAllContainers()) {
      if (each.isEmpty() && each.isDisposeWhenEmpty()) {
        DockWindow window = containerToWindow.get(each);
        if (window != null) {
          window.setTransparent(true);
        }
      }
    }

    myCurrentDragSession = new MyDragSession(mouseEvent, content);
    return myCurrentDragSession;
  }


  public void stopCurrentDragSession() {
    if (myCurrentDragSession != null) {
      myCurrentDragSession.cancelSession();
      myCurrentDragSession = null;
      myBusyObject.onReady();

      for (DockContainer each : getAllContainers()) {
        if (!each.isEmpty()) {
          DockWindow window = containerToWindow.get(each);
          if (window != null) {
            window.setTransparent(false);
          }
        }
      }
    }
  }

  private ActionCallback getReady() {
    return myBusyObject.getReady(this);
  }

  private final class MyDragSession implements DragSession {
    private final JDialog myWindow;

    private Image myDragImage;
    private final Image myDefaultDragImage;

    private final @NotNull DockableContent myContent;

    private final DockContainer myStartDragContainer;
    private DockContainer myCurrentOverContainer;
    private final JLabel myImageContainer;

    private MyDragSession(MouseEvent me, @NotNull DockableContent content) {
      myWindow = new JDialog(ComponentUtil.getWindow(me.getComponent()));
      myWindow.setUndecorated(true);
      myContent = content;
      myStartDragContainer = getContainerFor(me.getComponent());

      BufferedImage buffer = ImageUtil.toBufferedImage(content.getPreviewImage());

      double requiredSize = 220;

      double width = buffer.getWidth(null);
      double height = buffer.getHeight(null);

      double ratio;
      if (width > height) {
        ratio = requiredSize / width;
      }
      else {
        ratio = requiredSize / height;
      }

      myDefaultDragImage = buffer.getScaledInstance((int)(width * ratio), (int)(height * ratio), Image.SCALE_SMOOTH);
      myDragImage = myDefaultDragImage;

      myImageContainer = new JLabel(new Icon() {
        @Override
        public int getIconWidth() {
          return myDefaultDragImage.getWidth(myWindow);
        }

        @Override
        public int getIconHeight() {
          return myDefaultDragImage.getHeight(myWindow);
        }

        @Override
        public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
          StartupUiUtil.drawImage(g, myDefaultDragImage, x, y, myWindow);
        }
      });
      myWindow.setContentPane(myImageContainer);

      setLocationFrom(me);

      myWindow.setVisible(true);

      WindowManagerEx windowManager = WindowManagerEx.getInstanceEx();
      windowManager.setAlphaModeEnabled(myWindow, true);
      windowManager.setAlphaModeRatio(myWindow, 0.1f);
    }

    private void setLocationFrom(MouseEvent me) {
      DevicePoint devicePoint = new DevicePoint(me);
      Point showPoint = devicePoint.getLocationOnScreen();

      Dimension size = myImageContainer.getSize();
      showPoint.x -= size.width / 2;
      showPoint.y -= size.height / 2;
      myWindow.setBounds(new Rectangle(showPoint, size));
    }

    @Override
    public @NotNull DockContainer.ContentResponse getResponse(MouseEvent e) {
      DevicePoint point = new DevicePoint(e);
      for (DockContainer each : getAllContainers()) {
        RelativeRectangle rec = each.getAcceptArea();
        if (rec.contains(point)) {
          JComponent component = each.getContainerComponent();
          if (component.getGraphicsConfiguration() != null) {
            DockContainer.ContentResponse response = each.getContentResponse(myContent, point.toRelativePoint(component));
            if (response.canAccept()) {
              return response;
            }
          }
        }
      }
      return DockContainer.ContentResponse.DENY;
    }

    @Override
    public void process(MouseEvent e) {
      DevicePoint devicePoint = new DevicePoint(e);

      Image img = null;
      if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
        DockContainer over = findContainerFor(devicePoint, myContent);
        if (myCurrentOverContainer != null && myCurrentOverContainer != over) {
          myCurrentOverContainer.resetDropOver(myContent);
          myCurrentOverContainer = null;
        }

        if (myCurrentOverContainer == null && over != null) {
          myCurrentOverContainer = over;
          RelativePoint point = devicePoint.toRelativePoint(over.getContainerComponent());
          img = myCurrentOverContainer.startDropOver(myContent, point);
        }

        if (myCurrentOverContainer != null) {
          RelativePoint point = devicePoint.toRelativePoint(myCurrentOverContainer.getContainerComponent());
          img = myCurrentOverContainer.processDropOver(myContent, point);
        }

        if (img == null) {
          img = myDefaultDragImage;
        }

        if (img != myDragImage) {
          myDragImage = img;
          myImageContainer.setIcon(IconUtil.createImageIcon(myDragImage));
          myWindow.pack();
        }

        setLocationFrom(e);
      }
      else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
        if (myCurrentOverContainer == null) {
          // This relative point might be relative to a component that's on a different screen, with a different DPI scaling factor than
          // the target location. Ideally, we should pass the DevicePoint to createNewDockContainerFor, but that will change the API. We'll
          // fix it up inside createNewDockContainerFor
          RelativePoint point = new RelativePoint(e);
          createNewDockContainerFor(myContent, point);
          e.consume();//Marker for DragHelper: drag into separate window is not tabs reordering
        }
        else {
          RelativePoint point = devicePoint.toRelativePoint(myCurrentOverContainer.getContainerComponent());
          myCurrentOverContainer.add(myContent, point);
          ObjectUtils.consumeIfCast(myCurrentOverContainer, DockableEditorTabbedContainer.class, container -> {
            //Marker for DragHelper, not 'refined' drop in tab-set shouldn't affect ABC-order setting
            if (container.getCurrentDropSide() == CENTER) e.consume();
          });
        }
        stopCurrentDragSession();
      }
    }

    @Override
    public void cancel() {
      stopCurrentDragSession();
    }

    private void cancelSession() {
      myWindow.dispose();

      if (myCurrentOverContainer != null) {
        myCurrentOverContainer.resetDropOver(myContent);
        myCurrentOverContainer = null;
      }
    }
  }

  private @Nullable DockContainer findContainerFor(DevicePoint devicePoint, @NotNull DockableContent<?> content) {
    List<DockContainer> containers = new ArrayList<>(getContainers());
    MyDragSession session = myCurrentDragSession;
    if (session != null) {
      DockContainer startDragContainer = session.myStartDragContainer;
      if (startDragContainer != null) {
        containers.remove(startDragContainer);
        containers.add(0, startDragContainer);
      }
    }

    for (DockContainer each : containers) {
      RelativeRectangle rec = each.getAcceptArea();
      if (rec.contains(devicePoint) && each.getContentResponse(content, devicePoint.toRelativePoint(each.getContainerComponent())).canAccept()) {
        return each;
      }
    }

    for (DockContainer each : containers) {
      RelativeRectangle rec = each.getAcceptAreaFallback();
      if (rec.contains(devicePoint) && each.getContentResponse(content, devicePoint.toRelativePoint(each.getContainerComponent())).canAccept()) {
        return each;
      }
    }
    return null;
  }

  private DockContainerFactory getFactory(String type) {
    assert myFactories.containsKey(type) : "No factory for content type=" + type;
    return myFactories.get(type);
  }

  public void createNewDockContainerFor(@NotNull DockableContent<?> content, @NotNull RelativePoint point) {
    DockContainer container = getFactory(content.getDockContainerType()).createContainer(content);

    Boolean canReopenWindow = content.getPresentation().getClientProperty(REOPEN_WINDOW);
    boolean reopenWindow = canReopenWindow == null || canReopenWindow;
    DockWindow window = createWindowFor(getWindowDimensionKey(content), null, container, reopenWindow);
    boolean isNorthPanelAvailable = (content instanceof EditorTabbedContainer.DockableEditor)
                                    ? ((EditorTabbedContainer.DockableEditor)content).isNorthPanelAvailable()
                                    : isNorthPanelVisible(UISettings.getInstance());
    if (isNorthPanelAvailable) {
      window.setupNorthPanel();
    }

    Boolean canDockToolWindows = content.getPresentation().getClientProperty(ALLOW_DOCK_TOOL_WINDOWS);
    if (canDockToolWindows == null || canDockToolWindows) {
      window.setupToolWindowPane();
    }

    Dimension size = content.getPreferredSize();

    // The given relative point might be relative to a component on a different screen, using different DPI screen coordinates. Convert to
    // device coordinates first. Ideally, we would be given a DevicePoint
    Point showPoint = new DevicePoint(point).getLocationOnScreen();
    showPoint.x -= size.width / 2;
    showPoint.y -= size.height / 2;

    Rectangle target = new Rectangle(showPoint, size);
    ScreenUtil.moveRectangleToFitTheScreen(target);
    ScreenUtil.cropRectangleToFitTheScreen(target);

    window.setLocation(target.getLocation());
    window.myDockContentUiContainer.setPreferredSize(target.getSize());

    window.show(false);
    window.getFrame().pack();

    container.add(content, new RelativePoint(target.getLocation()));

    SwingUtilities.invokeLater(() -> window.myUiContainer.setPreferredSize(null));
  }

  public @NotNull Pair<FileEditor[], FileEditorProvider[]> createNewDockContainerFor(@NotNull VirtualFile file,
                                                                                     @NotNull FileEditorManagerImpl fileEditorManager) {
    DockContainer container = getFactory(DockableEditorContainerFactory.TYPE).createContainer(null);

    // Order is important here. Create the dock window, then create the editor window. That way, any listeners can check to see if the
    // parent window is floating.
    DockWindow window = createWindowFor(getWindowDimensionKey(file), null, container, REOPEN_WINDOW.get(file, true));
    if (!ApplicationManager.getApplication().isHeadlessEnvironment() && !ApplicationManager.getApplication().isUnitTestMode()) {
      window.show(true);
    }

    EditorWindow editorWindow = ((DockableEditorTabbedContainer)container).getSplitters().getOrCreateCurrentWindow(file);
    Pair<FileEditor[], FileEditorProvider[]> result = fileEditorManager.openFileImpl2(editorWindow, file, true);

    if (!isSingletonEditorInWindow(result.first)) {
      window.setupToolWindowPane();
    }

    boolean isNorthPanelAvailable = isNorthPanelAvailable(result.first);
    if (isNorthPanelAvailable) {
      window.setupNorthPanel();
    }
    container.add(EditorTabbedContainer
                    .createDockableEditor(myProject, null, file, new Presentation(file.getName()), editorWindow, isNorthPanelAvailable),
                  null);

    SwingUtilities.invokeLater(() -> window.myUiContainer.setPreferredSize(null));
    return result;
  }

  public static boolean isSingletonEditorInWindow(FileEditor[] editors) {
    for (FileEditor editor : editors) {
      if (FileEditorManagerImpl.SINGLETON_EDITOR_IN_WINDOW.get(editor, false)
        || EditorWindow.HIDE_TABS.get(editor, false)) {
        return true;
      }
    }
    return false;
  }

  private static @Nullable String getWindowDimensionKey(@NotNull DockableContent<?> content) {
    if (content instanceof EditorTabbedContainer.DockableEditor){
      VirtualFile contentFile = ((EditorTabbedContainer.DockableEditor)content).getFile();
      return getWindowDimensionKey(contentFile);
    }

    return null;
  }

  private static @Nullable String getWindowDimensionKey(@NotNull VirtualFile file) {
    return WINDOW_DIMENSION_KEY.get(file);
  }

  private @NotNull DockWindow createWindowFor(@Nullable String dimensionKey,
                                              @Nullable String id,
                                              @NotNull DockContainer container,
                                              boolean canReopenWindow) {
    String windowId = id != null ? id : Integer.toString(myWindowIdCounter++);
    DockWindow window =
      new DockWindow(dimensionKey, windowId, myProject, container, container instanceof DockContainer.Dialog, canReopenWindow);
    containerToWindow.put(container, window);
    myWindows.put(windowId, window);
    return window;
  }

  private @NotNull DockWindow getOrCreateWindowFor(@NotNull String id,
                                                   @NotNull DockContainer container) {
    final DockWindow existingWindow = myWindows.get(id);
    if (existingWindow != null) {
      final DockContainer oldContainer = existingWindow.replaceContainer(container);
      containerToWindow.remove(oldContainer);
      containerToWindow.put(container, existingWindow);

      if (oldContainer instanceof Disposable) {
        Disposer.dispose((Disposable)oldContainer);
      }

      return existingWindow;
    }

    return createWindowFor(null, id, container, true);
  }

  public static boolean isNorthPanelVisible(@NotNull UISettings uiSettings) {
    return uiSettings.getShowNavigationBar()
           && !uiSettings.getPresentationMode();
  }

  public static boolean isNorthPanelAvailable(FileEditor @NotNull [] editors) {
    boolean defaultNorthPanelVisible = isNorthPanelVisible(UISettings.getInstance());
    for (FileEditor editor : editors) {
      if (SHOW_NORTH_PANEL.isIn(editor)) {
        return SHOW_NORTH_PANEL.get(editor, defaultNorthPanelVisible);
      }
    }

    return defaultNorthPanelVisible;
  }

  private final class DockWindow extends FrameWrapper implements IdeEventQueue.EventDispatcher {
    private final String myId;
    private boolean myNorthPanelAvailable;
    private final boolean mySupportReopen;
    private DockContainer myContainer;

    private final VerticalBox myNorthPanel = new VerticalBox();
    private final Map<String, IdeRootPaneNorthExtension> myNorthExtensions = new LinkedHashMap<>();

    private final NonOpaquePanel myUiContainer;
    private final JPanel myCenterPanel = new JPanel(new BorderLayout(0, 2));
    private final JPanel myDockContentUiContainer;
    @Nullable private ToolWindowPane myToolWindowPane = null;

    private DockWindow(@Nullable String dimensionKey,
                       @Nullable String id,
                       @NotNull Project project,
                       @NotNull DockContainer container,
                       boolean isDialog,
                       boolean supportReopen) {
      super(project, dimensionKey != null ? dimensionKey : "dock-window-" + id, isDialog);

      myId = id;
      myContainer = container;
      setProject(project);

      mySupportReopen = supportReopen;

      if (!ApplicationManager.getApplication().isHeadlessEnvironment() && !(container instanceof DockContainer.Dialog)) {
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
        if (statusBar != null) {
          Window frame = getFrame();
          if (frame instanceof IdeFrame) {
            setStatusBar(statusBar.createChild((IdeFrame)frame));
          }
        }
      }

      myUiContainer = new NonOpaquePanel(new BorderLayout());

      myCenterPanel.setOpaque(false);

      myDockContentUiContainer = new JPanel(new BorderLayout());
      myDockContentUiContainer.setOpaque(false);

      myDockContentUiContainer.add(myContainer.getContainerComponent(), BorderLayout.CENTER);
      myCenterPanel.add(myDockContentUiContainer, BorderLayout.CENTER);
      myUiContainer.add(myCenterPanel, BorderLayout.CENTER);

      StatusBar statusBar = getStatusBar();
      if (statusBar != null) {
        myUiContainer.add(statusBar.getComponent(), BorderLayout.SOUTH);
      }

      setComponent(myUiContainer);

      IdeEventQueue.getInstance().addPostprocessor(this, this);

      myContainer.addListener(new DockContainer.Listener() {
        @Override
        public void contentRemoved(Object key) {
          getReady().doWhenDone(() -> closeIfEmpty());
        }
      }, this);
    }

    private void setupToolWindowPane() {
      if (ApplicationManager.getApplication().isUnitTestMode() || !(getFrame() instanceof JFrame)) {
        return;
      }

      if (myToolWindowPane != null) {
        return;
      }

      final String paneId = Objects.requireNonNull(getDimensionKey());

      final ToolWindowButtonManager buttonManager;
      if (ExperimentalUI.isNewUI()) {
        buttonManager = new ToolWindowPaneNewButtonManager(paneId, false);
        buttonManager.add(myDockContentUiContainer);
        buttonManager.initMoreButton();
      }
      else {
        buttonManager = new ToolWindowPaneOldButtonManager(paneId);
      }

      final JComponent containerComponent = myContainer.getContainerComponent();
      myToolWindowPane = new ToolWindowPane((JFrame)getFrame(), this, paneId, buttonManager);
      myToolWindowPane.setDocumentComponent(containerComponent);

      myDockContentUiContainer.remove(containerComponent);
      myDockContentUiContainer.add(myToolWindowPane, BorderLayout.CENTER);

      // Close the container if it's empty, and we've just removed the last tool window
      myProject.getMessageBus().connect(this).subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
        @Override
        public void stateChanged(@NotNull ToolWindowManager toolWindowManager,
                                 @NotNull ToolWindowManagerListener.ToolWindowManagerEventType eventType) {
          if (eventType == ToolWindowManagerEventType.ActivateToolWindow
              || eventType == ToolWindowManagerEventType.MovedOrResized
              || eventType == ToolWindowManagerEventType.SetContentUiType) {
            return;
          }
          getReady().doWhenDone(() -> closeIfEmpty());
        }
      });
    }

    private DockContainer replaceContainer(DockContainer container) {
      final JComponent newContainerComponent = container.getContainerComponent();
      if (myToolWindowPane != null) {
        myToolWindowPane.setDocumentComponent(newContainerComponent);
      }
      else {
        myDockContentUiContainer.remove(myContainer.getContainerComponent());
        myDockContentUiContainer.add(newContainerComponent);
      }
      final DockContainer oldContainer = myContainer;
      myContainer = container;
      if (getFrame().isVisible() && myContainer instanceof Activatable) {
        ((Activatable)myContainer).showNotify();
      }
      return oldContainer;
    }

    private void closeIfEmpty() {
      if (myContainer.isEmpty() && (myToolWindowPane == null || !myToolWindowPane.buttonManager.hasButtons())) {
        close();
        myContainers.remove(myContainer);
      }
    }

    private void setupNorthPanel() {
      if (myNorthPanelAvailable) {
        return;
      }
      myCenterPanel.add(myNorthPanel, BorderLayout.NORTH);
      myNorthPanelAvailable = true;
      myProject.getMessageBus().connect(this)
        .subscribe(UISettingsListener.TOPIC, uiSettings -> updateNorthPanel(isNorthPanelVisible(uiSettings)));
      updateNorthPanel(isNorthPanelVisible(UISettings.getInstance()));
    }

    @Override
    protected IdeRootPaneNorthExtension getNorthExtension(String key) {
      return myNorthExtensions.get(key);
    }

    private void updateNorthPanel(boolean visible) {
      if (ApplicationManager.getApplication().isUnitTestMode() || !myNorthPanelAvailable) {
        return;
      }

      myNorthPanel.setVisible(!(myContainer instanceof DockContainer.Dialog) && visible);

      Set<String> processedKeys = new HashSet<>();
      for (IdeRootPaneNorthExtension each : IdeRootPaneNorthExtension.EP_NAME.getExtensions(myProject)) {
        processedKeys.add(each.getKey());
        if (myNorthExtensions.containsKey(each.getKey())) {
          continue;
        }
        IdeRootPaneNorthExtension toInstall = each.copy();
        if(toInstall != null) {
          myNorthExtensions.put(toInstall.getKey(), toInstall);
          myNorthPanel.add(toInstall.getComponent());
        }
      }

      Iterator<String> existing = myNorthExtensions.keySet().iterator();
      while (existing.hasNext()) {
        String each = existing.next();
        if (processedKeys.contains(each)) {
          continue;
        }

        IdeRootPaneNorthExtension toRemove = myNorthExtensions.get(each);
        myNorthPanel.remove(toRemove.getComponent());
        existing.remove();
        if (toRemove instanceof Disposable) {
          Disposer.dispose((Disposable)toRemove);
        }
      }

      myNorthPanel.revalidate();
      myNorthPanel.repaint();
    }

    public void setTransparent(boolean transparent) {
      if (transparent) {
        WindowManagerEx.getInstanceEx().setAlphaModeEnabled(getFrame(), true);
        WindowManagerEx.getInstanceEx().setAlphaModeRatio(getFrame(), 0.5f);
      }
      else {
        WindowManagerEx.getInstanceEx().setAlphaModeEnabled(getFrame(), true);
        WindowManagerEx.getInstanceEx().setAlphaModeRatio(getFrame(), 0f);
      }
    }

    @Override
    public void dispose() {
      super.dispose();
      final DockWindow removed = containerToWindow.remove(myContainer);
      myWindows.remove(removed.myId);
      if (myContainer instanceof Disposable) {
        Disposer.dispose((Disposable)myContainer);
      }
      for (IdeRootPaneNorthExtension each : myNorthExtensions.values()) {
        if (each instanceof Disposable) {
          Disposer.dispose((Disposable)each);
        }
      }
      myNorthExtensions.clear();
    }

    @Override
    public boolean dispatch(@NotNull AWTEvent e) {
      if (e instanceof KeyEvent) {
        if (myCurrentDragSession != null) {
          stopCurrentDragSession();
        }
      }
      return false;
    }

    @Override
    protected @NotNull JFrame createJFrame(@NotNull IdeFrame parent) {
      JFrame frame = super.createJFrame(parent);
      installListeners(frame);
      return frame;
    }

    @Override
    protected @NotNull JDialog createJDialog(@NotNull IdeFrame parent) {
      JDialog frame = super.createJDialog(parent);
      installListeners(frame);
      return frame;
    }

    private void installListeners(@NotNull Window frame) {
      UiNotifyConnector uiNotifyConnector = myContainer instanceof Activatable
                                            ? new UiNotifyConnector(((RootPaneContainer)frame).getContentPane(), (Activatable)myContainer)
                                            : null;
      frame.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          myContainer.closeAll();
          if (uiNotifyConnector != null) {
            Disposer.dispose(uiNotifyConnector);
          }
        }
      });
    }
  }

  @Override
  public Element getState() {
    Element root = new Element("state");
    for (DockContainer each : getAllContainers()) {
      DockWindow eachWindow = containerToWindow.get(each);
      if (eachWindow != null && eachWindow.mySupportReopen && each instanceof DockContainer.Persistent) {
        DockContainer.Persistent eachContainer = (DockContainer.Persistent)each;
        Element eachWindowElement = new Element("window");
        String id = eachWindow.myId;
        if (id != null) {
          eachWindowElement.setAttribute("id", id);
        }
        eachWindowElement.setAttribute("withNorthPanel", Boolean.toString(eachWindow.myNorthPanelAvailable));
        eachWindowElement.setAttribute("withToolWindowPane", Boolean.toString(eachWindow.myToolWindowPane != null));
        Element content = new Element("content");
        content.setAttribute("type", eachContainer.getDockContainerType());
        content.addContent(eachContainer.getState());
        eachWindowElement.addContent(content);

        root.addContent(eachWindowElement);
      }
    }
    return root;
  }

  private @NotNull Iterable<DockContainer> getAllContainers() {
    return ContainerUtil.concat(myContainers, containerToWindow.keySet());
  }

  @Override
  public void loadState(@NotNull Element state) {
    myLoadedState = state;
  }

  private void readStateFor(@NotNull String type) {
    if (myLoadedState == null) {
      return;
    }

    for (Element windowElement : myLoadedState.getChildren("window")) {
      Element eachContent = windowElement.getChild("content");
      if (eachContent == null) {
        continue;
      }

      String eachType = eachContent.getAttributeValue("type");
      if (!type.equals(eachType) || !myFactories.containsKey(eachType)) {
        continue;
      }

      DockContainerFactory factory = myFactories.get(eachType);
      if (!(factory instanceof DockContainerFactory.Persistent)) {
        continue;
      }

      DockContainer container = ((DockContainerFactory.Persistent)factory).loadContainerFrom(eachContent);

      // If the window already exists, reuse it, otherwise create it. This handles changes in tasks & contexts. When we clear the current
      // context, all open editors are closed, but a floating editor container with a docked tool window will remain open. Switching to a
      // new context will reuse the window and open editors in that editor container. If the new context doesn't use this window, or it's
      // a default clean context, the window will remain open, containing only the docked tool windows.
      DockWindow window = getOrCreateWindowFor(windowElement.getAttributeValue("id"), container);

      // If the window already exists, we can't remove the north panel or tool window pane, but we can add them
      boolean withNorthPanel = Boolean.parseBoolean(windowElement.getAttributeValue("withNorthPanel", Boolean.toString(true)));
      if (withNorthPanel) {
        window.setupNorthPanel();
      }
      boolean withToolWindowPane = Boolean.parseBoolean(windowElement.getAttributeValue("withToolWindowPane", Boolean.toString(true)));
      if (withToolWindowPane) {
        window.setupToolWindowPane();
      }

      // If the window exists, it's already visible. Don't show multiple times as this will set up additional listeners and window decoration
      UIUtil.invokeLaterIfNeeded(() -> {
        if (!window.getFrame().isVisible()) {
          window.show();
        }
      });
    }
  }
}
