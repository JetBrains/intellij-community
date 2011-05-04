/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.MutualMap;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFocusManagerImpl;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.ui.docking.*;
import com.intellij.ui.tabs.impl.JBTabsImpl;
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

@State(
  name = "DockManager",
  storages = {@Storage(
    id = "other",
    file = "$WORKSPACE_FILE$")})

public class DockManagerImpl extends DockManager implements PersistentStateComponent<Element>{

  private Project myProject;

  private Map<String, DockContainerFactory> myFactories = new HashMap<String, DockContainerFactory>();

  private Set<DockContainer> myContainers = new HashSet<DockContainer>();

  private MutualMap<DockContainer, DockWindow> myWindows = new MutualMap<DockContainer, DockWindow>();

  private MyDragSession myCurrentDragSession;

  private BusyObject.Impl myBusyObject = new BusyObject.Impl() {
    @Override
    protected boolean isReady() {
      return myCurrentDragSession == null;
    }
  };

  private int myWindowIdCounter = 1;

  private Element myLoadedState;

  public DockManagerImpl(Project project) {
    myProject = project;
  }

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

  @Override
  public Set<DockContainer> getContainers() {
    return Collections.unmodifiableSet(myContainers);
  }

  @Override
  public IdeFrame getIdeFrame(DockContainer container) {
    Component parent = UIUtil.findUltimateParent(container.getComponent());
    if (parent instanceof IdeFrame) {
      return (IdeFrame)parent;
    }
    return null;
  }

  @Override
  public String getDimensionKeyForFocus(@NotNull String key) {
    Component owner = IdeFocusManagerImpl.getInstance(myProject).getFocusOwner();
    if (owner == null) return key;

    DockWindow wnd = myWindows.getValue(getContainerFor(owner));

    return wnd != null ? key + "#" + wnd.myId : key;
  }

  public DockContainer getContainerFor(Component c) {
    if (c == null) return null;

    for (DockContainer eachContainer : myContainers) {
      if (SwingUtilities.isDescendingFrom(c, eachContainer.getComponent())) {
        return eachContainer;
      }
    }

    Component parent = UIUtil.findUltimateParent(c);
    if (parent == null) return null;

    for (DockContainer eachContainer : myContainers) {
      if (parent == UIUtil.findUltimateParent(eachContainer.getComponent())) {
        return eachContainer;
      }
    }

    return null;
  }

  @Override
  public DragSession createDragSession(MouseEvent mouseEvent, DockableContent content) {
    stopCurrentDragSession();

    for (DockContainer each : myContainers) {
      if (each.isEmpty() && each.isDisposeWhenEmpty()) {
        DockWindow window = myWindows.getValue(each);
        if (window != null) {
          window.setTransparrent(true);
        }
      }
    }

    myCurrentDragSession = new MyDragSession(mouseEvent, content);
    return myCurrentDragSession;
  }


  private void stopCurrentDragSession() {
    if (myCurrentDragSession != null) {
      myCurrentDragSession.cancel();
      myCurrentDragSession = null;
      myBusyObject.onReady();

      for (DockContainer each : myContainers) {
        if (!each.isEmpty()) {
          DockWindow window = myWindows.getValue(each);
          if (window != null) {
            window.setTransparrent(false);
          }
        }
      }
    }
  }

