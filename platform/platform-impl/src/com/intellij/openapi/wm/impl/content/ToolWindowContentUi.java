// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.CloseAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.toolWindow.InternalDecoratorImpl;
import com.intellij.toolWindow.ToolWindowEventSource;
import com.intellij.toolWindow.ToolWindowHeader;
import com.intellij.toolWindow.ToolWindowPane;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.content.*;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.ui.content.tabs.TabbedContentAction;
import com.intellij.ui.tabs.impl.MorePopupAware;
import com.intellij.util.Alarm;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.LocationOnDragTracker;
import com.intellij.util.ui.StatusText;
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

public final class ToolWindowContentUi implements ContentUI, UiCompatibleDataProvider {
  // when client property is put in a toolwindow component, hides toolwindow label
  public static final @NonNls String HIDE_ID_LABEL = "HideIdLabel";
  // when client property is set to true in a toolwindow component, the toolbar is always visible in the tool window header
  public static final @NonNls Key<Boolean> DONT_HIDE_TOOLBAR_IN_HEADER = Key.create("DontHideToolbarInHeader");
  private static final @NonNls String TOOLWINDOW_UI_INSTALLED = "ToolWindowUiInstalled";
  public static final DataKey<BaseLabel> SELECTED_CONTENT_TAB_LABEL = DataKey.create("SELECTED_CONTENT_TAB_LABEL");
  @ApiStatus.Internal public static final String HEADER_ICON = "HeaderIcon";

  @ApiStatus.Internal
  public static final DataKey<ToolWindowContentUi> DATA_KEY = DataKey.create("ToolWindowContentUi");

  @ApiStatus.Experimental
  public static final Key<Boolean> NOT_SELECTED_TAB_ICON_TRANSPARENT = Key.create("NotSelectedIconTransparent");

  private final @NotNull ContentManager contentManager;
  int dropOverIndex = -1;
  int dropOverWidth = 0;

  public @NotNull ContentManager getContentManager() {
    return contentManager;
  }

  private final JPanel contentComponent;
  final ToolWindowImpl window;

  private final TabContentLayout tabsLayout;
  private ContentLayout comboLayout;

  private ToolWindowContentUiType type;

  public Predicate<Point> isResizableArea = __ -> true;

  private final JPanel tabComponent = new TabPanel();
  private final DefaultActionGroup tabActionGroup = new DefaultActionGroup();
  private ActionToolbar tabToolbar = null;

