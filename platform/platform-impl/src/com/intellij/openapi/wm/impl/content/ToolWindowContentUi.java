// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.CloseAction;
import com.intellij.ide.actions.ShowContentAction;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.content.*;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.ui.content.tabs.TabbedContentAction;
import com.intellij.util.Alarm;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.EmptyIterator;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.Predicate;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ToolWindowContentUi extends JPanel implements ContentUI, PropertyChangeListener, DataProvider {
  public static final String POPUP_PLACE = "ToolwindowPopup";
  // when client property is put in toolwindow component, hides toolwindow label
  public static final String HIDE_ID_LABEL = "HideIdLabel";

  ContentManager myManager;

  final JPanel myContent = new JPanel(new BorderLayout());
  ToolWindowImpl myWindow;

  TabbedContentAction.CloseAllAction myCloseAllAction;
  TabbedContentAction.MyNextTabAction myNextTabAction;
  TabbedContentAction.MyPreviousTabAction myPreviousTabAction;

  ShowContentAction myShowContent;

  TabContentLayout myTabsLayout = new TabContentLayout(this);
  ContentLayout myComboLayout = new ComboContentLayout(this);

  private ToolWindowContentUiType myType = ToolWindowContentUiType.TABBED;

  public Predicate<Point> isResizableArea = p -> true;

  public ToolWindowContentUi(ToolWindowImpl window) {
    myWindow = window;
    myContent.setOpaque(false);
    myContent.setFocusable(false);
    setOpaque(false);

    setBorder(new EmptyBorder(0, 0, 0, 2));

    UIUtil.putClientProperty(
      this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, new Iterable<JComponent>() {
        @Override
        public Iterator<JComponent> iterator() {
          if (myManager == null || myManager.getContentCount() == 0) {
            return EmptyIterator.getInstance();
          }
          return JBIterable.of(myManager.getContents())
            .map(content -> {
              JComponent last = null;
              for (Component c : UIUtil.uiParents(content.getComponent(), false)) {
                if (c == myManager.getComponent() || !(c instanceof JComponent)) return null;
                last = (JComponent)c;
              }
              return last;
            })
            .filter(Conditions.notNull())
            .iterator();
        }
      });

    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(UISettingsListener.TOPIC, uiSettings -> {
        revalidate();
        repaint();
    });
  }

  private boolean isResizeable() {
    if (myWindow.getType() == ToolWindowType.FLOATING || myWindow.getType() == ToolWindowType.WINDOWED) return false;
    if (myWindow.getAnchor() == ToolWindowAnchor.BOTTOM) return true;
    if (myWindow.getAnchor() == ToolWindowAnchor.TOP) return false;
    if (!myWindow.isSplitMode()) return false;
    ToolWindowManagerImpl manager = myWindow.getToolWindowManager();
    List<String> ids = manager.getIdsOn(myWindow.getAnchor());
    for (String id : ids) {
      if (id.equals(myWindow.getId())) continue;
      ToolWindow window = manager.getToolWindow(id);
      if (window != null && window.isVisible() && (window.getType() == ToolWindowType.DOCKED || window.getType() == ToolWindowType.SLIDING)) {
        return true;
      }
    }
    return false;
  }

  private boolean isResizeable(@NotNull Point point) {
    return isResizableArea.apply(point);
  }

  public void setType(@NotNull ToolWindowContentUiType type) {
    if (myType != type) {

      if (myType != null) {
        getCurrentLayout().reset();
      }

      myType = type;

      getCurrentLayout().init();
      rebuild();
    }
  }

  private ContentLayout getCurrentLayout() {
    assert myManager != null;
    return myType == ToolWindowContentUiType.TABBED ? myTabsLayout : myComboLayout;
  }

  @Override
  public JComponent getComponent() {
    return myContent;
  }

  public JComponent getTabComponent() {
    return this;
  }

  @Override
  public void setManager(@NotNull final ContentManager manager) {
    if (myManager != null) {
      getCurrentLayout().reset();
    }

    myManager = manager;

    getCurrentLayout().init();

    myManager.addContentManagerListener(new ContentManagerListener() {
      @Override
      public void contentAdded(@NotNull final ContentManagerEvent event) {
        getCurrentLayout().contentAdded(event);
        event.getContent().addPropertyChangeListener(ToolWindowContentUi.this);
        rebuild();
      }

      @Override
      public void contentRemoved(@NotNull final ContentManagerEvent event) {
        event.getContent().removePropertyChangeListener(ToolWindowContentUi.this);
        getCurrentLayout().contentRemoved(event);
        ensureSelectedContentVisible();
        rebuild();
      }

      @Override
      public void contentRemoveQuery(@NotNull final ContentManagerEvent event) {
      }

      @Override
      public void selectionChanged(@NotNull final ContentManagerEvent event) {
        ensureSelectedContentVisible();

        update();

        myContent.revalidate();
        myContent.repaint();
      }
    });

    initMouseListeners(this, this, true);

    rebuild();

    myCloseAllAction = new TabbedContentAction.CloseAllAction(myManager);
    myNextTabAction = new TabbedContentAction.MyNextTabAction(myManager);
    myPreviousTabAction = new TabbedContentAction.MyPreviousTabAction(myManager);
    myShowContent = new ShowContentAction(myWindow, myContent, myManager);
  }

  private void ensureSelectedContentVisible() {
    final Content selected = myManager.getSelectedContent();
    if (selected == null) {
      myContent.removeAll();
      return;
    }

    if (myContent.getComponentCount() == 1) {
      final Component visible = myContent.getComponent(0);
      if (visible == selected.getComponent()) return;
    }

    myContent.removeAll();
    myContent.add(selected.getComponent(), BorderLayout.CENTER);

    myContent.revalidate();
    myContent.repaint();
  }


  private void rebuild() {
    getCurrentLayout().rebuild();
    getCurrentLayout().update();

    revalidate();
    repaint();

    if (myManager.getContentCount() == 0 && myWindow.isToHideOnEmptyContent()) {
      myWindow.hide(null);
    }
  }



  @Override
  public void doLayout() {
    getCurrentLayout().layout();
  }


  @Override
  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    getCurrentLayout().paintComponent(g);
  }

  @Override
  protected void paintChildren(final Graphics g) {
    super.paintChildren(g);
    getCurrentLayout().paintChildren(g);
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

  @Override
  public void propertyChange(final PropertyChangeEvent evt) {
    update();
  }

  private void update() {
    getCurrentLayout().update();

    revalidate();
    repaint();
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
  public void beforeDispose() {
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

  public void setTabDoubleClickActions(@NotNull AnAction... actions) {
    myTabsLayout.setTabDoubleClickActions(actions);
  }

  public static void initMouseListeners(final JComponent c, final ToolWindowContentUi ui, final boolean allowResize) {
    if (c.getClientProperty(ui) != null) return;

    MouseAdapter mouseAdapter = new MouseAdapter() {
      final Ref<Point> myLastPoint = Ref.create();
      final Ref<Point> myPressPoint = Ref.create();
      final Ref<Integer> myInitialHeight = Ref.create(0);
      final Ref<Boolean> myIsLastComponent = Ref.create();


      private Component getActualSplitter() {
        if (!allowResize || !ui.isResizeable()) return null;

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
      }

      @Override
      public void mousePressed(MouseEvent e) {
        PointerInfo info = MouseInfo.getPointerInfo();
        if (!e.isPopupTrigger()) {
          if (!UIUtil.isCloseClick(e)) {
            myLastPoint.set(info != null ? info.getLocation() : e.getLocationOnScreen());
            myPressPoint.set(myLastPoint.get());
            if (allowResize && ui.isResizeable()) {
              arm(c.getComponentAt(e.getPoint()) == c && ui.isResizeable(e.getPoint()) ? c : null);
            }
            ui.myWindow.fireActivated();
          }
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
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
        if (myLastPoint.isNull() || myPressPoint.isNull()) return;

        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) return;
        final Point newPoint = info.getLocation();
        Point p = myLastPoint.get();

        final Window window = SwingUtilities.windowForComponent(c);
        if (!(window instanceof IdeFrame)) {
          final Point windowLocation = window.getLocationOnScreen();
          windowLocation.translate(newPoint.x - p.x, newPoint.y - p.y);
          window.setLocation(windowLocation);
        }

        myLastPoint.set(newPoint);
        Component component = getActualSplitter();
        if (component instanceof ThreeComponentsSplitter) {
          ThreeComponentsSplitter splitter = (ThreeComponentsSplitter)component;
          if (myIsLastComponent.get() == Boolean.TRUE) {
            splitter.setLastSize(myInitialHeight.get() + myPressPoint.get().y - myLastPoint.get().y);
          } else {
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
        ui.showContextMenu(comp, x, y, ui.myWindow.getPopupGroup(), content);
      }
    });

    c.putClientProperty(ui, Boolean.TRUE);
  }

  private void initActionGroup(DefaultActionGroup group, final Content content) {
    if (content == null) {
      return;
    }
    group.addSeparator();
    group.add(new TabbedContentAction.CloseAction(content));
    group.add(myCloseAllAction);
    group.add(new TabbedContentAction.CloseAllButThisAction(content));
    group.addSeparator();
    if (content.isPinnable()) {
      group.add(PinToolwindowTabAction.getPinAction());
      group.addSeparator();
    }

    group.add(myNextTabAction);
    group.add(myPreviousTabAction);
    group.add(myShowContent);

    if (content instanceof TabbedContent && ((TabbedContent)content).hasMultipleTabs()) {
      group.addAction(createSplitTabsAction((TabbedContent)content));
    }

    if (Boolean.TRUE == content.getUserData(Content.TABBED_CONTENT_KEY)) {
      final String groupName = content.getUserData(Content.TAB_GROUP_NAME_KEY);
      if (groupName != null) {
        group.addAction(createMergeTabsAction(myManager, groupName));
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

    final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(POPUP_PLACE, group);
    popupMenu.getComponent().show(comp, x, y);
  }

  private static AnAction createSplitTabsAction(final TabbedContent content) {
    return new DumbAwareAction("Split '" + content.getTitlePrefix() + "' group") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        content.split();
      }
    };
  }

  private static AnAction createMergeTabsAction(final ContentManager manager, final String tabPrefix) {
    return new DumbAwareAction("Merge tabs to '" + tabPrefix + "' group") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        final Content selectedContent = manager.getSelectedContent();
        final List<Pair<String, JComponent>> tabs = new ArrayList<>();
        int selectedTab = -1;
        List<Content> mergedContent = ContainerUtil.newArrayList();
        for (Content content : manager.getContents()) {
          if (tabPrefix.equals(content.getUserData(Content.TAB_GROUP_NAME_KEY))) {
            final String label = content.getTabName().substring(tabPrefix.length() + 2);
            final JComponent component = content.getComponent();
            if (content == selectedContent) {
              selectedTab = tabs.size();
            }
            tabs.add(Pair.create(label, component));
            manager.removeContent(content, false);
            content.setComponent(null);
            content.setShouldDisposeContent(false);
            mergedContent.add(content);
          }
        }
        PropertiesComponent.getInstance().unsetValue(TabbedContent.SPLIT_PROPERTY_PREFIX + tabPrefix);
        for (int i = 0; i < tabs.size(); i++) {
          final Pair<String, JComponent> tab = tabs.get(i);
          ContentUtilEx.addTabbedContent(manager, tab.second, tabPrefix, tab.first, i == selectedTab);
        }
        mergedContent.forEach(Disposer::dispose);
      }
    };
  }

  private void processHide(final MouseEvent e) {
    IdeEventQueue.getInstance().blockNextEvents(e);
    final Component c = e.getComponent();
    if (c instanceof BaseLabel) {
      final BaseLabel tab = (BaseLabel)c;
      if (tab.getContent() != null) {
        if (myManager.canCloseContents() && tab.getContent().isCloseable()) {
          myManager.removeContent(tab.getContent(), true, true, true);
        } else {
          if (myManager.getContentCount() == 1) {
            hideWindow(e);
          }
        }
      } else {
        hideWindow(e);
      }
    }
    else {
      hideWindow(e);
    }
  }

  private void hideWindow(final MouseEvent e) {
    if (e.isControlDown()) {
      myWindow.fireHiddenSide();
    }
    else {
      myWindow.fireHidden();
    }
  }

  @Override
  @Nullable
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.TOOL_WINDOW.is(dataId)) {
      return myWindow;
    }

    if (CloseAction.CloseTarget.KEY.is(dataId)) {
      return computeCloseTarget();
    }

    return null;
  }


  private CloseAction.CloseTarget computeCloseTarget() {
    if (myManager.canCloseContents()) {
      Content selected = myManager.getSelectedContent();
      if (selected != null && selected.isCloseable()) {
        return new CloseContentTarget(selected);
      }
    }

    return new HideToolwindowTarget();
  }

  private class HideToolwindowTarget implements CloseAction.CloseTarget {
    @Override
    public void close() {
      myWindow.fireHidden();
    }
  }

  private class CloseContentTarget implements CloseAction.CloseTarget {

    private final Content myContent;

    private CloseContentTarget(Content content) {
      myContent = content;
    }

    @Override
    public void close() {
      myManager.removeContent(myContent, true, true, true);
    }
  }

  @Override
  public void dispose() {
    myContent.removeAll();
  }

  boolean isCurrent(ContentLayout layout) {
    return getCurrentLayout() == layout;
  }

  public void toggleContentPopup() {
    final Content[] contents = myManager.getContents();
    final Content selectedContent = myManager.getSelectedContent();

    final SelectContentStep step = new SelectContentStep(contents);
    if (selectedContent != null) {
      step.setDefaultOptionIndex(myManager.getIndexOfContent(selectedContent));
    }

    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    getCurrentLayout().showContentPopup(popup);

    if (selectedContent instanceof TabbedContent) {
      new Alarm(Alarm.ThreadToUse.SWING_THREAD, popup).addRequest(() -> popup.handleSelect(false), 50);
    }
  }
}
