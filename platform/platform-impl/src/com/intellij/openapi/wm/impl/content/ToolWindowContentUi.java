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

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindowContentUiType;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.content.*;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.ui.content.tabs.TabbedContentAction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ToolWindowContentUi extends JPanel implements ContentUI, PropertyChangeListener, DataProvider {
  public static final String POPUP_PLACE = "ToolwindowPopup";

  ContentManager myManager;


  final JPanel myContent = new JPanel(new BorderLayout());
  ToolWindowImpl myWindow;

  TabbedContentAction.CloseAllAction myCloseAllAction;
  TabbedContentAction.MyNextTabAction myNextTabAction;
  TabbedContentAction.MyPreviousTabAction myPreviousTabAction;



  ContentLayout myTabsLayout = new TabContentLayout(this);
  ContentLayout myComboLayout = new ComboContentLayout(this);

  private ToolWindowContentUiType myType = ToolWindowContentUiType.TABBED;

  public ToolWindowContentUi(ToolWindowImpl window) {
    myWindow = window;
    myContent.setOpaque(false);
    myContent.setFocusable(false);
    setOpaque(false);

    setBorder(new EmptyBorder(0, 0, 0, 2));

  }

  public void setType(ToolWindowContentUiType type) {
    if (myType != type) {
      myType = type;
      rebuild();
    }
  }

  private ContentLayout getCurrentLayout() {
    return myType == ToolWindowContentUiType.TABBED ? myTabsLayout : myComboLayout;
  }

  public JComponent getComponent() {
    return myContent;
  }

  public JComponent getTabComponent() {
    return this;
  }

  public void setManager(final ContentManager manager) {
    myManager = manager;
    myManager.addContentManagerListener(new ContentManagerListener() {
      public void contentAdded(final ContentManagerEvent event) {
        getCurrentLayout().contentAdded(event);
        event.getContent().addPropertyChangeListener(ToolWindowContentUi.this);
        rebuild();
      }

      public void contentRemoved(final ContentManagerEvent event) {
        event.getContent().removePropertyChangeListener(ToolWindowContentUi.this);
        getCurrentLayout().contentRemoved(event);
        ensureSelectedContentVisible();
        rebuild();
      }

      public void contentRemoveQuery(final ContentManagerEvent event) {
      }

      public void selectionChanged(final ContentManagerEvent event) {
        ensureSelectedContentVisible();

        update();

        myContent.revalidate();
        myContent.repaint();
      }
    });

    initMouseListeners(this, ToolWindowContentUi.this);
    update();


    myCloseAllAction = new TabbedContentAction.CloseAllAction(myManager);
    myNextTabAction = new TabbedContentAction.MyNextTabAction(myManager);
    myPreviousTabAction = new TabbedContentAction.MyPreviousTabAction(myManager);
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



  public void doLayout() {
    getCurrentLayout().layout();
  }


  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    getCurrentLayout().paintComponent(g);
  }

  protected void paintChildren(final Graphics g) {
    super.paintChildren(g);
    getCurrentLayout().paintChildren(g);
  }

  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    size.height = 0;
    for (int i = 0; i < getComponentCount(); i++) {
      final Component each = getComponent(i);
      size.height = Math.max(each.getPreferredSize().height, size.height);
    }
    return size;
  }

  public void propertyChange(final PropertyChangeEvent evt) {
    update();
  }

  private void update() {
    getCurrentLayout().update();
  }

  public boolean isSingleSelection() {
    return true;
  }

  public boolean isToSelectAddedContent() {
    return false;
  }

  public boolean canBeEmptySelection() {
    return false;
  }

  public void beforeDispose() {
  }

  static void initMouseListeners(final JComponent c, final ToolWindowContentUi ui) {
    if (c.getClientProperty(ui) != null) return;


    final Point[] myLastPoint = new Point[1];

    c.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseDragged(final MouseEvent e) {
        if (myLastPoint[0] == null) return;

        final Window window = SwingUtilities.windowForComponent(c);

        if (window instanceof IdeFrame) return;

        final Rectangle oldBounds = window.getBounds();
        final Point newPoint = e.getPoint();
        SwingUtilities.convertPointToScreen(newPoint, c);
        final Point offset = new Point(newPoint.x - myLastPoint[0].x, newPoint.y - myLastPoint[0].y);
        window.setLocation(oldBounds.x + offset.x, oldBounds.y + offset.y);
        myLastPoint[0] = newPoint;
      }
    });

    c.addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        myLastPoint[0] = e.getPoint();
        SwingUtilities.convertPointToScreen(myLastPoint[0], c);
        if (!e.isPopupTrigger()) {
          if (UIUtil.isCloseClick(e)) {
            ui.processHide(e);
          }
          else {
            ui.myWindow.fireActivated();
          }
        }
      }
    });


    final DefaultActionGroup contentGroup = new DefaultActionGroup();
    if (c instanceof ContentTabLabel) {
      final Content content = ((ContentTabLabel)c).myContent;
      contentGroup.add(new TabbedContentAction.CloseAction(content));
      contentGroup.add(ui.myCloseAllAction);
      contentGroup.add(new TabbedContentAction.CloseAllButThisAction(content));
      contentGroup.addSeparator();
      if (content.isPinnable()) {
        contentGroup.add(PinToolwindowTabAction.getPinAction());
        contentGroup.addSeparator();
      }

      contentGroup.add(ui.myNextTabAction);
      contentGroup.add(ui.myPreviousTabAction);
      contentGroup.addSeparator();
    }

    c.addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        DefaultActionGroup group = new DefaultActionGroup();
        group.addAll(contentGroup);

        final ActionGroup windowPopup = ui.myWindow.getPopupGroup();
        if (windowPopup != null) {
          group.addAll(windowPopup);
        }

        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(POPUP_PLACE, group);
        popupMenu.getComponent().show(comp, x, y);
      }
    });

    c.putClientProperty(ui, Boolean.TRUE);
  }

  private void processHide(final MouseEvent e) {
    IdeEventQueue.getInstance().blockNextEvents(e);
    final Component c = e.getComponent();
    if (c instanceof ContentTabLabel) {
      final ContentTabLabel tab = (ContentTabLabel)c;
      if (myManager.canCloseContents() && tab.myContent.isCloseable()) {
        myManager.removeContent(tab.myContent, true);
      } else {
        if (myManager.getContentCount() == 1) {
          hideWindow(e);
        }
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

  @Nullable
  public Object getData(@NonNls String dataId) {
    if (dataId.equals(PlatformDataKeys.TOOL_WINDOW.getName())) return myWindow;

    return null;
  }

  public void dispose() {
  }
}
