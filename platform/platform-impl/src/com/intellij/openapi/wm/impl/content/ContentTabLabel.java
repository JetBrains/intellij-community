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

import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.BaseButtonBehavior;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;

class ContentTabLabel extends BaseLabel {

  Content myContent;
  private final BaseButtonBehavior myBehavior;
  private final TabContentLayout myLayout;

  public ContentTabLabel(final Content content, TabContentLayout layout) {
    super(layout.myUi, true);
    myLayout = layout;
    myContent = content;
    update();

    myBehavior = new BaseButtonBehavior(this) {
      protected void execute(final MouseEvent e) {
        final ContentManager mgr = myUi.myWindow.getContentManager();
        if (mgr.getIndexOfContent(myContent) >= 0) {
          mgr.setSelectedContent(myContent, true);
        }
      }
    };

  }

  public void update() {
    if (!myLayout.isToDrawTabs()) {
      setHorizontalAlignment(JLabel.LEFT);
      setBorder(null);
    } else {
      setHorizontalAlignment(JLabel.CENTER);
      setBorder(new EmptyBorder(0, 8, 0, 8));
    }

    updateTextAndIcon(myContent, isSelected());
  }


  protected void paintComponent(final Graphics g) {
    if (!isSelected() && myLayout.isToDrawTabs()) {
      g.translate(0, TAB_SHIFT);
    }

    super.paintComponent(g);

    if (!isSelected() && myLayout.isToDrawTabs()) {
      g.translate(0, -TAB_SHIFT);
    }
  }

  public boolean isSelected() {
    return myUi.myWindow.getContentManager().isSelected(myContent);
  }

  @Override
  public Content getContent() {
    return myContent;
  }
}
