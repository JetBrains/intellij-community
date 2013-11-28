/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
public class GotItPanel {
  JPanel myButton;
  JPanel myRoot;
  JLabel myTitle;
  JEditorPane myMessage;


  private void createUIComponents() {
    myButton = new JPanel(new BorderLayout()) {
      {
        enableEvents(AWTEvent.MOUSE_EVENT_MASK);
      }
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        GraphicsUtil.setupAAPainting(g);
        ((Graphics2D)g).setPaint(new GradientPaint(0, 0, new JBColor(new Color(77, 143, 253), new Color(52, 74, 100)), 0, getHeight(),
                                                   new JBColor(new Color(71, 135, 237), new Color(38, 53, 73))));
        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 5, 5);
        ((Graphics2D)g).setStroke(new BasicStroke(UIUtil.isUnderDarcula() ? 2f : 1f));
        g.setColor(new JBColor(new Color(71, 91, 167), new Color(78, 120, 161)));
        g.drawRoundRect(0,0,getWidth()-1, getHeight()-1, 5,5);
      }
    };
    myMessage = new JEditorPane("text/html", "<html></html>");
    myMessage.setEditorKit(UIUtil.getHTMLEditorKit());
    myMessage.setEditable(false);
    myMessage.addHyperlinkListener(new BrowserHyperlinkListener());
    myMessage.setFont(UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().getSize() + 2f));
  }
}