  private ActionCallback getReady() {
    return myBusyObject.getReady(this);
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "DockManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  private class MyDragSession implements DragSession {

    private JWindow myWindow;

    private Image myDragImage;
    private Image myDefaultDragImage;

    private DockableContent myContent;

    private DockContainer myCurrentOverContainer;
    private JLabel myImageContainer;

    private MyDragSession(MouseEvent me, DockableContent content) {
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

      BufferedImage buffer = new BufferedImage((int)width, (int)height, BufferedImage.TYPE_INT_ARGB);
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
        } else {
          myCurrentOverContainer.add(myContent, point);
          stopCurrentDragSession();
        }
      }
    }

    public void cancel() {
      myWindow.dispose();

      if (myCurrentOverContainer != null) {
        myCurrentOverContainer.resetDropOver(myContent);
        myCurrentOverContainer = null;
      }
    }
  }

  @Nullable
  private DockContainer findContainerFor(RelativePoint point, DockableContent content) {
    for (DockContainer each : myContainers) {
      RelativeRectangle rec = each.getAcceptArea();
      if (rec.contains(point) && each.canAccept(content, point)) {
        return each;
      }
    }

    return null;
  }



  private DockContainerFactory getFactory(String type) {
    assert myFactories.containsKey(type) : "No factory for content type=" + type;
    return myFactories.get(type);
  }

  private void createNewDockContainerFor(DockableContent content, RelativePoint point) {
    DockContainer container = getFactory(content.getDockContainerType()).createContainer();
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

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        window.myUiContainer.setPreferredSize(null);
      }
    });
  }

  private DockWindow createWindowFor(@Nullable String id, DockContainer container) {
    String windowId = id != null ? id : String.valueOf(myWindowIdCounter++);
    DockWindow window = new DockWindow(windowId, myProject, container);
    window.setDimensionKey("dock-window-" + windowId);
    myWindows.put(container, window);
    return window;
  }

  private class DockWindow extends FrameWrapper implements IdeEventQueue.EventDispatcher {

    private String myId;
    private DockContainer myContainer;

    private VerticalBox myNorthPanel = new VerticalBox();
    private Map<String, IdeRootPaneNorthExtension> myNorthExtensions = new LinkedHashMap<String, IdeRootPaneNorthExtension>();

    private NonOpaquePanel myUiContainer;
    private NonOpaquePanel myDockContentUiContainer;

    private DockWindow(String id, Project project, DockContainer container) {
      super(project);
      myId = id;
      myContainer = container;
      setProject(project);

      setStatusBar(WindowManager.getInstance().getStatusBar(project).createChild());

      myUiContainer = new NonOpaquePanel(new BorderLayout());

      NonOpaquePanel center = new NonOpaquePanel(new BorderLayout(0, 2));
      if (UIUtil.isUnderAquaLookAndFeel()) {
        center.setOpaque(true);
        center.setBackground(JBTabsImpl.MAC_AQUA_BG_COLOR);
      }
      
      center.add(myNorthPanel, BorderLayout.NORTH);

      myDockContentUiContainer = new NonOpaquePanel(new BorderLayout());
      myDockContentUiContainer.add(myContainer.getComponent(), BorderLayout.CENTER);
      center.add(myDockContentUiContainer, BorderLayout.CENTER);

      myUiContainer.add(center, BorderLayout.CENTER);
      myUiContainer.add(myStatusBar.getComponent(), BorderLayout.SOUTH);

      setComponent(myUiContainer);
      addDisposable(container);

      IdeEventQueue.getInstance().addPostprocessor(this, this);

      myContainer.addListener(new DockContainer.Listener.Adapter() {
        @Override
        public void contentRemoved(Object key) {
          getReady().doWhenDone(new Runnable() {
            @Override
            public void run() {
              if (myContainer.isEmpty()) {
                close();
              }
            }
          });
        }
      }, this);

      Extensions.getArea(myProject).getExtensionPoint(IdeRootPaneNorthExtension.EP_NAME).addExtensionPointListener(
        new ExtensionPointListener<IdeRootPaneNorthExtension>() {
          @Override
          public void extensionAdded(@NotNull IdeRootPaneNorthExtension extension, @Nullable PluginDescriptor pluginDescriptor) {
            updateNorthPanel();
          }

          @Override
          public void extensionRemoved(@NotNull IdeRootPaneNorthExtension extension, @Nullable PluginDescriptor pluginDescriptor) {
            updateNorthPanel();
          }

        });

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
      myNorthPanel.setVisible(UISettings.getInstance().SHOW_NAVIGATION_BAR);

      IdeRootPaneNorthExtension[] extensions = Extensions.getArea(myProject).getExtensionPoint(IdeRootPaneNorthExtension.EP_NAME).getExtensions();
      HashSet<String> processedKeys = new HashSet<String>();
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

    public void setTransparrent(boolean transparrent) {
      if (transparrent) {
        WindowManagerEx.getInstanceEx().setAlphaModeEnabled(getFrame(), true);
        WindowManagerEx.getInstanceEx().setAlphaModeRatio(getFrame(), 0.5f);
      } else {
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
      frame.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          myContainer.closeAll();
        }
      });

      new UiNotifyConnector(frame.getContentPane(), myContainer);

      return frame;
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
    for (int i = 0; i < windows.size(); i++) {
      Element eachWindow = (Element)windows.get(i);
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

      createWindowFor(eachId, container).show();
    }
  }
}
