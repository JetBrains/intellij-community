// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.ActivityTracker;
import com.intellij.ide.dnd.DnDSupport;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.MouseDragHelper;
import com.intellij.ui.UIBundle;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.TabbedContent;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.tabs.JBTabPainter;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.impl.MorePopupAware;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

class TabContentLayout extends ContentLayout implements MorePopupAware {
  static final int MORE_ICON_BORDER = 6;
  public static final int TAB_LAYOUT_START = 4;

  protected boolean isSingleContentView = false;
  protected JLabel dropOverPlaceholder;
  private LayoutData lastLayout;

  final List<ContentTabLabel> tabs = new ArrayList<>();
  private final Map<Content, ContentTabLabel> contentToTabs = new HashMap<>();

  List<AnAction> doubleClickActions = new ArrayList<>();

  TabContentLayout(@NotNull ToolWindowContentUi ui) {
    super(ui);

    BaseButtonBehavior behavior = new BaseButtonBehavior(ui.getTabComponent(), (Void)null) {
      @Override
      protected void execute(MouseEvent e) {
        if (!TabContentLayout.this.ui.isCurrent(TabContentLayout.this)) {
          return;
        }
        Rectangle moreRect = getMoreRect();
        if (moreRect != null) {
          showMorePopup();
        }
      }
    };
    behavior.setupListeners();
  }

  @Override
  public void init(@NotNull ContentManager contentManager) {
    reset();

    idLabel = new BaseLabel(ui, ExperimentalUI.isNewUI()) {
      @Override
      protected boolean allowEngravement() {
        return myUi.window.isActive();
      }
    };
    dropOverPlaceholder = new JLabel() {
      @Override
      public void paint(Graphics g) {
        g.setColor(JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND);
        RectanglePainter.FILL.paint((Graphics2D)g, 0, 0, getWidth(), getHeight(), null);
      }

      @Override
      public Dimension getPreferredSize() {
        return new Dimension(TabContentLayout.this.ui.dropOverWidth, 1);
      }
    };
    MouseDragHelper.setComponentDraggable(idLabel, true);

    for (int i = 0; i < contentManager.getContentCount(); i++) {
      contentAdded(new ContentManagerEvent(this, contentManager.getContent(i), i));
    }
  }

  @Override
  public void reset() {
    tabs.clear();
    contentToTabs.clear();
    idLabel = null;
    dropOverPlaceholder = null;
  }

  void setTabDoubleClickActions(@NotNull List<AnAction> actions) {
    doubleClickActions = actions;
  }

  private Rectangle getMoreRect() {
    return lastLayout == null ? null : lastLayout.moreRect;
  }

  public void dropCaches() {
    lastLayout = null;
  }

  @Override
  public boolean canShowMorePopup() {
    return getMoreRect() != null;
  }

  @Override
  public @Nullable JBPopup showMorePopup() {
    Rectangle rect = getMoreRect();
    if (rect == null) {
      return null;
    }
    List<? extends ContentTabLabel> tabs = ContainerUtil.filter(this.tabs, lastLayout.toDrop::contains);
    final List<Content> contentsToShow = ContainerUtil.map(tabs, ContentTabLabel::getContent);
    final SelectContentStep step = new SelectContentStep(contentsToShow);
    RelativePoint point = new RelativePoint(ui.getTabComponent(), new Point(rect.x, rect.y + rect.height));
    ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    popup.show(point);
    return popup;
  }

