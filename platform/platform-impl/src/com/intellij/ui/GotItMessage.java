/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author Konstantin Bulenkov
 */
public class GotItMessage {
  @NotNull private final String myTitle;
  @NotNull private final String myMessage;

  private Disposable myDisposable;
  private Runnable myCallback;
  private HyperlinkListener myHyperlinkListener = BrowserHyperlinkListener.INSTANCE;
  private boolean myShowCallout = true;

  private GotItMessage(@NotNull String title, @NotNull String message) {
    myTitle = title;
    myMessage =
      "<html><body><div align='center' style='font-family: " + UIUtil.getLabelFont().getFontName() + "; font-size: " + JBUI.scale(12) + "pt;'>" +
      StringUtil.replace(message, "\n", "<br>") +
      "</div></body></html>";
  }

  public static GotItMessage createMessage(@NotNull String title, @NotNull String message) {
    return new GotItMessage(title, message);
  }

  public GotItMessage setDisposable(Disposable disposable) {
    myDisposable = disposable;
    return this;
  }

  public GotItMessage setCallback(@Nullable Runnable callback) {
    myCallback = callback;
    return this;
  }

  public GotItMessage setHyperlinkListener(@Nullable HyperlinkListener hyperlinkListener) {
    myHyperlinkListener = hyperlinkListener;
    return this;
  }

  public GotItMessage setShowCallout(boolean showCallout) {
    myShowCallout = showCallout;
    return this;
  }

  public void show(RelativePoint point, Balloon.Position position) {
    final GotItPanel panel = new GotItPanel();
    panel.myTitle.setText(myTitle);

    panel.myMessage.setText(myMessage);
    if (myHyperlinkListener != null) {
      panel.myMessage.addHyperlinkListener(myHyperlinkListener);
    }

    panel.myButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    final BalloonBuilder builder = JBPopupFactory.getInstance().createBalloonBuilder(panel.myRoot);
    if (myDisposable != null) {
      builder.setDisposable(myDisposable);
    }

    final Balloon balloon = builder
      .setFillColor(UIUtil.getListBackground())
      .setHideOnClickOutside(false)
      .setHideOnAction(false)
      .setHideOnFrameResize(false)
      .setHideOnKeyOutside(false)
      .setShowCallout(myShowCallout)
      .setBlockClicksThroughBalloon(true)
      .createBalloon();

    panel.myButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        balloon.hide();
        if (myCallback != null) {
          myCallback.run();
        }
      }
    });

    balloon.show(point, position);
  }
}
