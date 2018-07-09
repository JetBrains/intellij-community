// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.dnd.DnDSupport;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.UIBundle;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.TabbedContent;
import com.intellij.ui.tabs.impl.singleRow.MoreTabsIcon;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

class TabContentLayout extends ContentLayout {

  static final int MORE_ICON_BORDER = 6;
  LayoutData myLastLayout;

  ArrayList<ContentTabLabel> myTabs = new ArrayList<>();
  final Map<Content, ContentTabLabel> myContent2Tabs = new HashMap<>();

  private final MoreTabsIcon myMoreIcon = new MoreTabsIcon() {
    @Nullable
    protected Rectangle getIconRec() {
      return myLastLayout.moreRect;
    }
  };

  TabContentLayout(ToolWindowContentUi ui) {
    super(ui);

    new BaseButtonBehavior(myUi) {
      protected void execute(final MouseEvent e) {
        if (!myUi.isCurrent(TabContentLayout.this)) return;

        if (myLastLayout != null) {
          final Rectangle moreRect = myLastLayout.moreRect;
          if (moreRect != null && moreRect.contains(e.getPoint())) {
            showPopup(e, ContainerUtil.filter(myTabs, myLastLayout.toDrop::contains));
          }
        }
      }
    };
  }

  @Override
  public void init() {
    reset();

    myIdLabel = new BaseLabel(myUi, false) {
      @Override
      protected boolean allowEngravement() {
        return myUi.myWindow.isActive();
      }
    };
    for (int i = 0; i < myUi.myManager.getContentCount(); i++) {
      contentAdded(new ContentManagerEvent(this, myUi.myManager.getContent(i), i));
    }
  }

  @Override
  public void reset() {
    myTabs.clear();
    myContent2Tabs.clear();
    myIdLabel = null;
  }

  private static void showPopup(MouseEvent e, List<ContentTabLabel> tabs) {
    final List<Content> contentsToShow = ContainerUtil.map(tabs, ContentTabLabel::getContent);
    final SelectContentStep step = new SelectContentStep(contentsToShow);
    JBPopupFactory.getInstance().createListPopup(step).show(new RelativePoint(e));
  }