  @Override
  public void layout() {
    Rectangle bounds = ui.getTabComponent().getBounds();
    ContentManager manager = ui.getContentManager();
    LayoutData data = new LayoutData(ui);

    data.eachX = getTabLayoutStart();
    data.eachY = 0;

    if (isIdVisible()) {
      idLabel.setBounds(data.eachX, data.eachY, idLabel.getPreferredSize().width, bounds.height);
      data.eachX += idLabel.getPreferredSize().width;
    }
    else {
      idLabel.setBounds(data.eachX, data.eachY, 0, 0);
    }
    int tabsStart = data.eachX;

    if (manager.getContentCount() == 0) return;

    Content selected = manager.getSelectedContent();
    if (selected == null) {
      selected = manager.getContents()[0];
    }

    if (lastLayout != null &&
        (idLabel == null || idLabel.isValid()) &&
        lastLayout.layoutSize.equals(bounds.getSize()) &&
        lastLayout.contentCount == manager.getContentCount() &&
        ContainerUtil.all(tabs, Component::isValid)) {
      for (ContentTabLabel each : tabs) {
        if (each.getContent() == selected && each.getBounds().width != 0) {
          data = lastLayout;
          data.fullLayout = false;
        }
      }
    }

    if (data.fullLayout) {
      for (JLabel eachTab : tabs) {
        final Dimension eachSize = eachTab.getPreferredSize();
        data.requiredWidth += eachSize.width;
        data.toLayout.add(eachTab);
      }

      if (ui.dropOverIndex != -1 && !isSingleContentView) {
        data.requiredWidth += ui.dropOverWidth;
        int index = Math.min(data.toLayout.size(), Math.max(0, ui.dropOverIndex - 1));
        data.toLayout.add(index, dropOverPlaceholder);
      }

      data.toFitWidth = bounds.getSize().width - data.eachX;

      final ContentTabLabel selectedTab = contentToTabs.get(selected);
      while (true) {
        if (data.requiredWidth <= data.toFitWidth) break;
        if (data.toLayout.size() <= 1) break;

        JLabel firstLabel = data.toLayout.get(0);
        JLabel lastLabel = data.toLayout.get(data.toLayout.size() - 1);
        if (firstLabel != selectedTab && firstLabel != dropOverPlaceholder) {
          dropTab(data, firstLabel);
        }
        else if (lastLabel != selectedTab && lastLabel != dropOverPlaceholder) {
          dropTab(data, lastLabel);
        }
        else {
          break;
        }
      }

      boolean reachedBounds = false;
      data.moreRect = null;
      TabsDrawMode toDrawTabs = isToDrawTabs();
      for (JLabel each : data.toLayout) {
        if (toDrawTabs == TabsDrawMode.HIDE) {
          each.setBounds(0, 0, 0, 0);
          continue;
        }
        data.eachY = 0;
        final Dimension eachSize = each.getPreferredSize();
        if (data.eachX + eachSize.width < data.toFitWidth + tabsStart) {
          each.setBounds(data.eachX, data.eachY, eachSize.width, bounds.height - data.eachY);
          data.eachX += eachSize.width;
        }
        else {
          if (!reachedBounds) {
            final int width = bounds.width - data.eachX;
            each.setBounds(data.eachX, data.eachY, width, bounds.height - data.eachY);
            data.eachX += width;
          }
          else {
            each.setBounds(0, 0, 0, 0);
          }
          reachedBounds = true;
        }
      }

      for (JLabel each : data.toDrop) {
        each.setBounds(0, 0, 0, 0);
      }
    }
    boolean toolbarUpdateNeeded;
    if (data.toDrop.size() > 0) {
      toolbarUpdateNeeded = lastLayout != null && lastLayout.moreRect == null;
      data.moreRect = new Rectangle(data.eachX + MORE_ICON_BORDER, 0, /*getMoreToolbarWidth()*/16, bounds.height);
    }
    else {
      toolbarUpdateNeeded = lastLayout != null && lastLayout.moreRect != null;
      data.moreRect = null;
    }

    Rectangle moreRect = data.moreRect == null ? null : new Rectangle(data.eachX, 0, /*getMoreToolbarWidth()*/16+MORE_ICON_BORDER, bounds.height);
    ui.isResizableArea = p -> moreRect == null || !moreRect.contains(p);
    lastLayout = data;
    if (toolbarUpdateNeeded) {
      ActivityTracker.getInstance().inc();
    }
  }

  @Override
  public int getMinimumWidth() {
    int result = 0;
    if (idLabel != null && isIdVisible()) {
      result += idLabel.getPreferredSize().width;
      Insets insets = idLabel.getInsets();
      if (insets != null) {
        result += insets.left + insets.right;
      }
    }

    ContentManager contentManager = ui.getContentManager();
    Content selected = contentManager.getSelectedContent();
    if (selected == null && contentManager.getContentCount() > 0) {
      selected = contentManager.getContents()[0];
    }

    if (!ExperimentalUI.isNewUI()) {
      if (selected != null) {
        ContentTabLabel label = contentToTabs.get(selected);
        if (label != null) {
          result += label.getMinimumSize().width;
        }
      }
    }
    return result;
  }

  @Nullable ContentTabLabel findTabLabelByContent(@Nullable Content content) {
    return contentToTabs.get(content);
  }

  static void dropTab(LayoutData data, JLabel toDropLabel) {
    data.requiredWidth -= (toDropLabel.getPreferredSize().width + 1);
    data.toDrop.add(toDropLabel);
    data.toLayout.remove(toDropLabel);
  }

  @NotNull
  TabContentLayout.TabsDrawMode isToDrawTabs() {
    int size = tabs.size();
    if (size > 1) {
      return TabsDrawMode.PAINT_ALL;
    }
    else if (size == 1) {
      ContentTabLabel tabLabel = tabs.get(0);
      Content content = tabLabel.getContent();
      if (!StringUtil.isEmptyOrSpaces(content.getToolwindowTitle())) {
        if (Boolean.TRUE.equals(content.getUserData(Content.SIMPLIFIED_TAB_RENDERING_KEY))) return TabsDrawMode.PAINT_SIMPLIFIED;
        return TabsDrawMode.PAINT_ALL;
      }
      if (tabLabel.hasActiveIcons()) return TabsDrawMode.PAINT_SIMPLIFIED;
      return TabsDrawMode.HIDE;
    }
    else {
      return TabsDrawMode.HIDE;
    }
  }

