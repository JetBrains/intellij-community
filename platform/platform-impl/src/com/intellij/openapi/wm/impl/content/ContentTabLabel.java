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

import com.intellij.ui.EngravedTextGraphics;
import com.intellij.ui.Gray;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

class ContentTabLabel extends BaseLabel {

  private final Content myContent;
  private final TabContentLayout myLayout;

  public ContentTabLabel(@NotNull Content content, @NotNull TabContentLayout layout) {
    super(layout.myUi, true);
    myLayout = layout;
    myContent = content;
    BaseButtonBehavior behavior = new BaseButtonBehavior(this) {
      protected void execute(final MouseEvent e) {
        selectContent();
      }
    };
    behavior.setActionTrigger(MouseEvent.MOUSE_PRESSED);
    behavior.setMouseDeadzone(TimedDeadzone.NULL);
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
    } else {
      setHorizontalAlignment(SwingConstants.CENTER);
      setBorder(JBUI.Borders.empty(0, 8));
    }

    updateTextAndIcon(myContent, isSelected());
  }

  @Override
  protected boolean allowEngravement() {
    return isSelected() || (myUi != null && myUi.myWindow.isActive());
  }

  @Override
  protected Color getActiveFg(boolean selected) {
    if (contentManager().getContentCount() > 1) {
       return selected ? Color.white : UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : Color.black;
     }
     return super.getActiveFg(selected);
  }

  @Override
  protected Color getPassiveFg(boolean selected) {
    if (contentManager().getContentCount() > 1) {
       return selected && !UIUtil.isUnderDarcula() ? Gray._255 : UIUtil.isUnderDarcula()? UIUtil.getLabelDisabledForeground() : Gray._75;
     }
     return super.getPassiveFg(selected);
  }

  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
  }

  public boolean isSelected() {
    return contentManager().isSelected(myContent);
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
