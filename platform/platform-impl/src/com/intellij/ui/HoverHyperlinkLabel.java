// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.PlatformColors;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public class HoverHyperlinkLabel extends JLabel {
  private String myOriginalText;
  private final List<HyperlinkListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public HoverHyperlinkLabel(String text) {
    this(text, PlatformColors.BLUE);
  }

  public HoverHyperlinkLabel(String text, Color color) {
    super(text);
    myOriginalText = text;
    setForeground(color);
    setupListener();
  }

  private void setupListener() {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        HoverHyperlinkLabel.super.setText(underlineTextInHtml(myOriginalText));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }

      @Override
      public void mouseExited(MouseEvent e) {
        HoverHyperlinkLabel.super.setText(myOriginalText);
        setCursor(Cursor.getDefaultCursor());
      }
    });

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        HyperlinkEvent event = new HyperlinkEvent(HoverHyperlinkLabel.this, HyperlinkEvent.EventType.ACTIVATED, null);
        for (HyperlinkListener listener : myListeners) {
          listener.hyperlinkUpdate(event);
        }
        return true;
      }
    }.installOn(this);
  }

  @Override
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

  public void addHyperlinkListener(HyperlinkListener listener) {
    myListeners.add(listener);
  }

  public void removeHyperlinkListener(HyperlinkListener listener) {
    myListeners.remove(listener);
  }
}
