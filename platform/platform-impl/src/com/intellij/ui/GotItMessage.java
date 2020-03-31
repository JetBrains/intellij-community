// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.PositionTracker;
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
      "<html><body><div align='center' style='font-family: " + UIUtil.getLabelFont().getFontName() + "; font-size: " +
      JBUIScale.scale(12) + "pt;'>" +
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

  public void show(@NotNull RelativePoint point, @NotNull Balloon.Position position) {
    show(new PositionTracker.Static<>(point), position);
  }

  public void show(@NotNull PositionTracker<Balloon> tracker, @NotNull Balloon.Position position) {
    if (myDisposable != null && Disposer.isDisposed(myDisposable)) {
      return;
    }
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

    balloon.show(tracker, position);
  }
}
