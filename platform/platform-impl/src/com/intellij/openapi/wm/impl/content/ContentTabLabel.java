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

import com.intellij.openapi.ui.popup.ActiveIcon;
import com.intellij.ui.EngravedTextGraphics;
import com.intellij.ui.Gray;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TimedDeadzone;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

class ContentTabLabel extends BaseLabel {
  private final ActiveIcon closeIcon = new ActiveIcon(JBUI.CurrentTheme.ToolWindow.closeTabIcon(true),
                                                      JBUI.CurrentTheme.ToolWindow.closeTabIcon(false));

  private final Content myContent;
  private final TabContentLayout myLayout;

  private static final int CLOSE_GAP = 3;

  private final AdditionalIcon closeTabIcon = new AdditionalIcon(closeIcon) {

    @Override
    public Rectangle getIconRec() {
      return new Rectangle(getWidth() - getInsets().right - getIconWidth(), 0, getIconWidth(), getHeight());
    }

    @Override
    public boolean isActive() {
      return mouseOverCloseIcon();
    }
  };

  BaseButtonBehavior behavior = new BaseButtonBehavior(this) {
    protected void execute(final MouseEvent e) {
      if(canBeClosed() && mouseOverCloseIcon()) {
        contentManager().removeContent(getContent(), true);
      } else {
        selectContent();
      }
    }
  };

  private boolean mouseOverCloseIcon() {
    if(!isHovered()) return false;

    Point point = MouseInfo.getPointerInfo().getLocation();
    SwingUtilities.convertPointFromScreen(point, this);
    return closeTabIcon.getIconRec().contains(point);
  }

  public ContentTabLabel(@NotNull Content content, @NotNull TabContentLayout layout) {
    super(layout.myUi, true);
    myLayout = layout;
    myContent = content;

    behavior.setActionTrigger(MouseEvent.MOUSE_PRESSED);
    behavior.setMouseDeadzone(TimedDeadzone.NULL);

    myContent.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent event) {
        final String property = event.getPropertyName();
        if(Content.IS_CLOSABLE.equals(property)) {
          repaint();
        }
      }
    });
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent event) {
    super.processMouseEvent(event);
    if(isHovered() && canBeClosed()) {
      repaint(closeTabIcon.getIconRec());
    }
  }

  public final boolean canBeClosed() {
    return myContent.isCloseable() && contentManager().canCloseContents();
  }

  protected void selectContent() {
    final ContentManager mgr = contentManager();
    if (mgr.getIndexOfContent(myContent) >= 0) {
      mgr.setSelectedContent(myContent, true);
    }
  }

  public void update() {
    if (!myLayout.isToDrawTabs()) {
      setHorizontalAlignment(SwingConstants.LEFT);
      setBorder(null);
    }
    else {
      if(canBeClosed()) {
        setHorizontalAlignment(SwingConstants.LEFT);
        setBorder(JBUI.Borders.empty(0, 12, 0, 7));
      } else {
        setHorizontalAlignment(SwingConstants.CENTER);
        setBorder(JBUI.Borders.empty(0, 12));
      }
    }

    updateTextAndIcon(myContent, isSelected());
  }


  @Override
  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    int w = size.width;
    if (canBeClosed()) {
      w += CLOSE_GAP + closeTabIcon.getIconWidth();
    }
    return new Dimension(w, size.height);
  }

  @Override
  protected boolean allowEngravement() {
    return isSelected() || (myUi != null && myUi.myWindow.isActive());
  }

  @Override
  protected Color getActiveFg(boolean selected) {
    if (contentManager().getContentCount() > 1) {
      return JBUI.CurrentTheme.Label.foreground(selected);
    }

    return super.getActiveFg(selected);
  }

  @Override
  protected Color getPassiveFg(boolean selected) {
    if (contentManager().getContentCount() > 1) {
      return JBUI.CurrentTheme.Label.disabledForeground(selected);
    }

    return super.getPassiveFg(selected);
  }

  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    if (canBeClosed()) {
      closeTabIcon.paintIcon(this, g);
    }
  }

  public boolean isSelected() {
    return contentManager().isSelected(myContent);
  }

  public boolean isHovered() {
    return behavior.isHovered();
  }

  @Override
  protected Graphics _getGraphics(Graphics2D g) {
    if (isSelected() && contentManager().getContentCount() > 1) {
      return new EngravedTextGraphics(g, 1, 1, Gray._0.withAlpha(myUi.myWindow.isActive() ? 120 : 130));
    }

    return super._getGraphics(g);
  }

  private ContentManager contentManager() {
    return myUi.myWindow.getContentManager();
  }

  @NotNull
  @Override
  public Content getContent() {
    return myContent;
  }
}
