// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.CloseAction;
import com.intellij.ide.actions.ShowContentAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.content.*;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.ui.content.tabs.TabbedContentAction;
import com.intellij.ui.layout.migLayout.MigLayoutUtilKt;
import com.intellij.ui.layout.migLayout.patched.MigLayout;
import com.intellij.ui.tabs.impl.MorePopupAware;
import com.intellij.util.Alarm;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.LocationOnDragTracker;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.function.Predicate;

public final class ToolWindowContentUi implements ContentUI, DataProvider {
  // when client property is put in toolwindow component, hides toolwindow label
  public static final String HIDE_ID_LABEL = "HideIdLabel";
  private static final String TOOLWINDOW_UI_INSTALLED = "ToolWindowUiInstalled";

  private final @NotNull ContentManager contentManager;

  public @NotNull ContentManager getContentManager() {
    return contentManager;
  }

  private final JPanel contentComponent;
  final ToolWindowImpl window;

  private final TabbedContentAction.CloseAllAction closeAllAction;
  private final TabbedContentAction.MyNextTabAction nextTabAction;
  private final TabbedContentAction.MyPreviousTabAction previousTabAction;

  private final ShowContentAction showContent;

  private final TabContentLayout tabsLayout;
  private ContentLayout myComboLayout;

  private ToolWindowContentUiType type;

  public Predicate<Point> isResizableArea = __ -> true;

  private final JPanel tabComponent = new TabPanel();

  @NotNull
  public JPanel getTabComponent() {
    return tabComponent;
  }

