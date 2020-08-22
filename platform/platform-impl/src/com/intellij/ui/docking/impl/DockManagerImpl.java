// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.ui.docking.*;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.*;

@State(name = "DockManager", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
public final class DockManagerImpl extends DockManager implements PersistentStateComponent<Element> {
  private final Project myProject;

  private final Map<String, DockContainerFactory> myFactories = new HashMap<>();
  private final Set<DockContainer> myContainers = new HashSet<>();
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

  public DockManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void register(@NotNull DockContainer container) {
    if (container instanceof Disposable) {
      register(container, (Disposable)container);
    }
    else {
      myContainers.add(container);
    }
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

    DockWindow window = containerToWindow.get(getContainerFor(owner));
    return window != null ? key + "#" + window.myId : key;
  }

  @Override
  public DockContainer getContainerFor(Component c) {
    if (c == null) {
      return null;
    }

    for (DockContainer eachContainer : getAllContainers()) {
      if (SwingUtilities.isDescendingFrom(c, eachContainer.getContainerComponent())) {
        return eachContainer;
      }
    }

    Component parent = UIUtil.findUltimateParent(c);
    for (DockContainer eachContainer : getAllContainers()) {
      if (parent == UIUtil.findUltimateParent(eachContainer.getContainerComponent())) {
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

    private DockContainer myStartDragContainer;
    private DockContainer myCurrentOverContainer;
    private final JLabel myImageContainer;

    private MyDragSession(MouseEvent me, @NotNull DockableContent content) {
      myWindow = new JDialog(UIUtil.getWindow(me.getComponent()));
      myWindow.setUndecorated(true);
      myContent = content;
      myStartDragContainer = getContainerFor(me.getComponent());

      Image previewImage = content.getPreviewImage();

      double requiredSize = 220;

      double width = previewImage.getWidth(null);
      double height = previewImage.getHeight(null);

      double ratio;
      if (width > height) {
        ratio = requiredSize / width;
      }
      else {
        ratio = requiredSize / height;
      }

      BufferedImage buffer = UIUtil.createImage(myWindow, (int)width, (int)height, BufferedImage.TYPE_INT_ARGB);
      buffer.createGraphics().drawImage(previewImage, 0, 0, (int)width, (int)height, null);

      myDefaultDragImage = buffer.getScaledInstance((int)(width * ratio), (int)(height * ratio), Image.SCALE_SMOOTH);
      myDragImage = myDefaultDragImage;

      myWindow.getContentPane().setLayout(new BorderLayout());
      myImageContainer = new JLabel(IconUtil.createImageIcon(myDragImage));
      myImageContainer.setBorder(new LineBorder(JBColor.LIGHT_GRAY));
      myWindow.getContentPane().add(myImageContainer, BorderLayout.CENTER);

      setLocationFrom(me);

      myWindow.setVisible(true);

      WindowManagerEx windowManager = WindowManagerEx.getInstanceEx();
      windowManager.setAlphaModeEnabled(myWindow, true);
      windowManager.setAlphaModeRatio(myWindow, 0.1f);
      myWindow.getRootPane().putClientProperty("Window.shadow", Boolean.FALSE);
    }

    private void setLocationFrom(MouseEvent me) {
      Point showPoint = me.getPoint();
      SwingUtilities.convertPointToScreen(showPoint, me.getComponent());

      Dimension size = myImageContainer.getSize();
      showPoint.x -= size.width / 2;
      showPoint.y -= size.height / 2;
      myWindow.setBounds(new Rectangle(showPoint, size));
    }

    @Override
    public @NotNull DockContainer.ContentResponse getResponse(MouseEvent e) {
      RelativePoint point = new RelativePoint(e);
      for (DockContainer each : getAllContainers()) {
        RelativeRectangle rec = each.getAcceptArea();
        if (rec.contains(point)) {
          DockContainer.ContentResponse response = each.getContentResponse(myContent, point);
          if (response.canAccept()) {
            return response;
          }
        }
      }
      return DockContainer.ContentResponse.DENY;
    }

    @Override
    public void process(MouseEvent e) {
      RelativePoint point = new RelativePoint(e);

      Image img = null;
      if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
        DockContainer over = findContainerFor(point, myContent);
        if (myCurrentOverContainer != null && myCurrentOverContainer != over) {
          myCurrentOverContainer.resetDropOver(myContent);
          myCurrentOverContainer = null;
        }

        if (myCurrentOverContainer == null && over != null) {
          myCurrentOverContainer = over;
          img = myCurrentOverContainer.startDropOver(myContent, point);
        }

        if (myCurrentOverContainer != null) {
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
          createNewDockContainerFor(myContent, point);
        }
        else {
          myCurrentOverContainer.add(myContent, point);
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

  private @Nullable DockContainer findContainerFor(RelativePoint point, @NotNull DockableContent<?> content) {
    DockContainer candidate = null;
    for (DockContainer each : getContainers()) {
      RelativeRectangle rec = each.getAcceptArea();
      if (rec.contains(point) && each.getContentResponse(content, point).canAccept()) {
        Window window = UIUtil.getWindow(each.getContainerComponent());
        if (window != null && window.isActive()) return each;
        if (candidate == null || Comparing.equal(candidate, myCurrentDragSession.myStartDragContainer)) {
          candidate = each;
        }
      }
    }

    for (DockContainer each : getContainers()) {
      RelativeRectangle rec = each.getAcceptAreaFallback();
      if (rec.contains(point) && each.getContentResponse(content, point).canAccept()) {
        Window window = UIUtil.getWindow(each.getContainerComponent());
        if (window != null && window.isActive()) return candidate;
        if (candidate == null || Comparing.equal(candidate, myCurrentDragSession.myStartDragContainer)) {
          candidate = each;
        }
      }
    }

    return candidate;
  }

  private DockContainerFactory getFactory(String type) {
    assert myFactories.containsKey(type) : "No factory for content type=" + type;
    return myFactories.get(type);
  }

  public void createNewDockContainerFor(@NotNull DockableContent<?> content, @NotNull RelativePoint point) {
    DockContainer container = getFactory(content.getDockContainerType()).createContainer(content);

    DockWindow window = createWindowFor(null, container);

    Dimension size = content.getPreferredSize();
    Point showPoint = point.getScreenPoint();
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

    DockWindow window = createWindowFor(null, container);
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      window.show(true);
    }

    EditorWindow editorWindow = ((DockableEditorTabbedContainer)container).getSplitters().getOrCreateCurrentWindow(file);
    Pair<FileEditor[], FileEditorProvider[]> result = fileEditorManager.openFileImpl2(editorWindow, file, true);
    container.add(EditorTabbedContainer.createDockableEditor(myProject, null, file, new Presentation(file.getName()), editorWindow), null);

    SwingUtilities.invokeLater(() -> window.myUiContainer.setPreferredSize(null));
    return result;
  }

  private @NotNull DockWindow createWindowFor(@Nullable String id, @NotNull DockContainer container) {
    String windowId = id != null ? id : Integer.toString(myWindowIdCounter++);
    DockWindow window = new DockWindow(windowId, myProject, container, container instanceof DockContainer.Dialog);
    containerToWindow.put(container, window);
    return window;
  }

  private final class DockWindow extends FrameWrapper implements IdeEventQueue.EventDispatcher {
    private final String myId;
    private final DockContainer myContainer;

    private final VerticalBox myNorthPanel = new VerticalBox();
    private final Map<String, IdeRootPaneNorthExtension> myNorthExtensions = new LinkedHashMap<>();

    private final NonOpaquePanel myUiContainer;
    private final JPanel myDockContentUiContainer;

    private DockWindow(String id, @NotNull Project project, DockContainer container, boolean isDialog) {
      super(project, "dock-window-" + id, isDialog);

      myId = id;
      myContainer = container;
      setProject(project);

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

      JPanel center = new JPanel(new BorderLayout(0, 2));
      center.setOpaque(false);
      center.add(myNorthPanel, BorderLayout.NORTH);

      myDockContentUiContainer = new JPanel(new BorderLayout());
      myDockContentUiContainer.setOpaque(false);
      myDockContentUiContainer.add(myContainer.getContainerComponent(), BorderLayout.CENTER);
      center.add(myDockContentUiContainer, BorderLayout.CENTER);

      myUiContainer.add(center, BorderLayout.CENTER);
      StatusBar statusBar = getStatusBar();
      if (statusBar != null) {
        myUiContainer.add(statusBar.getComponent(), BorderLayout.SOUTH);
      }

      setComponent(myUiContainer);

      IdeEventQueue.getInstance().addPostprocessor(this, this);

      myContainer.addListener(new DockContainer.Listener() {
        @Override
        public void contentRemoved(Object key) {
          getReady().doWhenDone(() -> {
            if (myContainer.isEmpty()) {
              close();
              myContainers.remove(myContainer);
            }
          });
        }
      }, this);

      project.getMessageBus().connect(this).subscribe(UISettingsListener.TOPIC, uiSettings -> updateNorthPanel());

      updateNorthPanel();
    }


    @Override
    protected IdeRootPaneNorthExtension getNorthExtension(String key) {
      return myNorthExtensions.get(key);
    }

    private void updateNorthPanel() {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return;
      }

      myNorthPanel.setVisible(UISettings.getInstance().getShowNavigationBar()
                              && !(myContainer instanceof DockContainer.Dialog)
                              && !UISettings.getInstance().getPresentationMode());

      Set<String> processedKeys = new HashSet<>();
      for (IdeRootPaneNorthExtension each : IdeRootPaneNorthExtension.EP_NAME.getExtensionList(myProject)) {
        processedKeys.add(each.getKey());
        if (myNorthExtensions.containsKey(each.getKey())) continue;
        IdeRootPaneNorthExtension toInstall = each.copy();
        myNorthExtensions.put(toInstall.getKey(), toInstall);
        myNorthPanel.add(toInstall.getComponent());
      }

      Iterator<String> existing = myNorthExtensions.keySet().iterator();
      while (existing.hasNext()) {
        String each = existing.next();
        if (processedKeys.contains(each)) continue;

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
      containerToWindow.remove(myContainer);
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
      UiNotifyConnector uiNotifyConnector = myContainer instanceof Activatable ? new UiNotifyConnector(((RootPaneContainer)frame).getContentPane(), (Activatable)myContainer) : null;
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
      if (eachWindow != null && each instanceof DockContainer.Persistent) {
        DockContainer.Persistent eachContainer = (DockContainer.Persistent)each;
        Element eachWindowElement = new Element("window");
        eachWindowElement.setAttribute("id", eachWindow.myId);
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
      DockWindow window = createWindowFor(windowElement.getAttributeValue("id"), container);
      UIUtil.invokeLaterIfNeeded(window::show);
    }
  }
}