  public ToolWindowContentUi(@NotNull ToolWindowImpl window,
                             @NotNull ContentManager contentManager,
                             @NotNull JPanel contentComponent) {
    this.contentManager = contentManager;
    type = window.getWindowInfo().getContentUiType();
    tabsLayout = new SingleContentLayout(this);
    this.window = window;
    this.contentComponent = contentComponent;

    getCurrentLayout().init(contentManager);

    ContentManagerListener contentManagerListener = new ContentManagerListener() {
      private final PropertyChangeListener propertyChangeListener = new PropertyChangeListener() {
        /**
         * @see Content#PROP_TAB_LAYOUT
         */
        @Override
        public void propertyChange(PropertyChangeEvent event) {
          if (Content.PROP_COMPONENT.equals(event.getPropertyName())) {
            ensureSelectedContentVisible();
          }
          update();
        }
      };

      @Override
      public void contentAdded(@NotNull ContentManagerEvent event) {
        Content content = event.getContent();
        ContentManager manager = content.getManager();
        // merge subContents to the main content if they are together inside one content manager
        if (manager != null && !(content instanceof SingleContentLayout.SubContent)) {
          List<Content> contents = manager.getContentsRecursively();
          List<Content> mainContents = contents.stream().filter(c -> !(c instanceof SingleContentLayout.SubContent)).toList();
          List<Content> subContents = contents.stream().filter(c -> c instanceof SingleContentLayout.SubContent).toList();
          if (mainContents.size() == 1) {
            Content mainContent = mainContents.get(0);
            JComponent component = mainContent.getComponent();
            SingleContentSupplier supplier = SingleContentSupplier.Companion.getSupplierFrom(component);
            if (supplier != null && supplier.getSubContents().containsAll(subContents)) {
              for (Content subContent : subContents) {
                ContentManager m = subContent.getManager();
                if (m != null) m.removeContent(subContent, false);
                ((SingleContentLayout.SubContent)subContent).getInfo().setHidden(false);
              }
            }
          }
        }

        getCurrentLayout().contentAdded(event);
        content.addPropertyChangeListener(propertyChangeListener);
        rebuild();

        if (window.isToHideOnEmptyContent()) {
          window.setAvailable(true);
        }
      }

      @Override
      public void contentRemoved(@NotNull ContentManagerEvent event) {
        if (window.isDisposed() || window.toolWindowManager.getProject().isDisposed()) {
          return;
        }

        Content content = event.getContent();
        if (!Content.TEMPORARY_REMOVED_KEY.get(content, false)) {
          SingleContentSupplier.removeSubContentsOfContent(content, false);
        }

        content.removePropertyChangeListener(propertyChangeListener);
        getCurrentLayout().contentRemoved(event);
        ensureSelectedContentVisible();
        rebuild();

        if (contentManager.isEmpty() &&
            contentManager == window.getContentManager() &&
            !Content.TEMPORARY_REMOVED_KEY.get(content, false)) {
          boolean removeFromStripe;
          if (window.isToHideOnEmptyContent()) {
            removeFromStripe = true;
          }
          else if (window.canCloseContents() && StatusText.getDefaultEmptyText().equals(window.getEmptyText().getText())) {
            removeFromStripe = false;
          }
          else {
            return;
          }
          window.toolWindowManager
            .hideToolWindow(window.getId(), /* hideSide = */ false, /* moveFocus = */ true, removeFromStripe, /* source = */ null);
        }
      }

      @Override
      public void selectionChanged(@NotNull ContentManagerEvent event) {
        ensureSelectedContentVisible();

        update();
        contentComponent.revalidate();
        contentComponent.repaint();
      }
    };
    contentManager.addContentManagerListener(contentManagerListener);
    // some tool windows clients can use contentManager.removeAllContents(true)
    // - ensure that we don't receive such events if a window is already disposed
    Disposer.register(window.getDisposable(), new Disposable() {
      @Override
      public void dispose() {
        contentManager.removeContentManagerListener(contentManagerListener);
      }
    });

    initMouseListeners(tabComponent, this, true);
    MouseDragHelper.setComponentDraggable(tabComponent, true);
  }

  public @NotNull String getToolWindowId() {
    return window.getId();
  }

  public @NotNull ToolWindow getWindow() {
    return window;
  }

  public @NotNull JPanel getTabComponent() {
    return tabComponent;
  }

  public @NotNull DefaultActionGroup getTabToolbarActions() {
    return tabActionGroup;
  }

  public @Nullable ActionToolbar getTabToolbar() {
    return tabToolbar;
  }

  /**
   * Adds tab toolbar to the tab panel.
   */
  public void connectTabToolbar() {
    if (tabToolbar != null) {
      tabComponent.add(tabToolbar.getComponent());
    }
  }

