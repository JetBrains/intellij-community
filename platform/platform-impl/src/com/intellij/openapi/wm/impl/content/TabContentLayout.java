// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.dnd.DnDSupport;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.UIBundle;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.TabbedContent;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.tabs.JBTabPainter;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.impl.MorePopupAware;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.BaseButtonBehavior;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

final class TabContentLayout extends ContentLayout implements MorePopupAware {
  static final int MORE_ICON_BORDER = 6;
  public static final int TAB_LAYOUT_START = 4;
  LayoutData myLastLayout;

  ArrayList<ContentTabLabel> myTabs = new ArrayList<>();
  final Map<Content, ContentTabLabel> myContent2Tabs = new HashMap<>();

  List<AnAction> myDoubleClickActions = new ArrayList<>();

  TabContentLayout(@NotNull ToolWindowContentUi ui) {
    super(ui);

    new BaseButtonBehavior(myUi.getTabComponent()) {
      @Override
      protected void execute(final MouseEvent e) {
        if (!myUi.isCurrent(TabContentLayout.this)) return;
        Rectangle moreRect = getMoreRect();
        if (moreRect != null) {
          showMorePopup();
        }
      }
    };
  }

  @Override
  public void init(@NotNull ContentManager contentManager) {
    reset();

    myIdLabel = new BaseLabel(myUi, false) {
      @Override
      protected boolean allowEngravement() {
        return myUi.window.isActive();
      }
    };

    for (int i = 0; i < contentManager.getContentCount(); i++) {
      contentAdded(new ContentManagerEvent(this, contentManager.getContent(i), i));
    }
  }

  @Override
  public void reset() {
    myTabs.clear();
    myContent2Tabs.clear();
    myIdLabel = null;
  }

  void setTabDoubleClickActions(@NotNull List<AnAction> actions) {
    myDoubleClickActions = actions;
  }

  private Rectangle getMoreRect() {
    if (myLastLayout == null) return null;
    return myLastLayout.moreRect;
  }

  public void dropCaches() {
    myLastLayout = null;
  }

  @Override
  public boolean canShowMorePopup() {
    return getMoreRect() != null;
  }

  @Override
  public void showMorePopup() {
    Rectangle rect = getMoreRect();
    if (rect == null) return;
    List<? extends ContentTabLabel> tabs = ContainerUtil.filter(myTabs, myLastLayout.toDrop::contains);
    final List<Content> contentsToShow = ContainerUtil.map(tabs, ContentTabLabel::getContent);
    final SelectContentStep step = new SelectContentStep(contentsToShow);
    RelativePoint point = new RelativePoint(myUi.getTabComponent(), new Point(rect.x, rect.y + rect.height));
    JBPopupFactory.getInstance().createListPopup(step).show(point);
  }