  public ToolWindowContentUi(@NotNull ToolWindowImpl window,
                             @NotNull ContentManager contentManager,
                             @NotNull JPanel contentComponent) {
    this.contentManager = contentManager;
    type = window.getWindowInfo().getContentUiType();
    tabsLayout = new TabContentLayout(this);
    this.window = window;
    this.contentComponent = contentComponent;

    getCurrentLayout().init(contentManager);

    contentManager.addContentManagerListener(new ContentManagerListener() {
      private final PropertyChangeListener propertyChangeListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent event) {
          update();
        }
      };

      @Override
      public void contentAdded(@NotNull ContentManagerEvent event) {
        getCurrentLayout().contentAdded(event);
        event.getContent().addPropertyChangeListener(propertyChangeListener);
        rebuild();
      }

      @Override
      public void contentRemoved(@NotNull ContentManagerEvent event) {
        event.getContent().removePropertyChangeListener(propertyChangeListener);
        getCurrentLayout().contentRemoved(event);
        ensureSelectedContentVisible();
        rebuild();

        if (contentManager.getContentCount() == 0 && window.isToHideOnEmptyContent()) {
          window.hide(null);
        }
      }

      @Override
      public void selectionChanged(@NotNull ContentManagerEvent event) {
        ensureSelectedContentVisible();

        update();
        contentComponent.revalidate();
        contentComponent.repaint();
      }
    });

    initMouseListeners(tabComponent, this, true);

    closeAllAction = new TabbedContentAction.CloseAllAction(contentManager);
    nextTabAction = new TabbedContentAction.MyNextTabAction(contentManager);
    previousTabAction = new TabbedContentAction.MyPreviousTabAction(contentManager);
    showContent = new ShowContentAction(window, contentComponent, contentManager);
  }

  @NotNull
  public String getToolWindowId() {
    return window.getId();
  }

  private boolean isResizeable() {
    if (window.getType() == ToolWindowType.FLOATING || window.getType() == ToolWindowType.WINDOWED) {
      return false;
    }
    if (window.getAnchor() == ToolWindowAnchor.BOTTOM) {
      return true;
    }
    if (window.getAnchor() == ToolWindowAnchor.TOP || !window.isSplitMode()) {
      return false;
    }

    ToolWindowManagerImpl manager = window.getToolWindowManager();
    for (String id : manager.getIdsOn(window.getAnchor())) {
      if (id.equals(window.getId())) {
        continue;
      }
      ToolWindow window = manager.getToolWindow(id);
      if (window != null && window.isVisible() && (window.getType() == ToolWindowType.DOCKED || window.getType() == ToolWindowType.SLIDING)) {
        return true;
      }
    }
    return false;
  }

  private boolean isResizeable(@NotNull Point point) {
    return isResizableArea.test(point);
  }

  public void setType(@NotNull ToolWindowContentUiType type) {
    if (this.type == type) {
      return;
    }

    if (this.type != null) {
      getCurrentLayout().reset();
    }

    this.type = type;

    getCurrentLayout().init(contentManager);
    rebuild();
  }

  @NotNull
  private ContentLayout getCurrentLayout() {
    if (type == ToolWindowContentUiType.TABBED) {
      return tabsLayout;
    }
    else {
      if (myComboLayout == null) {
        myComboLayout = new ComboContentLayout(this);
      }
      return myComboLayout;
    }
  }

  @Override
  public JComponent getComponent() {
    return contentComponent;
  }

  @Override
  public void setManager(@NotNull ContentManager manager) {
    throw new UnsupportedOperationException();
  }

  private void ensureSelectedContentVisible() {
    Content selected = contentManager.getSelectedContent();
    if (selected == null) {
      contentComponent.removeAll();
      return;
    }

    if (contentComponent.getComponentCount() == 1) {
      Component visible = contentComponent.getComponent(0);
      if (visible == selected.getComponent()) {
        return;
      }
    }

    contentComponent.removeAll();
    contentComponent.add(selected.getComponent(), BorderLayout.CENTER);

    contentComponent.revalidate();
    contentComponent.repaint();
  }

  public void dropCaches() {
    tabsLayout.dropCaches();
  }

  private void rebuild() {
    getCurrentLayout().rebuild();
    getCurrentLayout().update();

    tabComponent.revalidate();
    tabComponent.repaint();
  }

  private void update() {
    getCurrentLayout().update();
    getCurrentLayout().layout();

    tabComponent.revalidate();
    tabComponent.repaint();
  }

  @Override
  public boolean isSingleSelection() {
    return true;
  }

  @Override
  public boolean isToSelectAddedContent() {
    return false;
  }

  @Override
  public boolean canBeEmptySelection() {
    return false;
  }

  @Override
  public boolean canChangeSelectionTo(@NotNull Content content, boolean implicit) {
    return true;
  }

  @NotNull
  @Override
  public String getCloseActionName() {
    return getCurrentLayout().getCloseActionName();
  }

  @NotNull
  @Override
  public String getCloseAllButThisActionName() {
    return getCurrentLayout().getCloseAllButThisActionName();
  }

  @NotNull
  @Override
  public String getPreviousContentActionName() {
    return getCurrentLayout().getPreviousContentActionName();
  }

  @NotNull
  @Override
  public String getNextContentActionName() {
    return getCurrentLayout().getNextContentActionName();
  }

  public void setTabDoubleClickActions(@NotNull List<AnAction> actions) {
    tabsLayout.setTabDoubleClickActions(actions);
  }

  public static void initMouseListeners(@NotNull JComponent c, @NotNull ToolWindowContentUi ui, boolean allowResize) {
    if (c.getClientProperty(TOOLWINDOW_UI_INSTALLED) != null) {
      return;
    }

    MouseAdapter mouseAdapter = new MouseAdapter() {
      final Ref<Point> myLastPoint = Ref.create();
      final Ref<Point> myPressPoint = Ref.create();
      final Ref<Integer> myInitialHeight = Ref.create(0);
      final Ref<Boolean> myIsLastComponent = Ref.create();
      final Ref<LocationOnDragTracker> myDragTracker = Ref.create();

      private Component getActualSplitter() {
        if (!allowResize || !ui.isResizeable()) {
          return null;
        }

        Component component = c;
        Component parent = component.getParent();
        while(parent != null) {

          if (parent instanceof ThreeComponentsSplitter && ((ThreeComponentsSplitter)parent).getOrientation()) {
            if (component != ((ThreeComponentsSplitter)parent).getFirstComponent()) {
              return parent;
            }
          }
          if (parent instanceof Splitter && ((Splitter)parent).isVertical()
              && ((Splitter)parent).getSecondComponent() == component
              && ((Splitter)parent).getFirstComponent() != null) {
            return parent;
          }
          component = parent;
          parent = parent.getParent();
        }
        return null;
      }

      private void arm(Component c) {
        Component component = c != null ? getActualSplitter() : null;
        if (component instanceof ThreeComponentsSplitter) {
          ThreeComponentsSplitter splitter = (ThreeComponentsSplitter)component;
          myIsLastComponent.set(SwingUtilities.isDescendingFrom(c, splitter.getLastComponent()));
          myInitialHeight.set(myIsLastComponent.get() ? splitter.getLastSize() : splitter.getFirstSize());
          return;
        }
        if (component instanceof Splitter) {
          Splitter splitter = (Splitter)component;
          myIsLastComponent.set(true);
          myInitialHeight.set(splitter.getSecondComponent().getHeight());
          return;
        }
        myIsLastComponent.set(null);
        myInitialHeight.set(null);
        myPressPoint.set(null);
        myDragTracker.set(null);
      }

      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        PointerInfo info = MouseInfo.getPointerInfo();
        if (!e.isPopupTrigger()) {
          if (!UIUtil.isCloseClick(e)) {
            myLastPoint.set(info != null ? info.getLocation() : e.getLocationOnScreen());
            myPressPoint.set(myLastPoint.get());
            myDragTracker.set(LocationOnDragTracker.startDrag(e));
            if (allowResize && ui.isResizeable()) {
              arm(c.getComponentAt(e.getPoint()) == c && ui.isResizeable(e.getPoint()) ? c : null);
            }
            ui.window.fireActivated();
          }
        }
      }

      @Override
      public void mouseReleased(@NotNull MouseEvent e) {
        if (!e.isPopupTrigger()) {
          if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_RELEASED)) {
            ui.processHide(e);
          }
          arm(null);
        }
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        c.setCursor(allowResize && ui.isResizeable() && getActualSplitter() != null && c.getComponentAt(e.getPoint()) == c && ui.isResizeable(e.getPoint())
                    ? Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
                    : Cursor.getDefaultCursor());
      }

      @Override
      public void mouseExited(MouseEvent e) {
        c.setCursor(null);
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        if (myLastPoint.isNull() || myPressPoint.isNull() || myDragTracker.isNull()) return;

        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) return;
        Point newMouseLocation = info.getLocation();

        Window window = SwingUtilities.windowForComponent(c);
        if (!(window instanceof IdeFrame)) {
          myDragTracker.get().updateLocationOnDrag(window);
        }
        myLastPoint.set(newMouseLocation);
        Component component = getActualSplitter();
        if (component instanceof ThreeComponentsSplitter) {
          ThreeComponentsSplitter splitter = (ThreeComponentsSplitter)component;
          if (myIsLastComponent.get() == Boolean.TRUE) {
            splitter.setLastSize(myInitialHeight.get() + myPressPoint.get().y - myLastPoint.get().y);
          }
          else {
            splitter.setFirstSize(myInitialHeight.get() + myLastPoint.get().y - myPressPoint.get().y);
          }
        }
        if (component instanceof Splitter) {
          Splitter splitter = (Splitter)component;
          splitter.setProportion(Math.max(0, Math.min(1, 1f - (float)(myInitialHeight.get() + myPressPoint.get().y - myLastPoint.get().y )/ splitter.getHeight())));
        }
      }
    };

    c.addMouseMotionListener(mouseAdapter);
    c.addMouseListener(mouseAdapter);

    c.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component comp, final int x, final int y) {
        final Content content = c instanceof BaseLabel ? ((BaseLabel)c).getContent() : null;
        ui.showContextMenu(comp, x, y, ui.window.getPopupGroup(), content);
      }
    });

    c.putClientProperty(TOOLWINDOW_UI_INSTALLED, Boolean.TRUE);
  }

  private void initActionGroup(@NotNull DefaultActionGroup group, @Nullable Content content) {
    if (content == null) {
      return;
    }

    group.addSeparator();
    group.add(new TabbedContentAction.CloseAction(content));
    group.add(closeAllAction);
    group.add(new TabbedContentAction.CloseAllButThisAction(content));
    group.addSeparator();
    if (content.isPinnable()) {
      group.add(PinToolwindowTabAction.getPinAction());
      group.addSeparator();
    }

    group.add(nextTabAction);
    group.add(previousTabAction);
    group.add(showContent);

    if (content instanceof TabbedContent && ((TabbedContent)content).hasMultipleTabs()) {
      group.addAction(createSplitTabsAction((TabbedContent)content));
    }

    if (Boolean.TRUE == content.getUserData(Content.TABBED_CONTENT_KEY)) {
      TabGroupId groupId = content.getUserData(Content.TAB_GROUP_ID_KEY);
      if (groupId != null) {
        group.addAction(createMergeTabsAction(contentManager, groupId));
      }
    }

    group.addSeparator();
  }

  public void showContextMenu(Component comp, int x, int y, ActionGroup toolWindowGroup, @Nullable Content selectedContent) {
    if (selectedContent == null && toolWindowGroup == null) {
      return;
    }

    DefaultActionGroup configuredGroup = (DefaultActionGroup)ActionManager.getInstance().getAction("ToolWindowContextMenu");
    DefaultActionGroup group = new DefaultActionGroup();
    group.copyFromGroup(configuredGroup);
    if (selectedContent != null) {
      initActionGroup(group, selectedContent);
    }

    if (toolWindowGroup != null) {
      group.addAll(toolWindowGroup);
    }

    final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group);
    popupMenu.getComponent().show(comp, x, y);
  }

  @NotNull
  private static AnAction createSplitTabsAction(@NotNull TabbedContent content) {
    return new DumbAwareAction(IdeBundle.message("action.text.split.0.group", content.getTitlePrefix())) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        content.split();
      }
    };
  }

  @NotNull
  private static AnAction createMergeTabsAction(@NotNull ContentManager manager, @NotNull TabGroupId groupId) {
    return new DumbAwareAction(IdeBundle.message("action.text.merge.tabs.to.0.group", groupId.getDisplayName())) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ContentUtilEx.mergeTabs(manager, groupId);
      }
    };
  }

  private void processHide(@NotNull MouseEvent e) {
    IdeEventQueue.getInstance().blockNextEvents(e);
    final Component c = e.getComponent();
    if (c instanceof BaseLabel) {
      final BaseLabel tab = (BaseLabel)c;
      if (tab.getContent() != null) {
        if (contentManager.canCloseContents() && tab.getContent().isCloseable()) {
          contentManager.removeContent(tab.getContent(), true, true, true);
        }
        else {
          if (contentManager.getContentCount() == 1) {
            hideWindow(e);
          }
        }
      }
      else {
        hideWindow(e);
      }
    }
    else {
      hideWindow(e);
    }
  }

  private void hideWindow(@NotNull MouseEvent e) {
    if (e.isControlDown()) {
      window.fireHiddenSide();
    }
    else {
      window.fireHidden();
    }
  }

  @Override
  @Nullable
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.TOOL_WINDOW.is(dataId)) {
      return window;
    }
    else if (CommonDataKeys.PROJECT.is(dataId)) {
      return window.getToolWindowManager().getProject();
    }
    else if (CloseAction.CloseTarget.KEY.is(dataId)) {
      return computeCloseTarget();
    }
    else if (MorePopupAware.KEY.is(dataId)) {
      ContentLayout layout = getCurrentLayout();
      return  (layout instanceof TabContentLayout) ? layout : null;
    }
    return null;
  }

  @NotNull
  private CloseAction.CloseTarget computeCloseTarget() {
    if (contentManager.canCloseContents()) {
      Content selected = contentManager.getSelectedContent();
      if (selected != null && selected.isCloseable()) {
        return new CloseContentTarget(selected);
      }
    }

    return new HideToolwindowTarget();
  }

  private final class HideToolwindowTarget implements CloseAction.CloseTarget {
    @Override
    public void close() {
      window.fireHidden();
    }
  }

  private final class CloseContentTarget implements CloseAction.CloseTarget {
    private final Content myContent;

    private CloseContentTarget(Content content) {
      myContent = content;
    }

    @Override
    public void close() {
      contentManager.removeContent(myContent, true, true, true);
    }
  }

  boolean isCurrent(ContentLayout layout) {
    return getCurrentLayout() == layout;
  }

  public static void toggleContentPopup(@NotNull ToolWindowContentUi content, @NotNull ContentManager contentManager) {
    toggleContentPopup(content, contentManager, null);
  }

  static void toggleContentPopup(@NotNull ToolWindowContentUi content, @NotNull ContentManager contentManager, JBPopupListener listener) {
    SelectContentStep step = new SelectContentStep(contentManager.getContents());
    Content selectedContent = contentManager.getSelectedContent();
    if (selectedContent != null) {
      step.setDefaultOptionIndex(contentManager.getIndexOfContent(selectedContent));
    }

    ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    if (listener != null) popup.addListener(listener);
    content.getCurrentLayout().showContentPopup(popup);

    if (selectedContent instanceof TabbedContent) {
      new Alarm(Alarm.ThreadToUse.SWING_THREAD, popup).addRequest(() -> popup.handleSelect(false), 50);
    }
  }

  private final class TabPanel extends JPanel implements UISettingsListener {
    private TabPanel() {
      super(new MigLayout(MigLayoutUtilKt.createLayoutConstraints(0, 0).noVisualPadding().fillY()));

      setOpaque(false);
      setBorder(JBUI.Borders.emptyRight(2));
    }

    @Override
    public void uiSettingsChanged(@NotNull UISettings uiSettings) {
      revalidate();
      repaint();
    }

    @Override
    public void doLayout() {
      getCurrentLayout().layout();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      getCurrentLayout().paintComponent(g);
    }

    @Override
    public Dimension getMinimumSize() {
      Insets insets = getInsets();
      return new Dimension(insets.left + insets.right + getCurrentLayout().getMinimumWidth(), super.getMinimumSize().height);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = new Dimension();
      size.height = 0;
      size.width = TabContentLayout.TAB_LAYOUT_START + getInsets().left + getInsets().right;
      for (int i = 0; i < getComponentCount(); i++) {
        final Component each = getComponent(i);
        size.height = Math.max(each.getPreferredSize().height, size.height);
        size.width += each.getPreferredSize().width;
      }

      size.width = Math.max(size.width, getMinimumSize().width);
      return size;
    }
  }
}
