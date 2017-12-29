/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.dnd.DnDSupport;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.UIBundle;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

class TabContentLayout extends ContentLayout {

  static final int MORE_ICON_BORDER = 6;
  LayoutData myLastLayout;

  ArrayList<ContentTabLabel> myTabs = new ArrayList<>();
  final Map<Content, ContentTabLabel> myContent2Tabs = new HashMap<>();

  private Map<String, BufferedImage> myCached = new com.intellij.util.containers.HashMap<>();

  private final MoreIcon myMoreIcon = new MoreIcon() {
    protected Rectangle getIconRec() {
      return myLastLayout.moreRect;
    }

    protected boolean isActive() {
      return myUi.myWindow.isActive();
    }

    protected int getIconY(final Rectangle iconRec) {
      return iconRec.height / TAB_ARC - getIconHeight() / TAB_ARC;
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

    data.eachX = 2;
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
      final int selectedIndex = manager.getIndexOfContent(manager.getSelectedContent());
      if (selectedIndex == 0) {
        myMoreIcon.setPaintedIcons(false, true);
      }
      else if (selectedIndex == manager.getContentCount() - 1) {
        myMoreIcon.setPaintedIcons(true, false);
      }
      else {
        myMoreIcon.setPaintedIcons(true, true);
      }
    }
    else {
      data.moreRect = null;
    }

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
    return myTabs.size() > 1;
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

    boolean prevSelected = false;
    for (int i = 0; i < myTabs.size(); i++) {
      boolean last = (i == myTabs.size() - 1) || ((i + 1 < myTabs.size() && myTabs.get(i + 1).getBounds().width == 0));
      ContentTabLabel each = myTabs.get(i);
      Rectangle r = each.getBounds();

      StringBuilder key = new StringBuilder().append(i);
      if (each.isSelected()) key.append('s');
      if (prevSelected) key.append('p');
      if (last) key.append('l');
      if (myUi.myWindow.isActive()) key.append('a');

      BufferedImage image = myCached.get(key.toString());
      if (image == null || image.getWidth() != r.width || image.getHeight() != r.height) {
        image = drawToBuffer(r, each.isSelected(), last, prevSelected, myUi.myWindow.isActive());
        myCached.put(key.toString(), image);
      }
      
      if (image != null) {
        UIUtil.drawImage(g, image, isIdVisible() ? r.x : r.x - 2, r.y, null);
      }
      
      prevSelected = each.isSelected();
    }
  }
  
  @Nullable
  private static BufferedImage drawToBuffer(Rectangle r, boolean selected, boolean last, boolean prevSelected, boolean active) {
    if (r.width <= 0 || r.height <= 0) return null;
    BufferedImage image = UIUtil.createImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = image.createGraphics();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    if (selected) {
      if (!UIUtil.isUnderDarcula()) {
      g2d.setColor(active ? new Color(0, 0, 0, 70) : new Color(0, 0, 0, 90));
      g2d.fillRect(0, 0, r.width, r.height);

      g2d.setColor(new Color(0, 0, 0, 140));
      g2d.drawLine(0, 0, r.width - 1, 0);
      g2d.drawLine(0, 1, 0, r.height - 1);

      g2d.setColor(new Color(0, 0, 0, 20));
      g2d.drawLine(1, 1, r.width - 1, 1);
      g2d.drawLine(1, 2, 1, r.height - 2);
      g2d.drawLine(1, r.height - 1, r.width - 1, r.height - 1);

      g2d.setColor(new Color(0, 0, 0, 60));
      g2d.drawLine(r.width - 1, 1, r.width - 1, r.height - 2);
      }

      if (active) {
        g2d.setColor(new Color(100, 150, 230, 50));
        g2d.fill(new Rectangle(0, 0, r.width, r.height));
      }
    }
    else {
      g2d.setPaint(UIUtil.getGradientPaint(0, 0, new Color(0, 0, 0, 10), 0, r.height, new Color(0, 0, 0, 30)));
      g2d.fillRect(0, 0, r.width, r.height);

      final Color c = new Color(255, 255, 255, UIUtil.isUnderDarcula() ? 10 : 80);
      if (last) {
        if (prevSelected) {
          g2d.setColor(c);
          g2d.drawRect(0, 0, r.width - 2, r.height - 1);
        } else {
          g2d.setColor(c);
          g2d.drawRect(1, 0, r.width - 3, r.height - 1);

          g2d.setColor(new Color(0, 0, 0, 60));
          g2d.drawLine(0, 0, 0, r.height);
        }

        g2d.setColor(new Color(0, 0, 0, 60));
        g2d.drawLine(r.width - 1, 0, r.width - 1, r.height);
      } else {
        if (prevSelected) {
          g2d.setColor(c);
          g2d.drawRect(0, 0, r.width - 1, r.height - 1);
        }
        else {
          g2d.setColor(c);
          g2d.drawRect(1, 0, r.width - 2, r.height - 1);

          g2d.setColor(new Color(0, 0, 0, 60));
          g2d.drawLine(0, 0, 0, r.height);
        }
      }
    }

    g2d.dispose();
    return image;
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
    
    myCached.clear();
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
    
    myCached.clear();
  }

  @Override
  public void contentRemoved(ContentManagerEvent event) {
    final ContentTabLabel tab = myContent2Tabs.get(event.getContent());
    if (tab != null) {
      myTabs.remove(tab);
      myContent2Tabs.remove(event.getContent());
    }
    
    myCached.clear();
  }

  @Override
  public boolean shouldDrawDecorations() {
    return isToDrawTabs();
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
