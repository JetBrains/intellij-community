/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.wm.impl.TitlePanel;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.tabs.impl.singleRow.MoreIcon;
import com.intellij.util.ui.BaseButtonBehavior;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class TabContentLayout extends ContentLayout {

  static final int MORE_ICON_BORDER = 6;
  LayoutData myLastLayout;

  JPopupMenu myPopup;
  final PopupMenuListener myPopupListener;

  ArrayList<ContentTabLabel> myTabs = new ArrayList<ContentTabLabel>();
  final Map<Content, ContentTabLabel> myContent2Tabs = new HashMap<Content, ContentTabLabel>();

  BaseLabel myIdLabel;

  private final MoreIcon myMoreIcon = new MoreIcon() {
    protected Rectangle getIconRec() {
      return myLastLayout.moreRect;
    }

    protected boolean isActive() {
      return myUi.myWindow.isActive();
    }

    protected int getIconY(final Rectangle iconRec) {
      return iconRec.height / TAB_ARC - getIconHeight() / TAB_ARC + TitlePanel.STRUT;
    }
  };

  TabContentLayout(ToolWindowContentUi ui) {
    super(ui);

    myPopupListener = new MyPopupListener();

    new BaseButtonBehavior(myUi) {
      protected void execute(final MouseEvent e) {
        if (!myUi.isCurrent(TabContentLayout.this)) return;

        if (myLastLayout != null) {
          final Rectangle moreRect = myLastLayout.moreRect;
          if (moreRect != null && moreRect.contains(e.getPoint())) {
            showPopup();
          }
        }
      }
    };
  }

  @Override
  public void init() {
    reset();

    myIdLabel = new BaseLabel(myUi, false);
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

  private void showPopup() {
    myPopup = new JPopupMenu();
    myPopup.addPopupMenuListener(myPopupListener);

    ArrayList<ContentTabLabel> tabs = myTabs;

    for (final ContentTabLabel each : tabs) {
      final JCheckBoxMenuItem item = new JCheckBoxMenuItem(each.getText());
      if (myUi.myManager.isSelected(each.myContent)) {
        item.setSelected(true);
      }
      item.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          myUi.myManager.setSelectedContent(each.myContent, true);
        }
      });
      myPopup.add(item);
    }
    myPopup.show(myUi, myLastLayout.moreRect.x, myLastLayout.moreRect.y);
  }


  private class MyPopupListener implements PopupMenuListener {
    public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
    }

    public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
      if (myPopup != null) {
        myPopup.removePopupMenuListener(this);
      }
      myPopup = null;
    }

    public void popupMenuCanceled(final PopupMenuEvent e) {
    }
  }

  @Override
  public void layout() {
    Rectangle bounds = myUi.getBounds();
    ContentManager manager = myUi.myManager;
    LayoutData data = new LayoutData(myUi);

    data.eachX = 0;
    data.eachY = TitlePanel.STRUT;

    myIdLabel.setBounds(data.eachX, data.eachY, myIdLabel.getPreferredSize().width, bounds.height);
    data.eachX += myIdLabel.getPreferredSize().width;
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
        if (each.myContent == selected && each.getBounds().width != 0) {
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
        if (isToDrawTabs()) {
          data.eachY = 0;
        }
        else {
          data.eachY = TitlePanel.STRUT;
        }
        final Dimension eachSize = each.getPreferredSize();
        if (data.eachX + eachSize.width < data.toFitWidth + tabsStart) {
          each.setBounds(data.eachX, data.eachY, eachSize.width, bounds.height - data.eachY);
          data.eachX += eachSize.width;
          data.eachX++;
        }
        else {
          if (!reachedBounds) {
            final int width = bounds.width - data.eachX - data.moreRectWidth;
            each.setBounds(data.eachX, data.eachY, width, bounds.height - data.eachY);
            data.eachX += width;
            data.eachX++;
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


  void dropTab(final LayoutData data, final ContentTabLabel toDropLabel) {
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

    ArrayList<ContentTabLabel> toLayout = new ArrayList<ContentTabLabel>();
    ArrayList<ContentTabLabel> toDrop = new ArrayList<ContentTabLabel>();

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

    final Graphics2D g2d = (Graphics2D)g;

    final GraphicsConfig c = new GraphicsConfig(g);
    c.setAntialiasing(true);

    for (ContentTabLabel each : myTabs) {
      fillTabShape(g2d, each, getShapeFor(each), each.isSelected());
    }

    c.restore();
  }


  private Shape getShapeFor(ContentTabLabel label) {
    final Rectangle bounds = label.getBounds();

    if (bounds.width <= 0 || bounds.height <= 0) return new GeneralPath();

    if (!label.isSelected()) {
      bounds.y += TAB_SHIFT;
    }

    bounds.width += 1;

    int arc = TAB_ARC;

    final GeneralPath path = new GeneralPath();
    path.moveTo(bounds.x, bounds.y + bounds.height);
    path.lineTo(bounds.x, bounds.y + arc);
    path.quadTo(bounds.x, bounds.y, bounds.x + arc, bounds.y);
    path.lineTo(bounds.x + bounds.width - arc, bounds.y);
    path.quadTo(bounds.x + bounds.width, bounds.y, bounds.x + bounds.width, bounds.y + arc);
    path.lineTo(bounds.x + bounds.width, bounds.y + bounds.height);
    path.closePath();

    return path;
  }

  @Override
  public void paintChildren(Graphics g) {
    if (!isToDrawTabs()) return;

    final GraphicsConfig c = new GraphicsConfig(g);
    c.setAntialiasing(true);

    final Graphics2D g2d = (Graphics2D)g;

    final Color edges = myUi.myWindow.isActive() ? TAB_BORDER_ACTIVE_WINDOW : TAB_BORDER_PASSIVE_WINDOW;
    g2d.setColor(edges);
    for (int i = 0; i < myTabs.size(); i++) {
      ContentTabLabel each = myTabs.get(i);
      final Shape shape = getShapeFor(each);
      g2d.draw(shape);
    }

    c.restore();

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
    myUi.initMouseListeners(myIdLabel, myUi);

    for (ContentTabLabel each : myTabs) {
      myUi.add(each);
      myUi.initMouseListeners(each, myUi);
    }
  }

  @Override
  public void contentAdded(ContentManagerEvent event) {
    final ContentTabLabel tab = new ContentTabLabel(event.getContent(), this);
    myTabs.add(event.getIndex(), tab);
    myContent2Tabs.put(event.getContent(), tab);
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
}
