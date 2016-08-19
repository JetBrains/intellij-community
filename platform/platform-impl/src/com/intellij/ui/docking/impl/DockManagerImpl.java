/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.impl.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.ui.docking.*;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
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
import java.util.List;

@State(name = "DockManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))

public class DockManagerImpl extends DockManager implements PersistentStateComponent<Element> {

  private final Project myProject;

  private final Map<String, DockContainerFactory> myFactories = new HashMap<>();

  private final Set<DockContainer> myContainers = new HashSet<>();

  private final MutualMap<DockContainer, DockWindow> myWindows = new MutualMap<>();

  private MyDragSession myCurrentDragSession;

  private final BusyObject.Impl myBusyObject = new BusyObject.Impl() {
    @Override
    public boolean isReady() {
      return myCurrentDragSession == null;
    }
  };

  private int myWindowIdCounter = 1;

  private Element myLoadedState;

  public DockManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public void register(final DockContainer container) {
    myContainers.add(container);
    Disposer.register(container, new Disposable() {
      @Override
      public void dispose() {
        myContainers.remove(container);
      }
    });
  }

  @Override
  public void register(final String id, DockContainerFactory factory) {
    myFactories.put(id, factory);
    Disposer.register(factory, new Disposable() {
      @Override
      public void dispose() {
        myFactories.remove(id);
      }
    });

    readStateFor(id);
  }

  public void readState() {
    for (String id : myFactories.keySet()) {
      readStateFor(id);
    }
  }

  @Override
  public Set<DockContainer> getContainers() {
    return Collections.unmodifiableSet(ContainerUtil.newHashSet(myContainers));
  }

  @Override
  public IdeFrame getIdeFrame(DockContainer container) {
    Component parent = UIUtil.findUltimateParent(container.getContainerComponent());
    if (parent instanceof IdeFrame) {
      return (IdeFrame)parent;
    }
    return null;
  }

  @Override
  public String getDimensionKeyForFocus(@NotNull String key) {
    Component owner = IdeFocusManager.getInstance(myProject).getFocusOwner();
    if (owner == null) return key;

    DockWindow wnd = myWindows.getValue(getContainerFor(owner));

    return wnd != null ? key + "#" + wnd.myId : key;
  }

  @Override
  public DockContainer getContainerFor(Component c) {
    if (c == null) return null;

    for (DockContainer eachContainer : myContainers) {
      if (SwingUtilities.isDescendingFrom(c, eachContainer.getContainerComponent())) {
        return eachContainer;
      }
    }

    Component parent = UIUtil.findUltimateParent(c);
    if (parent == null) return null;

    for (DockContainer eachContainer : myContainers) {
      if (parent == UIUtil.findUltimateParent(eachContainer.getContainerComponent())) {
        return eachContainer;
      }
    }

    return null;
  }

  @Override
  public DragSession createDragSession(MouseEvent mouseEvent, @NotNull DockableContent content) {
    stopCurrentDragSession();

    for (DockContainer each : myContainers) {
      if (each.isEmpty() && each.isDisposeWhenEmpty()) {
        DockWindow window = myWindows.getValue(each);
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

      for (DockContainer each : myContainers) {
        if (!each.isEmpty()) {
          DockWindow window = myWindows.getValue(each);
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

  private class MyDragSession implements DragSession {

    private final JWindow myWindow;

    private Image myDragImage;
    private final Image myDefaultDragImage;

    @NotNull
    private final DockableContent myContent;

    private DockContainer myCurrentOverContainer;
    private final JLabel myImageContainer;

    private MyDragSession(MouseEvent me, @NotNull DockableContent content) {
      myWindow = new JWindow();
      myContent = content;

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

      BufferedImage buffer = UIUtil.createImage((int)width, (int)height, BufferedImage.TYPE_INT_ARGB);
      buffer.createGraphics().drawImage(previewImage, 0, 0, (int)width, (int)height, null);

      myDefaultDragImage = buffer.getScaledInstance((int)(width * ratio), (int)(height * ratio), Image.SCALE_SMOOTH);
      myDragImage = myDefaultDragImage;

      myWindow.getContentPane().setLayout(new BorderLayout());
      myImageContainer = new JLabel(new ImageIcon(myDragImage));
      myImageContainer.setBorder(new LineBorder(Color.lightGray));
      myWindow.getContentPane().add(myImageContainer, BorderLayout.CENTER);

      setLocationFrom(me);

      myWindow.setVisible(true);

      WindowManagerEx.getInstanceEx().setAlphaModeEnabled(myWindow, true);
      WindowManagerEx.getInstanceEx().setAlphaModeRatio(myWindow, 0.1f);
      myWindow.getRootPane().putClientProperty("Window.shadow", Boolean.FALSE);
    }

    private void setLocationFrom(MouseEvent me) {
      Point showPoint = me.getPoint();
      SwingUtilities.convertPointToScreen(showPoint, me.getComponent());

      showPoint.x -= myDragImage.getWidth(null) / 2;
      showPoint.y += 10;
      myWindow.setBounds(new Rectangle(showPoint, new Dimension(myDragImage.getWidth(null), myDragImage.getHeight(null))));
    }

    @NotNull
    @Override
    public DockContainer.ContentResponse getResponse(MouseEvent e) {
      RelativePoint point = new RelativePoint(e);
      for (DockContainer each : myContainers) {
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
          myImageContainer.setIcon(new ImageIcon(myDragImage));
          myWindow.pack();
        }

        setLocationFrom(e);
      }
      else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
        if (myCurrentOverContainer == null) {
          createNewDockContainerFor(myContent, point);
          stopCurrentDragSession();
        }
        else {
          myCurrentOverContainer.add(myContent, point);
          stopCurrentDragSession();
        }
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

  @Nullable
  private DockContainer findContainerFor(RelativePoint point, @NotNull DockableContent content) {
    for (DockContainer each : myContainers) {
      RelativeRectangle rec = each.getAcceptArea();
      if (rec.contains(point) && each.getContentResponse(content, point).canAccept()) {
        return each;
      }
    }

    for (DockContainer each : myContainers) {
      RelativeRectangle rec = each.getAcceptAreaFallback();
      if (rec.contains(point) && each.getContentResponse(content, point).canAccept()) {
        return each;
      }
    }

    return null;
  }


  private DockContainerFactory getFactory(String type) {
    assert myFactories.containsKey(type) : "No factory for content type=" + type;
    return myFactories.get(type);
  }

  public void createNewDockContainerFor(DockableContent content, RelativePoint point) {
    DockContainer container = getFactory(content.getDockContainerType()).createContainer(content);
    register(container);

    final DockWindow window = createWindowFor(null, container);

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

  @NotNull
  public Pair<FileEditor[], FileEditorProvider[]> createNewDockContainerFor(@NotNull VirtualFile file, @NotNull FileEditorManagerImpl fileEditorManager) {
    DockContainer container = getFactory(DockableEditorContainerFactory.TYPE).createContainer(null);
    register(container);

    final DockWindow window = createWindowFor(null, container);

    window.show(true);
    final EditorWindow editorWindow = ((DockableEditorTabbedContainer)container).getSplitters().getOrCreateCurrentWindow(file);
    final Pair<FileEditor[], FileEditorProvider[]> result = fileEditorManager.openFileImpl2(editorWindow, file, true);
    container.add(EditorTabbedContainer.createDockableEditor(myProject, null, file, new Presentation(file.getName()), editorWindow), null);

    SwingUtilities.invokeLater(() -> window.myUiContainer.setPreferredSize(null));
    return result;
  }

  private DockWindow createWindowFor(@Nullable String id, DockContainer container) {
    String windowId = id != null ? id : String.valueOf(myWindowIdCounter++);
    DockWindow window = new DockWindow(windowId, myProject, container, container instanceof DockContainer.Dialog);
    window.setDimensionKey("dock-window-" + windowId);
    myWindows.put(container, window);
    return window;
  }

  private class DockWindow extends FrameWrapper implements IdeEventQueue.EventDispatcher {

    private final String myId;
    private final DockContainer myContainer;

    private final VerticalBox myNorthPanel = new VerticalBox();
    private final Map<String, IdeRootPaneNorthExtension> myNorthExtensions = new LinkedHashMap<>();

    private final NonOpaquePanel myUiContainer;
    private final NonOpaquePanel myDockContentUiContainer;

    private DockWindow(String id, Project project, DockContainer container, boolean dialog) {
      super(project, null, dialog);
      myId = id;
      myContainer = container;
      setProject(project);

      if (!(container instanceof DockContainer.Dialog)) {
        setStatusBar(WindowManager.getInstance().getStatusBar(project).createChild());
      }

      myUiContainer = new NonOpaquePanel(new BorderLayout());

      NonOpaquePanel center = new NonOpaquePanel(new BorderLayout(0, 2));
      if (UIUtil.isUnderAquaLookAndFeel()) {
        center.setOpaque(true);
        center.setBackground(JBTabsImpl.MAC_AQUA_BG_COLOR);
      }

      center.add(myNorthPanel, BorderLayout.NORTH);

      myDockContentUiContainer = new NonOpaquePanel(new BorderLayout());
      myDockContentUiContainer.add(myContainer.getContainerComponent(), BorderLayout.CENTER);
      center.add(myDockContentUiContainer, BorderLayout.CENTER);

      myUiContainer.add(center, BorderLayout.CENTER);
      if (myStatusBar != null) {
        myUiContainer.add(myStatusBar.getComponent(), BorderLayout.SOUTH);
      }

      setComponent(myUiContainer);
      addDisposable(container);

      IdeEventQueue.getInstance().addPostprocessor(this, this);

      myContainer.addListener(new DockContainer.Listener.Adapter() {
        @Override
        public void contentRemoved(Object key) {
          getReady().doWhenDone(() -> {
            if (myContainer.isEmpty()) {
              close();
            }
          });
        }
      }, this);

      UISettings.getInstance().addUISettingsListener(new UISettingsListener() {
        @Override
        public void uiSettingsChanged(UISettings source) {
          updateNorthPanel();
        }
      }, this);

      updateNorthPanel();
    }


    @Override
    protected IdeRootPaneNorthExtension getNorthExtension(String key) {
      return myNorthExtensions.get(key);
    }

    private void updateNorthPanel() {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;
      myNorthPanel.setVisible(UISettings.getInstance().SHOW_NAVIGATION_BAR
                              && !(myContainer instanceof DockContainer.Dialog)
                              && !UISettings.getInstance().PRESENTATION_MODE);

      IdeRootPaneNorthExtension[] extensions =
        Extensions.getArea(myProject).getExtensionPoint(IdeRootPaneNorthExtension.EP_NAME).getExtensions();
      HashSet<String> processedKeys = new HashSet<>();
      for (IdeRootPaneNorthExtension each : extensions) {
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
        Disposer.dispose(toRemove);
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
      myWindows.remove(myContainer);

      for (IdeRootPaneNorthExtension each : myNorthExtensions.values()) {
        Disposer.dispose(each);
      }
      myNorthExtensions.clear();
    }

    @Override
    public boolean dispatch(AWTEvent e) {
      if (e instanceof KeyEvent) {
        if (myCurrentDragSession != null) {
          stopCurrentDragSession();
        }
      }
      return false;
    }

    @Override
    protected JFrame createJFrame(IdeFrame parent) {
      JFrame frame = super.createJFrame(parent);
      installListeners(frame);

      return frame;
    }

    @Override
    protected JDialog createJDialog(IdeFrame parent) {
      JDialog frame = super.createJDialog(parent);
      installListeners(frame);

      return frame;
    }

    private void installListeners(Window frame) {
      frame.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          myContainer.closeAll();
        }
      });

      UiNotifyConnector connector = new UiNotifyConnector(((RootPaneContainer)frame).getContentPane(), myContainer);
      Disposer.register(myContainer, connector);
    }
  }


  @Override
  public Element getState() {
    Element root = new Element("DockManager");
    for (DockContainer each : myContainers) {
      DockWindow eachWindow = myWindows.getValue(each);
      if (eachWindow != null) {
        if (each instanceof DockContainer.Persistent) {
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
    }
    return root;
  }

  @Override
  public void loadState(Element state) {
    myLoadedState = state;
  }

  private void readStateFor(String type) {
    if (myLoadedState == null) return;

    List windows = myLoadedState.getChildren("window");
    for (Object window1 : windows) {
      Element eachWindow = (Element)window1;
      if (eachWindow == null) continue;

      String eachId = eachWindow.getAttributeValue("id");

      Element eachContent = eachWindow.getChild("content");
      if (eachContent == null) continue;

      String eachType = eachContent.getAttributeValue("type");
      if (eachType == null || !type.equals(eachType) || !myFactories.containsKey(eachType)) continue;

      DockContainerFactory factory = myFactories.get(eachType);
      if (!(factory instanceof DockContainerFactory.Persistent)) continue;

      DockContainerFactory.Persistent persistentFactory = (DockContainerFactory.Persistent)factory;
      DockContainer container = persistentFactory.loadContainerFrom(eachContent);
      register(container);

      final DockWindow window = createWindowFor(eachId, container);
      UIUtil.invokeLaterIfNeeded(() -> window.show());
    }
  }
}
