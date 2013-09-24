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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author Konstantin Bulenkov
 */
public class GotItMessage {
  @NotNull private final String myTitle;
  @NotNull private final String myMessage;
  private Runnable myCallback;
  private Disposable myDisposable;
  private boolean myShowCallout = true;

  private GotItMessage(@NotNull String title,
                       @NotNull String message                       ) {
    myTitle = title;
    final String[] lines = message.split("\n");

    StringBuffer buf = new StringBuffer("<html><body><div align='center' style=\"font-family: ")
      .append(UIUtil.getLabelFont().getFontName()).append("; ")
      .append("font-size: 12pt;\">")
      .append(lines.length > 1 ? message.replace("\n", "<br>") : message)
      .append("</div></body></html>");
    myMessage = buf.toString();
  }

  public static GotItMessage createMessage(@NotNull String title,
                                           @NotNull String message) {
    return new GotItMessage(title, message);
  }

  public GotItMessage setDisposable(Disposable disposable) {
    myDisposable = disposable;
    return this;
  }

  public GotItMessage setCallback(Runnable callback) {
    myCallback = callback;
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
