// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.InternalUICustomization;
import com.intellij.openapi.components.ComponentManagerEx;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockableContent;
import com.intellij.ui.docking.DockableContentContainer;
import com.intellij.ui.docking.DragSession;
import com.intellij.ui.docking.impl.DockManagerImpl;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.tabs.JBTabPainter;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.UiDecorator;
import com.intellij.ui.tabs.impl.*;
import com.intellij.ui.tabs.impl.singleRow.WindowTabsLayout;
import com.intellij.ui.tabs.impl.themes.DefaultTabTheme;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.util.*;

@ApiStatus.Internal
public final class WindowTabsComponent extends JBTabsImpl {
  private static final String TITLE_LISTENER_KEY = "TitleListener";
  public static final String CLOSE_TAB_KEY = "CloseTab";

  private static final int TAB_HEIGHT = 30;

  private static DockManagerImpl dockManager;

  private final IdeFrameImpl myNativeWindow;
  private final Disposable myParentDisposable;
  private final Map<IdeFrameImpl, Integer> myIndexes = new HashMap<>();

  public WindowTabsComponent(@NotNull IdeFrameImpl nativeWindow, @Nullable Project project, @NotNull Disposable parentDisposable) {
    super(project, parentDisposable);

    myNativeWindow = nativeWindow;
    myParentDisposable = parentDisposable;

    setUiDecorator(new UiDecorator() {
      @Override
      public @NotNull UiDecoration getDecoration() {
        //noinspection UseDPIAwareInsets
        return new UiDecoration(JBFont.medium(), new Insets(-1, getDropInfo() == null ? 0 : -1, -1, -1));
      }
    });

    Disposer.register(myParentDisposable, () -> {
      int count = getTabCount();
      for (int i = 0; i < count; i++) {
        removeTitleListener(getTabAt(i));
      }
    });

    createTabActions();
    installDnD();
  }

  public void selfDispose() {
    Disposer.dispose(myParentDisposable);
  }

  @Override
  protected @NotNull TabLayout createRowLayout() {
    if (isSingleRow()) {
      return new WindowTabsLayout(this);
    }
    else {
      return super.createRowLayout();
    }
  }

  @Override
  public @NotNull Dimension getPreferredSize() {
    return new Dimension(super.getPreferredSize().width, JBUI.scale(TAB_HEIGHT));
  }

  @Override
  protected @Nullable TabInfo getDraggedTabSelectionInfo() {
    TabInfo source = getDragHelper().getDragSource();
    return source != null ? source : super.getDraggedTabSelectionInfo();
  }

  private static boolean _isSelectionClick(@NotNull MouseEvent e) {
    return e.getClickCount() == 1 && !e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1
           && !e.isControlDown() && !e.isAltDown() && !e.isMetaDown();
  }