  @Override
  public void layout() {
    Rectangle bounds = myUi.getTabComponent().getBounds();
    ContentManager manager = myUi.getContentManager();
    LayoutData data = new LayoutData(myUi);

    data.eachX = TAB_LAYOUT_START;
    data.eachY = 0;

    if (isIdVisible()) {
      myIdLabel.setBounds(data.eachX, data.eachY, myIdLabel.getPreferredSize().width, bounds.height);
      data.eachX += myIdLabel.getPreferredSize().width;
    }
    int tabsStart = data.eachX;

    if (manager.getContentCount() == 0) return;

    Content selected = manager.getSelectedContent();
    if (selected == null) {
      selected = manager.getContents()[0];
    }

    if (myLastLayout != null &&
        myLastLayout.layoutSize.equals(bounds.getSize()) &&
        myLastLayout.contentCount == manager.getContentCount() &&
        ContainerUtil.all(myTabs, Component::isValid)) {
      for (ContentTabLabel each : myTabs) {
        if (each.getContent() == selected && each.getBounds().width != 0) {
          data = myLastLayout;
          data.fullLayout = false;
        }
      }
    }

    if (data.fullLayout) {
      for (ContentTabLabel eachTab : myTabs) {
        final Dimension eachSize = eachTab.getPreferredSize();
        data.requiredWidth += eachSize.width;
        data.toLayout.add(eachTab);
      }


      data.toFitWidth = bounds.getSize().width - data.eachX;

      final ContentTabLabel selectedTab = myContent2Tabs.get(selected);
      while (true) {
        if (data.requiredWidth <= data.toFitWidth) break;
        if (data.toLayout.size() <= 1) break;

        if (data.toLayout.get(0) != selectedTab) {
          dropTab(data, data.toLayout.remove(0));
        }
        else if (data.toLayout.get(data.toLayout.size() - 1) != selectedTab) {
          dropTab(data, data.toLayout.remove(data.toLayout.size() - 1));
        }
        else {
          break;
        }
      }

      boolean reachedBounds = false;
      data.moreRect = null;
      boolean toDrawTabs = isToDrawTabs();
      for (ContentTabLabel each : data.toLayout) {
        if (!toDrawTabs) {
          each.setBounds(0,0, 0, 0);
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

      for (ContentTabLabel each : data.toDrop) {
        each.setBounds(0, 0, 0, 0);
      }
    }
    boolean toolbarUpdateNeeded;
    if (data.toDrop.size() > 0) {
      toolbarUpdateNeeded = myLastLayout != null && myLastLayout.moreRect == null;
      data.moreRect = new Rectangle(data.eachX + MORE_ICON_BORDER, 0, /*getMoreToolbarWidth()*/16, bounds.height);
    }
    else {
      toolbarUpdateNeeded = myLastLayout != null && myLastLayout.moreRect != null;
      data.moreRect = null;
    }

    Rectangle moreRect = data.moreRect == null ? null : new Rectangle(data.eachX, 0, /*getMoreToolbarWidth()*/16+MORE_ICON_BORDER, bounds.height);
    myUi.isResizableArea = p -> moreRect == null || !moreRect.contains(p);
    myLastLayout = data;
    if (toolbarUpdateNeeded) {
      ActionToolbarImpl.updateAllToolbarsImmediately();
    }
  }

  @Override
  public int getMinimumWidth() {
    int result = 0;
    if (myIdLabel != null) {
      result += myIdLabel.getPreferredSize().width;
      Insets insets = myIdLabel.getInsets();
      if (insets != null) {
        result += insets.left + insets.right;
      }
    }

    ContentManager contentManager = myUi.getContentManager();
    Content selected = contentManager.getSelectedContent();
    if (selected == null && contentManager.getContents().length > 0) {
      selected = contentManager.getContents()[0];
    }

    result += selected != null ? myContent2Tabs.get(selected).getMinimumSize().width : 0;

    return result;
  }

  static void dropTab(final LayoutData data, final ContentTabLabel toDropLabel) {
    data.requiredWidth -= (toDropLabel.getPreferredSize().width + 1);
    data.toDrop.add(toDropLabel);
  }

  boolean isToDrawTabs() {
    int size = myTabs.size();
    if (size > 1) {
      return true;
    }
    else if (size == 1) {
      return !StringUtil.isEmpty(myTabs.get(0).getContent().getToolwindowTitle());
    }
    else {
      return false;
    }
  }

  static class LayoutData {
    int toFitWidth;
    int requiredWidth;
    Dimension layoutSize;
    boolean fullLayout = true;

    ArrayList<ContentTabLabel> toLayout = new ArrayList<>();
    Collection<ContentTabLabel> toDrop = new HashSet<>();

    Rectangle moreRect;

    public int eachX;
    public int eachY;
    public int contentCount;

    LayoutData(ToolWindowContentUi ui) {
      layoutSize = ui.getTabComponent().getSize();
      contentCount = ui.getContentManager().getContentCount();
    }
  }

  private final JBTabPainter tabPainter = JBTabPainter.getTOOL_WINDOW();

  @Override
  public void paintComponent(Graphics g) {
    if (!isToDrawTabs()) return;

    Graphics2D g2d = (Graphics2D)g.create();
    for (ContentTabLabel each : myTabs) {
      //TODO set borderThickness
      int borderThickness = JBUIScale.scale(1);
      Rectangle r = each.getBounds();

      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      if (each.isSelected()) {
        tabPainter.paintSelectedTab(JBTabsPosition.top, g2d, r, borderThickness, null, myUi.window.isActive(), each.isHovered());
      }
      else {
        tabPainter.paintTab(JBTabsPosition.top, g2d, r, borderThickness, null, myUi.window.isActive(), each.isHovered());
      }
    }
    g2d.dispose();
  }

  @Override
  public void update() {
    for (ContentTabLabel each : myTabs) {
      each.update();
    }

    updateIdLabel(myIdLabel);
  }

  @Override
  public void rebuild() {
    myUi.getTabComponent().removeAll();

    myUi.getTabComponent().add(myIdLabel);
    ToolWindowContentUi.initMouseListeners(myIdLabel, myUi, true);

    for (ContentTabLabel each : myTabs) {
      myUi.getTabComponent().add(each);
      ToolWindowContentUi.initMouseListeners(each, myUi, false);
    }
  }

  @Override
  public void contentAdded(ContentManagerEvent event) {
    final Content content = event.getContent();
    final ContentTabLabel tab;
    if (content instanceof TabbedContent) {
      tab = new TabbedContentTabLabel((TabbedContent)content, this);
    } else {
      tab = new ContentTabLabel(content, this);
    }
    myTabs.add(event.getIndex(), tab);
    myContent2Tabs.put(content, tab);

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
  public void contentRemoved(ContentManagerEvent event) {
    final ContentTabLabel tab = myContent2Tabs.get(event.getContent());
    if (tab != null) {
      myTabs.remove(tab);
      myContent2Tabs.remove(event.getContent());
    }
  }

  @Override
  public void showContentPopup(ListPopup listPopup) {
    Content selected = myUi.getContentManager().getSelectedContent();
    if (selected != null) {
      ContentTabLabel tab = myContent2Tabs.get(selected);
      listPopup.showUnderneathOf(tab);
    } else {
      listPopup.showUnderneathOf(myIdLabel);
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
}