  /**
   * Removes tab toolbar from the tab panel.
   */
  public void disconnectTabToolbar() {
    if (tabToolbar != null) {
      tabComponent.remove(tabToolbar.getComponent());
    }
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

    ToolWindowManagerImpl manager = window.toolWindowManager;
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

  @ApiStatus.Internal
  public @NotNull ContentLayout getCurrentLayout() {
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

    JComponent replacement = selected.getUserData(Content.REPLACEMENT_COMPONENT);
    JComponent newComponent = replacement != null ? replacement : selected.getComponent();

    contentComponent.removeAll();
    contentComponent.add(newComponent, BorderLayout.CENTER);

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

  @Override
  public @NotNull String getCloseActionName() {
    return getCurrentLayout().getCloseActionName();
  }

  @Override
  public @NotNull String getCloseAllButThisActionName() {
    return getCurrentLayout().getCloseAllButThisActionName();
  }

  @Override
  public @NotNull String getPreviousContentActionName() {
    return getCurrentLayout().getPreviousContentActionName();
  }

  @Override
  public @NotNull String getNextContentActionName() {
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
          if (parent instanceof ToolWindowPane) {
            return parent;
          }
          component = parent;
          parent = parent.getParent();
        }
        return null;
      }

      private void arm(Component c) {
        Component component = c != null ? getActualSplitter() : null;
        if (component instanceof ThreeComponentsSplitter splitter) {
          myIsLastComponent.set(SwingUtilities.isDescendingFrom(c, splitter.getLastComponent()));
          myInitialHeight.set(myIsLastComponent.get() ? splitter.getLastSize() : splitter.getFirstSize());
          return;
        }
        if (component instanceof Splitter splitter) {
          myIsLastComponent.set(true);
          myInitialHeight.set(splitter.getSecondComponent().getHeight());
          return;
        }
        if (component instanceof ToolWindowPane) {
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
        if (!isToolWindowDrag(e)) {
          myLastPoint.set(e.getLocationOnScreen());
          myPressPoint.set(myLastPoint.get());
          myDragTracker.set(LocationOnDragTracker.startDrag(e));
          if (allowResize && ui.isResizeable()) {
            arm(c.getComponentAt(e.getPoint()) == c && ui.isResizeable(e.getPoint()) ? c : null);
          }
        }
        ui.window.fireActivated(ToolWindowEventSource.ToolWindowHeader);
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
        if (!AdvancedSettings.getBoolean("ide.tool.window.header.dnd")) {
          return false;
        }

        Component realMouseTarget = SwingUtilities.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());
        Component decorator = InternalDecoratorImpl.findTopLevelDecorator(realMouseTarget);
        if (decorator == null || ui.window.getType() == ToolWindowType.FLOATING || ui.window.getType() == ToolWindowType.WINDOWED) {
          return false;
        }
        if (ui.window.getAnchor() != ToolWindowAnchor.BOTTOM ||
            SwingUtilities.convertMouseEvent(e.getComponent(), e, decorator).getY() >
            ToolWindowPane.Companion.getHeaderResizeArea()) {
          return true;
        }
        //it's drag, not resize!
        return false;
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        if (myLastPoint.isNull() || myPressPoint.isNull() || myDragTracker.isNull()) return;
        //"Dock" modes,
        // for "Undock" mode processing see com.intellij.toolWindow.InternalDecoratorImpl.ResizeOrMoveDocketToolWindowMouseListener
        Point newMouseLocation = e.getLocationOnScreen();

        Window window = SwingUtilities.windowForComponent(c);
        if (!(window instanceof IdeFrame)) {
          myDragTracker.get().updateLocationOnDrag(window, newMouseLocation);
        }
        myLastPoint.set(newMouseLocation);
        Component component = getActualSplitter();
        if (isToolWindowDrag(e)) return;//it's drag, not resize!
        if (component instanceof ThreeComponentsSplitter splitter) {
          if (myIsLastComponent.get() == Boolean.TRUE) {
            splitter.setLastSize(myInitialHeight.get() + myPressPoint.get().y - myLastPoint.get().y);
          }
          else {
            splitter.setFirstSize(myInitialHeight.get() + myLastPoint.get().y - myPressPoint.get().y);
          }
        }
        if (component instanceof Splitter splitter) {
          splitter.setProportion(Math.max(0, Math.min(1, 1f - (float)(myInitialHeight.get() + myPressPoint.get().y - myLastPoint.get().y )/ splitter.getHeight())));
        }
        if (component instanceof ToolWindowPane) {
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
        ui.showContextMenu(comp, x, y, ui.window.createPopupGroup(false), content);
      }
    });