  @Override
  public void layout() {
    Rectangle bounds = myUi.getBounds();
    ContentManager manager = myUi.myManager;
    LayoutData data = new LayoutData(myUi);

    data.eachX = 4;
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
        myLastLayout.contentCount == manager.getContentCount()) {
      for (ContentTabLabel each : myTabs) {
        if (!each.isValid()) break;
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
        data.requiredWidth++;
        data.toLayout.add(eachTab);
      }


      data.moreRectWidth = myMoreIcon.getIconWidth() + MORE_ICON_BORDER * TAB_ARC;
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
      for (ContentTabLabel each : data.toLayout) {
        data.eachY = 0;
        final Dimension eachSize = each.getPreferredSize();
        if (data.eachX + eachSize.width < data.toFitWidth + tabsStart) {
          each.setBounds(data.eachX, data.eachY, eachSize.width, bounds.height - data.eachY);
          data.eachX += eachSize.width;
        }
        else {
          if (!reachedBounds) {
            final int width = bounds.width - data.eachX - data.moreRectWidth;
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

    if (data.toDrop.size() > 0) {
      data.moreRect = new Rectangle(data.eachX + MORE_ICON_BORDER, 0, myMoreIcon.getIconWidth(), bounds.height);
      myMoreIcon.updateCounter(data.toDrop.size());
    }
    else {
      data.moreRect = null;
    }

    final Rectangle moreRect = data.moreRect == null ? null : new Rectangle(data.eachX, 0, myMoreIcon.getIconWidth()+MORE_ICON_BORDER, bounds.height);

    myUi.isResizableArea = p -> moreRect == null || !moreRect.contains(p);
    myLastLayout = data;
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
    if (myLastLayout != null) {
      result += myLastLayout.moreRectWidth + myLastLayout.requiredWidth;
      result -= myLastLayout.toLayout.size() > 1 ? myLastLayout.moreRectWidth + 1 : -14;
    }
    return result;
  }

  static void dropTab(final LayoutData data, final ContentTabLabel toDropLabel) {
    data.requiredWidth -= (toDropLabel.getPreferredSize().width + 1);
    data.toDrop.add(toDropLabel);
    if (data.toDrop.size() == 1) {
      data.toFitWidth -= data.moreRectWidth;
    }
  }

  boolean isToDrawTabs() {
    if(myTabs.size() > 1) return true;

      if(myTabs.size() == 1)  {
        String title = myTabs.get(0).getContent().getToolwindowTitle();
        return !StringUtil.isEmpty(title);
      }

      return false;
  }

  static class LayoutData {
    int toFitWidth;
    int requiredWidth;
    Dimension layoutSize;
    boolean fullLayout = true;

    int moreRectWidth;

    ArrayList<ContentTabLabel> toLayout = new ArrayList<>();
    Collection<ContentTabLabel> toDrop = new HashSet<>();

    Rectangle moreRect;

    public int eachX;
    public int eachY;
    public int contentCount;

    LayoutData(ToolWindowContentUi ui) {
      layoutSize = ui.getSize();
      contentCount = ui.myManager.getContentCount();
    }
  }

  @Override
  public void paintComponent(Graphics g) {
    if (!isToDrawTabs()) return;

    for (ContentTabLabel each : myTabs) {
      if (each.isSelected() || each.isHovered()) {
        Color color = each.isSelected() ?
                      JBUI.CurrentTheme.ToolWindow.tabSelectedBackground(myUi.myWindow.isActive()) :
                      JBUI.CurrentTheme.ToolWindow.tabHoveredBackground(myUi.myWindow.isActive());

        Rectangle r = each.getBounds();
        Graphics2D g2d = (Graphics2D)g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(color);

        g2d.fillRect(isIdVisible() ? r.x : r.x - 2, r.y, r.width, r.height);
        g2d.dispose();
      }
    }
  }

  @Override
  public void paintChildren(Graphics g) {
    if (!isToDrawTabs()) return;

    if (myLastLayout != null && myLastLayout.moreRect != null) {
      myMoreIcon.paintIcon(myUi, g);
    }
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
    myUi.removeAll();

    myUi.add(myIdLabel);
    ToolWindowContentUi.initMouseListeners(myIdLabel, myUi, true);

    for (ContentTabLabel each : myTabs) {
      myUi.add(each);
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
    if (content instanceof DnDTarget) {
      DnDTarget target = (DnDTarget)content;
      DnDSupport.createBuilder(tab)
                .setDropHandler(target)
                .setTargetChecker(target)
                .setCleanUpOnLeaveCallback(() -> target.cleanUpOnLeave())
                .install();
    }
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
    Content selected = myUi.myManager.getSelectedContent();
    if (selected != null) {
      ContentTabLabel tab = myContent2Tabs.get(selected);
      listPopup.showUnderneathOf(tab);
    } else {
      listPopup.showUnderneathOf(myIdLabel);
    }
  }

  @Override
  public RelativeRectangle getRectangleFor(Content content) {
    ContentTabLabel label = myContent2Tabs.get(content);
    return new RelativeRectangle(label.getParent(), label.getBounds());
  }

  public Component getComponentFor(Content content) {
    return myContent2Tabs.get(content);
  }

  @Override
  public String getCloseActionName() {
    return UIBundle.message("tabbed.pane.close.tab.action.name");
  }

  @Override
  public String getCloseAllButThisActionName() {
    return UIBundle.message("tabbed.pane.close.all.tabs.but.this.action.name");
  }
  @Override
  public String getPreviousContentActionName() {
    return "Select Previous Tab";
  }

  @Override
  public String getNextContentActionName() {
    return "Select Next Tab";
  }
}