  static class LayoutData {
    int toFitWidth;
    int requiredWidth;
    Dimension layoutSize;
    boolean fullLayout = true;

    ArrayList<JLabel> toLayout = new ArrayList<>();
    Collection<JLabel> toDrop = new HashSet<>();

    Rectangle moreRect;

    public int eachX;
    public int eachY;
    public int contentCount;

    LayoutData(ToolWindowContentUi ui) {
      layoutSize = ui.getTabComponent().getSize();
      contentCount = ui.getContentManager().getContentCount();
    }
  }

  protected final JBTabPainter tabPainter = JBTabPainter.getTOOL_WINDOW();

  @Override
  public void paintComponent(Graphics g) {
    TabsDrawMode toDrawTabs = isToDrawTabs();
    if (toDrawTabs == TabsDrawMode.HIDE) return;

    Graphics2D g2d = (Graphics2D)g.create();
    for (ContentTabLabel each : tabs) {
      //TODO set borderThickness
      int borderThickness = JBUIScale.scale(1);
      Rectangle r = each.getBounds();

      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      if (toDrawTabs == TabsDrawMode.PAINT_ALL) {
        if (each.isSelected()) {
          tabPainter.paintSelectedTab(JBTabsPosition.top, g2d, r, borderThickness, each.getTabColor(),
                                      ui.window.isActive(), each.isHovered());
        }
        else {
          tabPainter.paintTab(JBTabsPosition.top, g2d, r, borderThickness, each.getTabColor(),
                              ui.window.isActive(), each.isHovered());
        }
      }
    }
    g2d.dispose();
  }

  @Override
  public void update() {
    for (ContentTabLabel each : tabs) {
      each.update();
    }

    updateIdLabel(idLabel);
  }

  @Override
  public void rebuild() {
    ui.getTabComponent().removeAll();

    ui.getTabComponent().add(idLabel);
    ToolWindowContentUi.initMouseListeners(idLabel, ui, true);

    for (ContentTabLabel each : tabs) {
      ui.getTabComponent().add(each);
      ToolWindowContentUi.initMouseListeners(each, ui, false);
    }
    if ((!isSingleContentView || !Registry.is("debugger.new.tool.window.layout.dnd", false))
        && ui.dropOverIndex >= 0 && !tabs.isEmpty()) {
      int index = Math.min(ui.dropOverIndex, ui.getTabComponent().getComponentCount());
      ui.getTabComponent().add(dropOverPlaceholder, index);
    }
  }

  @Override
  public void contentAdded(@NotNull ContentManagerEvent event) {
    Content content = event.getContent();
    ContentTabLabel tab;
    if (content instanceof TabbedContent) {
      tab = new TabbedContentTabLabel((TabbedContent)content, this);
    }
    else {
      tab = new ContentTabLabel(content, this);
    }
    tabs.add(Math.min(event.getIndex(), tabs.size()), tab);
    contentToTabs.put(content, tab);

    DnDTarget target = getDnDTarget(content);
    if (target != null) {
      DnDSupport.createBuilder(tab)
        .setDropHandler(target)
        .setTargetChecker(target)
        .setCleanUpOnLeaveCallback(() -> target.cleanUpOnLeave())
        .install();
    }
  }

  @Nullable
  private static DnDTarget getDnDTarget(Content content) {
    DnDTarget target = content.getUserData(Content.TAB_DND_TARGET_KEY);
    if (target != null) return target;
    return ObjectUtils.tryCast(content, DnDTarget.class);
  }

  @Override
  public void contentRemoved(@NotNull ContentManagerEvent event) {
    final ContentTabLabel tab = contentToTabs.get(event.getContent());
    if (tab != null) {
      tabs.remove(tab);
      contentToTabs.remove(event.getContent());
    }
  }

  @Override
  public void showContentPopup(ListPopup listPopup) {
    Content selected = ui.getContentManager().getSelectedContent();
    if (selected != null) {
      ContentTabLabel tab = contentToTabs.get(selected);
      listPopup.showUnderneathOf(tab);
    }
    else {
      listPopup.showUnderneathOf(idLabel);
    }
  }

  @Override
  public @NlsActions.ActionText String getCloseActionName() {
    return UIBundle.message("tabbed.pane.close.tab.action.name");
  }

  @Override
  public @NlsActions.ActionText String getCloseAllButThisActionName() {
    return UIBundle.message("tabbed.pane.close.all.tabs.but.this.action.name");
  }

  @Override
  public @NlsActions.ActionText String getPreviousContentActionName() {
    return UIBundle.message("tabbed.pane.select.previous.tab");
  }

  @Override
  public @NlsActions.ActionText String getNextContentActionName() {
    return UIBundle.message("tabbed.pane.select.next.tab");
  }

  enum TabsDrawMode {
    PAINT_ALL,
    PAINT_SIMPLIFIED,
    HIDE
  }

  public static int getTabLayoutStart(){
    return ExperimentalUI.isNewUI() ? 0 : TAB_LAYOUT_START;
  }
}
