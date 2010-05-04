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
package com.intellij.ui;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

/**
 * @author Eugene Belyaev
 */
public class HoverHyperlinkLabel extends JLabel {
  private String myOriginalText;
  private final ArrayList<HyperlinkListener> myListeners = new ArrayList<HyperlinkListener>();

  public HoverHyperlinkLabel(String text) {
    this(text, Color.BLUE);
  }

  public HoverHyperlinkLabel(String text, Color color) {
    super(text);
    myOriginalText = text;
    setForeground(color);
    setupListener();
  }

  private void setupListener() {
    addMouseListener(new MouseHandler());
  }

  public void setText(String text) {
    if (BasicHTML.isHTMLString(getText())) { // if is currently showing string as html
      super.setText(underlineTextInHtml(text));
    }
    else {
      super.setText(text);
    }
    myOriginalText = text;
  }

  @NonNls private static String underlineTextInHtml(final String text) {
    return "<html><u>" + StringUtil.escapeXml(text) + "</u></html>";
  }

  public String getOriginalText() {
    return myOriginalText;
  }

  private class MouseHandler extends MouseAdapter {
    public void mouseClicked(MouseEvent e) {
      HyperlinkListener[] listeners = myListeners.toArray(new HyperlinkListener[myListeners.size()]);
      HyperlinkEvent event = new HyperlinkEvent(HoverHyperlinkLabel.this, HyperlinkEvent.EventType.ACTIVATED, null);
      for (int i = 0; i < listeners.length; i++) {
        HyperlinkListener listener = listeners[i];
        listener.hyperlinkUpdate(event);
      }
    }

    public void mouseEntered(MouseEvent e) {
      HoverHyperlinkLabel.super.setText(underlineTextInHtml(myOriginalText));
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public void mouseExited(MouseEvent e) {
      HoverHyperlinkLabel.super.setText(myOriginalText);
      setCursor(Cursor.getDefaultCursor());
    }
  }

  public void addHyperlinkListener(HyperlinkListener listener) {
    myListeners.add(listener);
  }

  public void removeHyperlinkListener(HyperlinkListener listener) {
    myListeners.remove(listener);
  }
}
