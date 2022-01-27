// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.openapi.wm.impl.ToolWindowsPane;
import com.intellij.toolWindow.InternalDecoratorImpl;
import com.intellij.toolWindow.ToolWindowEventSource;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.MouseDragHelper;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.content.*;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.ui.content.tabs.TabbedContentAction;
import com.intellij.ui.layout.migLayout.MigLayoutUtilKt;
import com.intellij.ui.layout.migLayout.patched.MigLayout;
import com.intellij.ui.popup.PopupState;
import com.intellij.ui.tabs.impl.MorePopupAware;
import com.intellij.util.Alarm;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.LocationOnDragTracker;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
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
  @NonNls public static final String HIDE_ID_LABEL = "HideIdLabel";
  @NonNls public static final Key<Boolean> ALLOW_DND_FOR_TABS = Key.create("AllowDragAndDropForTabs");
  @NonNls private static final String TOOLWINDOW_UI_INSTALLED = "ToolWindowUiInstalled";
  public static final DataKey<BaseLabel> SELECTED_CONTENT_TAB_LABEL = DataKey.create("SELECTED_CONTENT_TAB_LABEL");

  private final @NotNull ContentManager contentManager;
  int dropOverIndex = -1;
  int dropOverWidth = 0;

  public @NotNull ContentManager getContentManager() {
    return contentManager;
  }

  private final JPanel contentComponent;
  final ToolWindowImpl window;

  private final TabbedContentAction.CloseAllAction closeAllAction;
  private final TabbedContentAction.MyNextTabAction nextTabAction;
  private final TabbedContentAction.MyPreviousTabAction previousTabAction;
  private final TabbedContentAction.SplitTabAction splitRightTabAction;
  private final TabbedContentAction.SplitTabAction splitDownTabAction;
  private final TabbedContentAction.UnsplitTabAction unsplitTabAction;

  private final ShowContentAction showContent;

  private final TabContentLayout tabsLayout;
  private ContentLayout comboLayout;

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
    tabsLayout = new SingleContentLayout(this);
    this.window = window;
    this.contentComponent = contentComponent;

    getCurrentLayout().init(contentManager);

    contentManager.addContentManagerListener(new ContentManagerListener() {
      private final PropertyChangeListener propertyChangeListener = new PropertyChangeListener() {
        /**
         * @see Content#PROP_TAB_LAYOUT
         */
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

        if (window.isToHideOnEmptyContent()) {
          window.setAvailable(true);
        }
      }

      @Override
      public void contentRemoved(@NotNull ContentManagerEvent event) {
        event.getContent().removePropertyChangeListener(propertyChangeListener);
        getCurrentLayout().contentRemoved(event);
        ensureSelectedContentVisible();
        rebuild();

        if (contentManager.isEmpty() && window.isToHideOnEmptyContent()) {
          window.getToolWindowManager().hideToolWindow(window.getId(), false, true, true, null);
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
    MouseDragHelper.setComponentDraggable(tabComponent, true);

    closeAllAction = new TabbedContentAction.CloseAllAction(contentManager);
    nextTabAction = new TabbedContentAction.MyNextTabAction(contentManager);
    previousTabAction = new TabbedContentAction.MyPreviousTabAction(contentManager);
    splitRightTabAction = new TabbedContentAction.SplitTabAction(contentManager, true);
    splitDownTabAction = new TabbedContentAction.SplitTabAction(contentManager, false);
    unsplitTabAction = new TabbedContentAction.UnsplitTabAction(contentManager);
    showContent = new ShowContentAction(window, contentComponent, contentManager);
  }

  @NotNull
  public String getToolWindowId() {
    return window.getId();
  }

  @NotNull
  public ToolWindow getWindow() {
    return window;
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
      if (comboLayout == null) {
        comboLayout = new ComboContentLayout(this);
      }
      return comboLayout;
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

  @ApiStatus.Internal
  public void rebuild() {
    getCurrentLayout().rebuild();
    getCurrentLayout().update();

    tabComponent.revalidate();
    tabComponent.repaint();
  }

  public void update() {
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
    initMouseListeners(c, ui, allowResize, false);
  }

  public static void initMouseListeners(@NotNull JComponent c, @NotNull ToolWindowContentUi ui, boolean allowResize, boolean allowDrag) {
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
          if (parent instanceof ToolWindowsPane) {
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
        if (component instanceof ToolWindowsPane) {
          myIsLastComponent.set(ui.window.getAnchor() == ToolWindowAnchor.BOTTOM || ui.window.getAnchor() == ToolWindowAnchor.RIGHT);
          myInitialHeight.set(ui.window.getAnchor().isHorizontal() ? ui.window.getDecorator().getHeight() : ui.window.getDecorator().getWidth());
          return;
        }
        myIsLastComponent.set(null);
        myInitialHeight.set(null);
        myPressPoint.set(null);
        myDragTracker.set(null);
      }

      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        if (e.isPopupTrigger() || UIUtil.isCloseClick(e)) return;
        PointerInfo info = MouseInfo.getPointerInfo();
        if (!isToolWindowDrag(e)) {
          myLastPoint.set(info != null ? info.getLocation() : e.getLocationOnScreen());
          myPressPoint.set(myLastPoint.get());
          myDragTracker.set(LocationOnDragTracker.startDrag(e));
          if (allowResize && ui.isResizeable()) {
            arm(c.getComponentAt(e.getPoint()) == c && ui.isResizeable(e.getPoint()) ? c : null);
          }
        }
        ui.window.fireActivated(ToolWindowEventSource.ToolWindowHeader);
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
          if (ui.tabsLayout.myDoubleClickActions.isEmpty() || !(e.getComponent() instanceof ContentTabLabel)) {
            ToolWindowManagerEx manager = ui.window.getToolWindowManager();
            manager.setMaximized(ui.window, !manager.isMaximized(ui.window));
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
        if (isToolWindowDrag(e)) {
          c.setCursor(Cursor.getDefaultCursor());
          return;
        }
        c.setCursor(allowResize && ui.isResizeable() && getActualSplitter() != null && c.getComponentAt(e.getPoint()) == c && ui.isResizeable(e.getPoint())
                    ? Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
                    : Cursor.getDefaultCursor());
      }

      @Override
      public void mouseExited(MouseEvent e) {
        c.setCursor(null);
      }

      private boolean isToolWindowDrag(MouseEvent e) {
        if (!Registry.is("ide.new.tool.window.dnd")) return false;
        Component realMouseTarget = SwingUtilities.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());
        Component decorator = InternalDecoratorImpl.findTopLevelDecorator(realMouseTarget);
        if (decorator == null || ui.window.getType() == ToolWindowType.FLOATING || ui.window.getType() == ToolWindowType.WINDOWED) return false;
        if (ui.window.getAnchor() != ToolWindowAnchor.BOTTOM) return true;
        if (SwingUtilities.convertMouseEvent(e.getComponent(), e, decorator).getY() > ToolWindowsPane.getHeaderResizeArea()) return true;//it's drag, not resize!
        return false;
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        if (myLastPoint.isNull() || myPressPoint.isNull() || myDragTracker.isNull()) return;
        //"Dock" modes,
        // for "Undock" mode processing see com.intellij.toolWindow.InternalDecoratorImpl.ResizeOrMoveDocketToolWindowMouseListener
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) return;
        Point newMouseLocation = info.getLocation();

        Window window = SwingUtilities.windowForComponent(c);
        if (!(window instanceof IdeFrame)) {
          myDragTracker.get().updateLocationOnDrag(window);
        }
        myLastPoint.set(newMouseLocation);
        Component component = getActualSplitter();
        if (isToolWindowDrag(e)) return;//it's drag, not resize!
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
        if (component instanceof ToolWindowsPane) {
          if (ui.window.getType() == ToolWindowType.SLIDING) {
            ui.window.getDecorator().updateBounds(e);
          } else {
            Dimension size = ui.window.getDecorator().getSize();
            if (ui.window.getAnchor().isHorizontal()) {
              size.height = myInitialHeight.get() - myLastPoint.get().y + myPressPoint.get().y;
            }
            ui.window.getDecorator().setSize(size);
          }
        }
      }
    };

    c.addMouseMotionListener(mouseAdapter);
    c.addMouseListener(mouseAdapter);

    c.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component comp, final int x, final int y) {
        final Content content = c instanceof BaseLabel ? ((BaseLabel)c).getContent() : null;
        ui.showContextMenu(comp, x, y, ui.window.createPopupGroup(), content);
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
    Component component = window.getComponent();
    if (Registry.is("ide.allow.split.and.reorder.in.tool.window", false) && ClientProperty.isTrue(component, ALLOW_DND_FOR_TABS)) {
      group.add(splitRightTabAction);
      group.add(splitDownTabAction);
      group.add(unsplitTabAction);
      group.addSeparator();
    }
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
    return new DumbAwareAction(IdeBundle.message("action.text.split.group", content.getTitlePrefix())) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        content.split();
      }
    };
  }

  @NotNull
  private static AnAction createMergeTabsAction(@NotNull ContentManager manager, @NotNull TabGroupId groupId) {
    return new DumbAwareAction(IdeBundle.message("action.text.merge.tabs.to.group", groupId.getDisplayName())) {
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
      window.fireHiddenSide(ToolWindowEventSource.ToolWindowHeaderAltClick);
    }
    else {
      window.fireHidden(ToolWindowEventSource.ToolWindowHeader);
    }
  }

  @Override
  @Nullable
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.TOOL_WINDOW.is(dataId)) {
      return window;
    }
    else if (PlatformCoreDataKeys.HELP_ID.is(dataId)) {
      return window.getHelpId();
    }
    else if (CommonDataKeys.PROJECT.is(dataId)) {
      return window.getToolWindowManager().getProject();
    }
    else if (CloseAction.CloseTarget.KEY.is(dataId)) {
      return computeCloseTarget();
    }
    else if (MorePopupAware.KEY.is(dataId)) {
      ContentLayout layout = getCurrentLayout();
      return  (layout instanceof MorePopupAware) ? layout : null;
    }
    else if (SELECTED_CONTENT_TAB_LABEL.is(dataId) && type == ToolWindowContentUiType.TABBED) {
      return tabsLayout.findTabLabelByContent(contentManager.getSelectedContent());
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
      window.fireHidden(ToolWindowEventSource.CloseAction);
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

  static void toggleContentPopup(@NotNull ToolWindowContentUi content,
                                 @NotNull ContentManager contentManager,
                                 @Nullable PopupState<JBPopup> popupState) {
    SelectContentStep step = new SelectContentStep(contentManager.getContents());
    Content selectedContent = contentManager.getSelectedContent();
    if (selectedContent != null) {
      step.setDefaultOptionIndex(contentManager.getIndexOfContent(selectedContent));
    }

    ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    if (popupState != null) popupState.prepareToShow(popup);
    content.getCurrentLayout().showContentPopup(popup);

    if (selectedContent instanceof TabbedContent) {
      new Alarm(Alarm.ThreadToUse.SWING_THREAD, popup).addRequest(() -> popup.handleSelect(false), 50);
    }
  }

  public void setDropInfoIndex(int dropIndex, int dropWidth) {
    if (dropIndex != dropOverIndex || dropWidth != dropOverWidth) {
      dropOverIndex = dropIndex;
      dropOverWidth = dropWidth;
      dropCaches();
      rebuild();
    }
  }

  public final class TabPanel extends NonOpaquePanel implements UISettingsListener {
    private TabPanel() {
      super(new MigLayout(MigLayoutUtilKt.createLayoutConstraints(0, 0).noVisualPadding().fillY()));
      setBorder(JBUI.Borders.emptyRight(2));
      if (ExperimentalUI.isNewUI()) {
        setBorder(JBUI.Borders.empty());
      }
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
      size.width = TabContentLayout.getTabLayoutStart() + getInsets().left + getInsets().right;
      for (int i = 0; i < getComponentCount(); i++) {
        final Component each = getComponent(i);
        if (each.isVisible()) {
          size.height = Math.max(each.getPreferredSize().height, size.height);
          size.width += each.getPreferredSize().width;
        }
      }

      size.width = Math.max(size.width, getMinimumSize().width);
      return size;
    }
  }
}