    c.putClientProperty(TOOLWINDOW_UI_INSTALLED, Boolean.TRUE);
  }

  private void initActionGroup(@NotNull DefaultActionGroup group, @Nullable Content content) {
    if (content == null) {
      return;
    }
    var actionManager = ActionManager.getInstance();

    group.addSeparator();
    group.add(new TabbedContentAction.CloseAction(content));
    group.add(actionManager.getAction("TW.CloseAllTabs"));
    group.add(actionManager.getAction("TW.CloseOtherTabs"));
    group.addSeparator();
    if (isTabsReorderingAllowed(window)) {
      group.add(actionManager.getAction("TW.SplitRight"));
      group.add(actionManager.getAction("TW.SplitAndMoveRight"));
      group.add(actionManager.getAction("TW.SplitDown"));
      group.add(actionManager.getAction("TW.SplitAndMoveDown"));
      group.add(actionManager.getAction("TW.Unsplit"));
      group.addSeparator();
    }
    if (content.isPinnable()) {
      group.add(PinToolwindowTabAction.getPinAction());
      group.addSeparator();
    }

    group.add(actionManager.getAction("NextTab"));
    group.add(actionManager.getAction("PreviousTab"));
    group.add(actionManager.getAction("ShowContent"));

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
      group.add(toolWindowGroup);
    }

    final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group);
    popupMenu.getComponent().show(comp, x, y);
  }

  private static @NotNull AnAction createSplitTabsAction(@NotNull TabbedContent content) {
    return new DumbAwareAction(IdeBundle.message("action.text.split.group", content.getTitlePrefix())) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        content.split();
      }
    };
  }

  private static @NotNull AnAction createMergeTabsAction(@NotNull ContentManager manager, @NotNull TabGroupId groupId) {
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
    if (c instanceof BaseLabel tab) {
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
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformDataKeys.TOOL_WINDOW, window);
    sink.set(PlatformCoreDataKeys.HELP_ID, window.getHelpId());
    sink.set(CommonDataKeys.PROJECT, window.toolWindowManager.getProject());
    sink.set(CloseAction.CloseTarget.KEY, computeCloseTarget());
    if (getCurrentLayout() instanceof MorePopupAware o) {
      sink.set(MorePopupAware.KEY_TOOLWINDOW_TITLE, o);
    }
    if (type == ToolWindowContentUiType.TABBED) {
      sink.set(SELECTED_CONTENT_TAB_LABEL, tabsLayout.findTabLabelByContent(contentManager.getSelectedContent()));
    }
  }

  public void setTabActions(@NotNull List<AnAction> actions) {
    if (tabToolbar == null) {
      tabToolbar =
          ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_TITLE, new DefaultActionGroup(tabActionGroup), true);
      ActionToolbarImpl tabToolbarImpl = (ActionToolbarImpl)tabToolbar;
      ToolWindowHeader header = ComponentUtil.getParentOfType(ToolWindowHeader.class, tabComponent);
      tabToolbarImpl.setTargetComponent(header);
      tabToolbarImpl.setForceMinimumSize(true);
      tabToolbarImpl.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
      tabToolbarImpl.setReservePlaceAutoPopupIcon(false);
      tabToolbarImpl.setOpaque(false);
      tabToolbarImpl.setBorder(JBUI.Borders.empty());
      if (tabComponent.isShowing()) {
        tabComponent.add(tabToolbarImpl);
      }
    }
    tabActionGroup.removeAll();
    tabActionGroup.addSeparator();
    tabActionGroup.addAll(actions);
    if (tabComponent.isShowing()) {
      tabToolbar.updateActionsAsync();
    }
  }

  private @NotNull CloseAction.CloseTarget computeCloseTarget() {
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
    SelectContentStep step = new SelectContentStep(contentManager.getContents());
    Content selectedContent = contentManager.getSelectedContent();
    if (selectedContent != null) {
      step.setDefaultOptionIndex(contentManager.getIndexOfContent(selectedContent));
    }

    ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
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

  /** Checks if the selected content component or one of its descendants has focus. */
  @ApiStatus.Internal public Boolean isActive() {
    return UIUtil.isFocusAncestor(contentComponent);
  }

  /**
   * @deprecated please use {@link ToolWindowContentUi#setAllowTabsReordering(ToolWindow, boolean)} instead.
   */
  @Deprecated
  public static final @NonNls Key<Boolean> ALLOW_DND_FOR_TABS = Key.create("AllowDragAndDropForTabs");

  @ApiStatus.Internal
  public static final Key<Boolean> ALLOW_TABS_REORDERING = ALLOW_DND_FOR_TABS;

  /**
   * If {@code allow} parameter is specified as {@code true} then it will be possible to reorder and split
   * tabs of the provided tool window using drag and drop and specific actions, such as
   * {@link com.intellij.ide.actions.ToolWindowSplitRightAction}.
   */
  public static void setAllowTabsReordering(@NotNull ToolWindow toolWindow, boolean allow) {
    toolWindow.getComponent().putClientProperty(ALLOW_TABS_REORDERING, allow);
  }

  /**
   * @return whether reorder and split of tabs in the provided tool window is allowed.
   */
  public static boolean isTabsReorderingAllowed(@NotNull ToolWindow window) {
    return ClientProperty.isTrue(window.getComponent(), ALLOW_TABS_REORDERING) &&
           Registry.is("ide.allow.split.and.reorder.in.tool.window", false);
  }

  public final class TabPanel extends NonOpaquePanel implements UISettingsListener {
    private TabPanel() {
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
        if (each.isVisible() || tabToolbar != null && each == tabToolbar.getComponent()) {
          size.height = Math.max(each.getPreferredSize().height, size.height);
          size.width += each.getPreferredSize().width;
        }
      }

      size.width = Math.max(size.width, getMinimumSize().width);
      return size;
    }
  }
}