  @Override
  protected @NotNull TabLabel createTabLabel(@NotNull TabInfo info) {
    return new TabLabel(this, info) {
      {
        for (MouseListener listener : getMouseListeners()) {
          removeMouseListener(listener);
        }
        TabLabel label = this;
        addMouseListener(new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            if (!UIUtil.isCloseClick(e, MouseEvent.MOUSE_PRESSED) && !_isSelectionClick(e)) {
              handlePopup(e);
            }
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            handlePopup(e);
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            if (_isSelectionClick(e)) {
              Component c = SwingUtilities.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());
              if (c instanceof InplaceButton) return;
              tabs.select(info, true);
              JBPopup container = PopupUtil.getPopupContainerFor(label);
              if (container != null && ClientProperty.isTrue(container.getContent(), MorePopupAware.class)) {
                container.cancel();
              }
            }
            else if (e.getButton() == MouseEvent.BUTTON2) {
              closeTab((IdeFrameImpl)getInfo().getObject(), false);
            }
            else {
              handlePopup(e);
            }
          }

          @Override
          public void mouseEntered(MouseEvent e) {
            setHovered(true);
          }

          @Override
          public void mouseExited(MouseEvent e) {
            setHovered(false);
          }
        });
      }

      @Override
      public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, JBUI.scale(TAB_HEIGHT));
      }

      @Override
      public void setTabActions(ActionGroup group) {
        super.setTabActions(group);
        if (actionPanel != null) {
          Container parent = actionPanel.getParent();
          parent.remove(actionPanel);
          parent.add(new Wrapper(actionPanel) {
            @Override
            public Dimension getPreferredSize() {
              return actionPanel.getPreferredSize();
            }
          }, BorderLayout.WEST);

          actionPanel.setBorder(JBUI.Borders.emptyLeft(6));
          actionPanel.setVisible(!showCloseActionOnHover());
        }
      }

      @Override
      protected void setHovered(boolean value) {
        super.setHovered(value);
        if (actionPanel != null) {
          actionPanel.setVisible(!showCloseActionOnHover() || value || getInfo() == tabs.getPopupInfo());
        }
      }

      @Override
      protected void handlePopup(MouseEvent e) {
        super.handlePopup(e);
        JPopupMenu popup = tabs.getActivePopup();
        if (popup != null) {
          popup.addPopupMenuListener(new PopupMenuListenerAdapter() {
            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
              handle();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
              handle();
            }

            private void handle() {
              popup.removePopupMenuListener(this);
              actionPanel.setVisible(!showCloseActionOnHover() || isHovered());
            }
          });
        }
      }

      @Override
      protected boolean isTabActionsOnTheRight() {
        return false;
      }

      @Override
      protected boolean shouldPaintFadeout() {
        return false;
      }
    };
  }

  private static boolean showCloseActionOnHover() {
    return Registry.is("ide.mac.os.wintabs.show.closeaction.on.hover", true);
  }

  @Override
  protected @NotNull TabPainterAdapter createTabPainterAdapter() {
    return new TabPainterAdapter() {
      private final JBTabPainter myTabPainter = new JBDefaultTabPainter(new DefaultTabTheme() {
        @Override
        public int getTopBorderThickness() {
          return 0;
        }

        @Override
        public @Nullable Color getInactiveColoredTabBackground() {
          return null;
        }
      });

      @Override
      public @NotNull JBTabPainter getTabPainter() {
        return myTabPainter;
      }

      @Override
      public void paintBackground(@NotNull TabLabel label, @NotNull Graphics g, @NotNull JBTabsImpl tabs) {
        Rectangle rect = new Rectangle(0, 0, label.getWidth(), label.getHeight());
        TabInfo info = label.getInfo();
        Graphics2D g2d = (Graphics2D)g;

        int index = tabs.getIndexOf(info);
        int lastIndex = tabs.getTabCount() - 1;
        int border = JBUI.scale(1);
        boolean selected = info.getObject() == myNativeWindow;

        InternalUICustomization customization = InternalUICustomization.getInstance();
        if (customization != null && customization.paintProjectTab(label, g, tabs, selected, index, lastIndex)) {
          return;
        }

        if (selected) {
          Window window = ComponentUtil.getWindow(WindowTabsComponent.this);
          Color tabColor = JBUI.CurrentTheme.MainWindow.Tab.background(true, window != null && !window.isActive(), false);
          myTabPainter.paintTab(tabs.getTabsPosition(), g2d, rect, tabs.getBorderThickness(), tabColor, true, false);

          g.setColor(JBUI.CurrentTheme.MainWindow.Tab.BORDER);
          LinePainter2D.paint(g2d, 0, 0, rect.width, 0, LinePainter2D.StrokeType.INSIDE, border);
          if (index > 0) {
            LinePainter2D.paint(g2d, 0, 0, 0, rect.height, LinePainter2D.StrokeType.INSIDE, border);
          }
          if (index < lastIndex) {
            LinePainter2D.paint(g2d, rect.width - border, 0, rect.width - border, rect.height, LinePainter2D.StrokeType.INSIDE, border);
          }
        }
        else {
          Color tabColor = JBUI.CurrentTheme.MainWindow.Tab.background(false, false, label.isHovered());
          myTabPainter.paintTab(tabs.getTabsPosition(), g2d, rect, tabs.getBorderThickness(), tabColor, true, false);

          if (lastIndex > 1 && index < lastIndex && tabs.getTabAt(index + 1) != tabs.getSelectedInfo()) {
            g.setColor(JBUI.CurrentTheme.MainWindow.Tab.SEPARATOR);
            LinePainter2D.paint(g2d, rect.width - border, 0, rect.width - border, rect.height, LinePainter2D.StrokeType.INSIDE, border);
          }
        }
      }
    };
  }

  private boolean isSameGroup(@NotNull WindowTabsComponent anotherComponent) {
    if (this == anotherComponent) {
      return true;
    }

    int count = getTabCount();
    if (count != anotherComponent.getTabCount()) {
      return false;
    }

    Set<Object> tabs = new HashSet<>();
    Set<Object> anotherTabs = new HashSet<>();

    for (int i = 0; i < count; i++) {
      tabs.add(getTabAt(i).getObject());
      anotherTabs.add(anotherComponent.getTabAt(i).getObject());
    }

    return tabs.equals(anotherTabs);
  }

  public void createTabsForFrame(IdeFrameImpl @NotNull [] tabFrames) {
    for (IdeFrameImpl tabFrame : tabFrames) {
      createTabItem(tabFrame, -1, tabFrame == myNativeWindow);
    }

    recalculateIndexes();

    setSelectionChangeHandler((info, requestFocus, doChangeSelection) -> {
      IdeFrameImpl tabFrame = (IdeFrameImpl)info.getObject();

      if (tabFrame == myNativeWindow) {
        TabInfo selectedInfo = getSelectedInfo();
        if (selectedInfo != null && selectedInfo.getObject() == myNativeWindow) {
          return ActionCallback.REJECTED;
        }
        return doChangeSelection.run();
      }

      Foundation.executeOnMainThread(true, false, () -> {
        ID window = MacUtil.getWindowFromJavaWindow((Window)info.getObject());
        ID tabGroup = Foundation.invoke(window, "tabGroup");
        Foundation.invoke(tabGroup, "setSelectedWindow:", window);
      });

      return ActionCallback.REJECTED;
    });
  }

  public void insertTabForFrame(@NotNull IdeFrameImpl tab, int index) {
    createTabItem(tab, index, false);
    recalculateIndexes();
  }

  public boolean removeTabFromFrame(@NotNull IdeFrameImpl tab) {
    int count = getTabCount();
    boolean removed = false;

    for (int i = 0; i < count; i++) {
      TabInfo info = getTabAt(i);
      if (info.getObject() == tab) {
        removeTitleListener(info);
        removeTab(info);
        recalculateIndexes();
        removed = true;
        count--;
        break;
      }
    }
    return removed && count == 1;
  }

  private void createTabItem(@NotNull IdeFrameImpl tabFrame, int index, boolean selection) {
    TabInfo info = new TabInfo(new JLabel());
    info.setObject(tabFrame).setText(tabFrame.getTitle()).setTooltipText(tabFrame.getTitle()); //NON-NLS
    info.setTabLabelActions(createTabActions(tabFrame), ActionPlaces.UNKNOWN);
    info.setDefaultForeground(JBUI.CurrentTheme.MainWindow.Tab.foreground(selection, false));

    addTab(info, index);

    if (selection) {
      select(info, true);

      WindowAdapter listener = new WindowAdapter() {
        @Override
        public void windowActivated(WindowEvent e) {
          repaint();
        }

        @Override
        public void windowDeactivated(WindowEvent e) {
          repaint();
        }
      };
      tabFrame.addWindowListener(listener);
      Disposer.register(myParentDisposable, () -> tabFrame.removeWindowListener(listener));
    }

    PropertyChangeListener listener = event -> info.setText((String)event.getNewValue()).setTooltipText((String)event.getNewValue());
    tabFrame.addPropertyChangeListener("title", listener);
    info.getComponent().putClientProperty(TITLE_LISTENER_KEY, listener);

    installTabDnd(info);
  }

  private static void removeTitleListener(@NotNull TabInfo info) {
    PropertyChangeListener listener = (PropertyChangeListener)info.getComponent().getClientProperty(TITLE_LISTENER_KEY);
    ((IdeFrameImpl)info.getObject()).removePropertyChangeListener("title", listener);
  }

  private void recalculateIndexes() {
    myIndexes.clear();

    for (int i = 0, count = getTabCount(); i < count; i++) {
      myIndexes.put((IdeFrameImpl)getTabAt(i).getObject(), i);
    }
  }

  private void reorderTabs(Map<IdeFrameImpl, Integer> indexes) {
    sortTabs(Comparator.comparingInt(info -> indexes.get(info.getObject())));
    recalculateIndexes();
  }

  private static @NotNull DefaultActionGroup createTabActions(@NotNull IdeFrameImpl tabFrame) {
    DumbAwareAction closeAction = new DumbAwareAction(IdeBundle.message("mac.window.tabs.close.title")) {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        closeTab(tabFrame, (e.getModifiers() & ActionEvent.ALT_MASK) != 0);
      }
    };

    Presentation presentation = closeAction.getTemplatePresentation();
    boolean isNewUiAndDark = ExperimentalUI.isNewUI() && (!JBColor.isBright() || ColorUtil.isDark(JBColor.namedColor("MainToolbar.background")));
    presentation.setIcon(isNewUiAndDark ?
                         IconManager.getInstance().getIcon("expui/general/closeSmall_dark.svg", AllIcons.class.getClassLoader()) :
                         AllIcons.Actions.Close);
    presentation.setHoveredIcon(isNewUiAndDark
                                ? IconManager.getInstance()
                                  .getIcon("expui/general/closeSmallHovered_dark.svg", AllIcons.class.getClassLoader())
                                : AllIcons.Actions.CloseHovered);

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(closeAction);

    return group;
  }

  private void createTabActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new DumbAwareAction(IdeBundle.message("mac.window.tabs.close.tab")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        closeTab(getPopupFrame(e), false);
      }
    });

    group.add(new DumbAwareAction(IdeBundle.message("mac.window.tabs.close.other.tabs")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        closeTab(getPopupFrame(e), true);
      }
    });

    group.add(new DumbAwareAction(IdeBundle.message("mac.window.tabs.move.tab")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        moveTabToNewWindow(getPopupFrame(e));
      }
    });

    group.add(new DumbAwareAction(IdeBundle.message("mac.window.tabs.show.all.tabs")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        IdeFrameImpl tabFrame = getPopupFrame(e);

        Foundation.executeOnMainThread(true, false, () -> {
          ID window = MacUtil.getWindowFromJavaWindow(tabFrame);
          ID tabs = Foundation.invoke(window, "tabbedWindows");
          int count = Foundation.invoke(tabs, "count").intValue();

          for (int i = 0; i < count; i++) {
            ID tab = Foundation.invoke(tabs, "objectAtIndex:", i);
            Foundation.invoke(tab, "setTabOverviewVisible:", 1);
          }
        });
      }
    });

    setPopupGroup(group, ActionPlaces.POPUP, false);
  }

  private static @NotNull IdeFrameImpl getPopupFrame(@NotNull AnActionEvent e) {
    TabLabel label = (TabLabel)Objects.requireNonNull(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT));
    return (IdeFrameImpl)label.getInfo().getObject();
  }

  private static void closeTab(@NotNull IdeFrameImpl tabFrame, boolean closeOthers) {
    tabFrame.getRootPane().putClientProperty(CLOSE_TAB_KEY, Boolean.TRUE);
    Foundation.executeOnMainThread(true, false, () -> {
      ID window = MacUtil.getWindowFromJavaWindow(tabFrame);
      Foundation.invoke(window, closeOthers ? "performCloseOtherTabs:" : "performClose:", ID.NIL);
    });
  }

  private static void moveTabToNewWindow(@NotNull IdeFrameImpl tabFrame) {
    Foundation.executeOnMainThread(true, false, () -> {
      ID window = MacUtil.getWindowFromJavaWindow(tabFrame);
      ID tabGroup = Foundation.invoke(window, "tabGroup");
      Foundation.invoke(tabGroup, "setSelectedWindow:", window);
      Foundation.invoke(window, "moveTabToNewWindow:", ID.NIL);

      ApplicationManager.getApplication().invokeLater(() -> MacWinTabsHandlerV2.updateTabBarsAfterMove(tabFrame, null, -1));
    });
  }

  private static void moveTabToWindow(@NotNull IdeFrameImpl tabFrame, @NotNull IdeFrameImpl target) {
    Foundation.executeOnMainThread(true, false, () -> {
      ID window = MacUtil.getWindowFromJavaWindow(target);
      ID tabGroup = Foundation.invoke(window, "tabGroup");
      ID tab = MacUtil.getWindowFromJavaWindow(tabFrame);
      Foundation.invoke(tabGroup, "addWindow:", tab);
      Foundation.invoke(tabGroup, "setSelectedWindow:", tab);

      ApplicationManager.getApplication().invokeLater(() -> MacWinTabsHandlerV2.updateTabBarsAfterMove(tabFrame, target, -1));
    });
  }

  private void moveTabToNewIndex(@NotNull TabInfo info) {
    IdeFrameImpl movedFrame = (IdeFrameImpl)info.getObject();
    int newIndex = getIndexOf(info);
    int oldIndex = myIndexes.get(movedFrame);

    if (oldIndex != newIndex) {
      recalculateIndexes();
      moveTabToNewIndex(movedFrame, newIndex);
    }
  }

  private void moveTabToNewIndex(@NotNull IdeFrameImpl movedFrame, int newIndex) {
    for (int i = 0, count = getTabCount(); i < count; i++) {
      IdeFrameImpl tabFrame = (IdeFrameImpl)getTabAt(i).getObject();
      if (tabFrame != myNativeWindow) {
        WindowTabsComponent tabsComponent =
          Objects.requireNonNull(MacWinTabsHandlerV2.getTabsComponent(MacWinTabsHandlerV2.getTabsContainer(tabFrame)));
        tabsComponent.reorderTabs(myIndexes);
      }
    }

    Runnable action = () -> Foundation.executeOnMainThread(true, false, () -> {
      ID window = MacUtil.getWindowFromJavaWindow(movedFrame);
      ID tabGroup = Foundation.invoke(window, "tabGroup");
      Foundation.invoke(tabGroup, "removeWindow:", window);
      Foundation.invoke(tabGroup, "insertWindow:atIndex:", window, newIndex);
      Foundation.invoke(tabGroup, "setSelectedWindow:", window);
    });

    if (movedFrame == myNativeWindow) {
      action.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(action);
    }
  }

  private void moveTabToNewIndexOrWindow(@NotNull TabInfo info, boolean movedFromOut) {
    IdeFrameImpl movedFrame = (IdeFrameImpl)info.getObject();
    int newIndex = getDropInfoIndex();
    assert newIndex != -1 : "Wrong Reordering";

    if (movedFromOut) {
      Foundation.executeOnMainThread(true, false, () -> {
        ID window = MacUtil.getWindowFromJavaWindow(myNativeWindow);
        ID tabGroup = Foundation.invoke(window, "tabGroup");
        ID tab = MacUtil.getWindowFromJavaWindow(movedFrame);
        Foundation.invoke(tabGroup, "insertWindow:atIndex:", tab, newIndex);
        Foundation.invoke(tabGroup, "setSelectedWindow:", tab);

        ApplicationManager.getApplication()
          .invokeLater(() -> MacWinTabsHandlerV2.updateTabBarsAfterMove(movedFrame, myNativeWindow, newIndex));
      });
    }
    else {
      info.setHidden(false);
      int oldIndex = myIndexes.get(movedFrame);

      if (oldIndex != newIndex) {
        reorderTab(info, newIndex);
        recalculateIndexes();
        moveTabToNewIndex(movedFrame, newIndex);
      }
    }
  }

  private void installTabDnd(@NotNull TabInfo info) {
    info.setDragOutDelegate(new TabInfo.DragOutDelegate() {
      private DragSession mySession;

      @Override
      public void dragOutStarted(@NotNull MouseEvent mouseEvent, @NotNull TabInfo info) {
        WindowFrameDockableContent content = new WindowFrameDockableContent(WindowTabsComponent.this, info, getTabLabel(info));
        info.setHidden(true);
        DockManagerImpl manager = getDockManager();
        updateDockContainers(manager);
        mySession = manager.createDragSession(mouseEvent, content);
      }

      @Override
      public void processDragOut(@NotNull MouseEvent event, @NotNull TabInfo source) {
        mySession.process(event);
      }

      @Override
      public void dragOutFinished(@NotNull MouseEvent event, TabInfo source) {
        mySession.process(event);
        mySession = null;
      }

      @Override
      public void dragOutCancelled(TabInfo source) {
        mySession.cancel();
        mySession = null;

        source.setHidden(false);

        if (getIndexOf(source) != -1) {
          moveTabToNewIndex(source);
        }
      }
    });
    info.setDragDelegate(new TabInfo.DragDelegate() {
      @Override
      public void dragStarted(@NotNull MouseEvent mouseEvent) {
        WindowTabsComponent.this.setComponentZOrder(getTabLabel(info), 0);
      }

      @Override
      public void dragFinishedOrCanceled() {
        WindowTabsComponent.this.setComponentZOrder(getTabLabel(getSelectedInfo()), 0);
      }
    });
  }

  private static void updateDockContainers(@NotNull DockManagerImpl manager) {
    for (DockContainer _container : manager.getContainers()) {
      if (_container instanceof TabsDockContainer container) {
        container.checkEnabled();
      }
    }
  }

  private static @NotNull DockManagerImpl getDockManager() {
    if (dockManager == null) {
      Project project = ProjectManager.getInstance().getDefaultProject();
      dockManager = new DockManagerImpl(project, ((ComponentManagerEx)project).getCoroutineScope());
    }
    return dockManager;
  }

  private void installDnD() {
    setTabDraggingEnabled(true);

    addListener(new TabsListener() {
      @Override
      public void tabsMoved() {
        moveTabToNewIndex(getDragHelper().getDragSource());
      }
    });

    getDockManager().register(new TabsDockContainer(), myParentDisposable);
  }

  static void registerFrameDockContainer(@NotNull IdeFrameImpl frame, @NotNull CoroutineScope coroutineScope) {
    getDockManager().register(new DockContainer() {
      private Disposable myPaintDisposable;
      private AbstractPainter myDropPainter;

      @Override
      public @NotNull RelativeRectangle getAcceptArea() {
        return new RelativeRectangle(frame.getComponent());
      }

      @Override
      public @NotNull ContentResponse getContentResponse(@NotNull DockableContent<?> _content, RelativePoint point) {
        return _content instanceof WindowFrameDockableContent content && !content.isInFullScreen() &&
               MacWinTabsHandlerV2.isTabsNotVisible(frame) && !frame.isInFullScreen() ? ContentResponse.ACCEPT_MOVE : ContentResponse.DENY;
      }

      @Override
      public @NotNull JComponent getContainerComponent() {
        return frame.getComponent();
      }

      @Override
      public @Nullable Image startDropOver(@NotNull DockableContent<?> content, RelativePoint point) {
        myPaintDisposable = Disposer.newDisposable("GlassPaneListeners");
        myDropPainter = new WindowDropAreaPainter(frame);
        JComponent component = frame.getComponent();

        IdeGlassPaneUtil.find(component).addPainter(component, myDropPainter, myPaintDisposable);

        return null;
      }

      @Override
      public void resetDropOver(@NotNull DockableContent<?> content) {
        Disposer.dispose(myPaintDisposable);
        myPaintDisposable = null;
        myDropPainter = null;
      }

      @Override
      public void add(@NotNull DockableContent<?> content, @Nullable RelativePoint dropTarget) {
        moveTabToWindow(((WindowFrameDockableContent)content).getKey(), frame);
      }

      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public boolean isDisposeWhenEmpty() {
        return false;
      }
    }, coroutineScope);
  }

  private final class TabsDockContainer implements DockContainer {
    boolean enabled;
    TabInfo myDropTab;
    Image myDropImage;

    void checkEnabled() {
      ID window = MacUtil.getWindowFromJavaWindow(myNativeWindow);
      enabled = Foundation.invoke(window, "isNativeSelected").intValue() == 1;
    }

    @Override
    public @NotNull RelativeRectangle getAcceptArea() {
      return new RelativeRectangle(WindowTabsComponent.this);
    }

    @Override
    public @NotNull ContentResponse getContentResponse(@NotNull DockableContent<?> content, RelativePoint point) {
      return enabled && content instanceof WindowFrameDockableContent ? ContentResponse.ACCEPT_MOVE : ContentResponse.DENY;
    }

    @Override
    public @NotNull JComponent getContainerComponent() {
      return WindowTabsComponent.this;
    }

    @Override
    public @NotNull Image startDropOver(@NotNull DockableContent<?> content, RelativePoint point) {
      Presentation presentation = content.getPresentation();
      myDropTab = new TabInfo(new JLabel()).setText(presentation.getText())
        .setDefaultForeground(JBUI.CurrentTheme.MainWindow.Tab.foreground(true, false));
      return myDropImage = WindowTabsComponent.this.startDropOver(myDropTab, point);
    }

    @Override
    public @Nullable Image processDropOver(@NotNull DockableContent<?> content, RelativePoint point) {
      WindowTabsComponent.this.processDropOver(myDropTab, point);
      return myDropImage;
    }

    @Override
    public void resetDropOver(@NotNull DockableContent<?> content) {
      WindowTabsComponent.this.resetDropOver(myDropTab);
      myDropTab = null;
      myDropImage = null;
    }

    @Override
    public void add(@NotNull DockableContent<?> _content, @Nullable RelativePoint dropTarget) {
      WindowFrameDockableContent content = (WindowFrameDockableContent)_content;
      moveTabToNewIndexOrWindow(content.myInfo, !isSameGroup(content.myTabsComponent));
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean isDisposeWhenEmpty() {
      return false;
    }
  }

  private static final class WindowFrameDockableContent implements DockableContent<IdeFrameImpl>, DockableContentContainer {
    private final Dimension mySize;
    private final WindowTabsComponent myTabsComponent;
    private final TabInfo myInfo;
    private final Presentation myPresentation;
    private final BufferedImage myImage;

    private WindowFrameDockableContent(@NotNull WindowTabsComponent tabsComponent, @NotNull TabInfo info, @NotNull TabLabel label) {
      myTabsComponent = tabsComponent;
      myInfo = info;

      IdeFrameImpl frame = getKey();

      mySize = frame.getSize();

      myImage = UIUtil.createImage(frame, mySize.width, mySize.height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = myImage.createGraphics();
      label.paint(g);

      myPresentation = new Presentation(info.getText());
    }

    @Override
    public @NotNull IdeFrameImpl getKey() {
      return (IdeFrameImpl)myInfo.getObject();
    }

    public boolean isInFullScreen() {
      return getKey().isInFullScreen() || myTabsComponent.myNativeWindow.isInFullScreen();
    }

    @Override
    public Image getPreviewImage() {
      return myImage;
    }

    @Override
    public String getDockContainerType() {
      return null;
    }

    @Override
    public Dimension getPreferredSize() {
      return mySize;
    }

    @Override
    public void close() {
    }

    @Override
    public Presentation getPresentation() {
      return myPresentation;
    }

    @Override
    public void add(@Nullable RelativePoint dropTarget) {
      moveTabToNewWindow(getKey());
    }
  }

  private static final class WindowDropAreaPainter extends AbstractPainter {
    private final Shape myArea;

    WindowDropAreaPainter(@NotNull IdeFrameImpl frame) {
      myArea = new Rectangle2D.Double(0, 0, frame.getWidth(), frame.getHeight());
    }

    @Override
    public boolean needsRepaint() {
      return true;
    }

    @Override
    public void executePaint(@NotNull Component component, @NotNull Graphics2D g) {
      GraphicsUtil.setupAAPainting(g);
      g.setColor(JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND);
      g.fill(myArea);
    }
  }
}