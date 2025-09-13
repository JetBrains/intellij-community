// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.ActivityTracker;
import com.intellij.ide.dnd.DnDSupport;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.impl.InternalUICustomization;
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
import java.util.*;
import java.util.List;

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
        if (canShowMorePopup()) {
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

  /**
   * Show popup after the last visible tab.
   * This is close to the "TabList" action location on {@link ActionPlaces#TOOLWINDOW_TITLE}.
   */
  private @Nullable Point getMorePopupOffset() {
    return lastLayout != null ? lastLayout.morePopupOffset : null;
  }

  public void dropCaches() {
    lastLayout = null;
  }

  @Override
  public boolean canShowMorePopup() {
    return getMorePopupOffset() != null;
  }

  @Override
  public @Nullable JBPopup showMorePopup() {
    Point offset = getMorePopupOffset();
    if (offset == null) {
      return null;
    }
    List<? extends ContentTabLabel> hiddenTabs = ContainerUtil.filter(this.tabs, tab -> tab.getWidth() == 0);
    final List<Content> contentsToShow = ContainerUtil.map(hiddenTabs, ContentTabLabel::getContent);
    final SelectContentStep step = new SelectContentStep(contentsToShow);
    RelativePoint point = new RelativePoint(ui.getTabComponent(), offset);
    ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    popup.show(point);
    return popup;
  }

  @Override
  public void layout() {
    Rectangle bounds = ui.getTabComponent().getBounds();
    ContentManager manager = ui.getContentManager();
    LayoutData data = new LayoutData(ui);

    data.toolbarWidth = getTabToolbarPreferredWidth();
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

    boolean toolbarUpdateNeeded = false;
    if (manager.getContentCount() != 0) {
      Content selected = manager.getSelectedContent();
      if (selected == null) {
        selected = manager.getContents()[0];
      }

      if (lastLayout != null &&
          (idLabel == null || idLabel.isValid()) &&
          lastLayout.layoutSize.equals(bounds.getSize()) &&
          lastLayout.contentCount == manager.getContentCount() &&
          lastLayout.toolbarWidth == data.toolbarWidth &&
          ContainerUtil.all(tabs, Component::isValid)) {
        for (ContentTabLabel each : tabs) {
          if (each.getContent() == selected && each.getBounds().width != 0) {
            return; // keep last layout
          }
        }
      }

      ArrayList<JLabel> toLayout = new ArrayList<>();
      Collection<JLabel> toDrop = new HashSet<>();

      for (JLabel eachTab : tabs) {
        final Dimension eachSize = eachTab.getPreferredSize();
        data.requiredWidth += eachSize.width;
        toLayout.add(eachTab);
      }

      if (ui.dropOverIndex != -1 && !isSingleContentView) {
        data.requiredWidth += ui.dropOverWidth;
        int index = Math.min(toLayout.size(), Math.max(0, ui.dropOverIndex - 1));
        toLayout.add(index, dropOverPlaceholder);
      }

      data.toFitWidth = bounds.getSize().width - data.toolbarWidth - data.eachX;

      final ContentTabLabel selectedTab = contentToTabs.get(selected);
      while (true) {
        if (data.requiredWidth <= data.toFitWidth) break;
        if (toLayout.size() <= 1) break;

        JLabel firstLabel = toLayout.get(0);
        JLabel lastLabel = toLayout.get(toLayout.size() - 1);
        JLabel labelToDrop;
        if (firstLabel != selectedTab && firstLabel != dropOverPlaceholder) {
          labelToDrop = firstLabel;
        }
        else if (lastLabel != selectedTab && lastLabel != dropOverPlaceholder) {
          labelToDrop = lastLabel;
        }
        else {
          break;
        }
        data.requiredWidth -= (labelToDrop.getPreferredSize().width + 1);
        toDrop.add(labelToDrop);
        toLayout.remove(labelToDrop);
      }

      boolean reachedBounds = false;
      TabsDrawMode toDrawTabs = isToDrawTabs();
      for (JLabel each : toLayout) {
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
            final int width = bounds.width - data.eachX - data.toolbarWidth;
            each.setBounds(data.eachX, data.eachY, width, bounds.height - data.eachY);
            data.eachX += width;
          }
          else {
            each.setBounds(0, 0, 0, 0);
          }
          reachedBounds = true;
        }
      }

      for (JLabel each : toDrop) {
        each.setBounds(0, 0, 0, 0);
      }

      if (toDrop.isEmpty()) {
        toolbarUpdateNeeded = lastLayout != null && lastLayout.morePopupOffset != null;
        data.morePopupOffset = null;
      }
      else {
        toolbarUpdateNeeded = lastLayout != null && lastLayout.morePopupOffset == null;
        data.morePopupOffset = new Point(data.eachX + data.toolbarWidth + MORE_ICON_BORDER, bounds.height);
      }
    }

    // Tab toolbar is positioned at the end.
    ActionToolbar tabToolbar = ui.getTabToolbar();
    if (tabToolbar != null) {
      JComponent component = tabToolbar.getComponent();
      Dimension size = component.getPreferredSize();
      component.setBounds(data.eachX, data.eachY + (bounds.height - size.height) / 2, size.width, size.height);
      data.eachX += component.getWidth();
    }

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

    result += getTabToolbarPreferredWidth();

    return result;
  }

  @Nullable ContentTabLabel findTabLabelByContent(@Nullable Content content) {
    return contentToTabs.get(content);
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

  static final class LayoutData {
    int toFitWidth;
    int requiredWidth;
    Dimension layoutSize;

    Point morePopupOffset;

    public int eachX;
    public int eachY;
    public int contentCount;
    public int toolbarWidth;

    LayoutData(ToolWindowContentUi ui) {
      layoutSize = ui.getTabComponent().getSize();
      contentCount = ui.getContentManager().getContentCount();
    }
  }

  protected final JBTabPainter tabPainter = createTabPainter();

  private static @NotNull JBTabPainter createTabPainter() {
    InternalUICustomization customization = InternalUICustomization.getInstance();
    return customization == null ? JBTabPainter.getTOOL_WINDOW() : customization.getToolWindowTabPainter();
  }

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
                                      ui.window.isActive() && ui.isActive(), each.isHovered());
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
    JPanel tabComponent = ui.getTabComponent();

    tabComponent.removeAll();

    tabComponent.add(idLabel);
    ToolWindowContentUi.initMouseListeners(idLabel, ui, true);

    for (ContentTabLabel each : tabs) {
      tabComponent.add(each);
      ToolWindowContentUi.initMouseListeners(each, ui, false);
    }
    if ((!isSingleContentView || !Registry.is("debugger.new.tool.window.layout.dnd", false))
        && ui.dropOverIndex >= 0 && !tabs.isEmpty()) {
      int index = Math.min(ui.dropOverIndex, tabComponent.getComponentCount());
      tabComponent.add(dropOverPlaceholder, index);
    }

    ui.connectTabToolbar();
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

  private static @Nullable DnDTarget getDnDTarget(Content content) {
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

  public static int getTabLayoutStart() {
    return ExperimentalUI.isNewUI() ? 0 : TAB_LAYOUT_START;
  }
}
