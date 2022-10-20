// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
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
 * @deprecated use {@link GotItTooltip} instead
 */
@Deprecated
public final class GotItMessage {
  @NotNull private final @NlsContexts.PopupContent String myTitle;
  @NotNull private final @NlsContexts.PopupContent String myMessage;

  private Disposable myDisposable;
  private Runnable myCallback;
  private HyperlinkListener myHyperlinkListener = BrowserHyperlinkListener.INSTANCE;
  private boolean myShowCallout = true;

  private GotItMessage(@NlsContexts.PopupContent @NotNull String title, @NlsContexts.PopupContent @NotNull String message) {
    myTitle = title;
    myMessage =
      new HtmlBuilder()
        .append(HtmlChunk.div("font-family: \" + UIUtil.getLabelFont().getFontName() + \"; font-size: \" +\n" +
                              "      JBUIScale.scale(12) + \"pt;")
                  .attr("align", "center")
                  .addRaw(StringUtil.replace(message, "\n", "<br>")))
        .wrapWithHtmlBody()
        .toString();
  }

  public static GotItMessage createMessage(@NotNull @NlsContexts.PopupContent String title, @NotNull @NlsContexts.PopupContent String message) {
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
