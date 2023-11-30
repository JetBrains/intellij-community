// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.BalloonImpl;
import com.intellij.ui.ClientProperty;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BalloonPopupBuilderImpl implements BalloonBuilder {
  private final @Nullable Map<Disposable, List<Balloon>> myStorage;
  private @Nullable Disposable myAnchor;

  private final JComponent myContent;

  private Color myBorder = MessageType.INFO.getBorderColor();
  private @Nullable Insets myBorderInsets;
  private Color myFill = MessageType.INFO.getPopupBackground();
  private boolean myHideOnMouseOutside = true;
  private boolean myHideOnKeyOutside = true;
  private long myFadeoutTime = -1;
  private boolean myShowCallout = true;
  private boolean myCloseButtonEnabled;
  private boolean myHideOnFrameResize = true;
  private boolean myHideOnLinkClick;

  private ActionListener myClickHandler;
  private boolean        myCloseOnClick;
  private int myAnimationCycle = 500;

  private int myCalloutShift;
  private int myPositionChangeXShift;
  private int myPositionChangeYShift;
  private boolean myHideOnAction = true;
  private boolean myHideOnCloseClick = true;
  private boolean myDialogMode;
  private @NlsContexts.PopupTitle String  myTitle;
  private Insets  myContentInsets = JBUI.insets(2);
  private boolean myShadow        = true;
  private boolean mySmallVariant;
  private Balloon.Layer myLayer;
  private boolean myBlockClicks;
  private boolean myRequestFocus;

  private Dimension myPointerSize;
  private int       myCornerToPointerDistance = -1;
  private int myCornerRadius = -1;
  private boolean myPointerShiftedToStart;

  public BalloonPopupBuilderImpl(@Nullable Map<Disposable, List<Balloon>> storage, final @NotNull JComponent content) {
    myStorage = storage;
    myContent = content;
    if (ClientProperty.isTrue(myContent, BalloonImpl.FORCED_NO_SHADOW)) {
      myShadow = false;
    }
  }

  @Override
  public @NotNull BalloonBuilder setHideOnAction(boolean hideOnAction) {
    myHideOnAction = hideOnAction;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setDialogMode(boolean dialogMode) {
    myDialogMode = dialogMode;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setBorderColor(final @NotNull Color color) {
    myBorder = color;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setBorderInsets(@Nullable Insets insets) {
    myBorderInsets = insets;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setFillColor(final @NotNull Color color) {
    myFill = color;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setHideOnClickOutside(final boolean hide) {
    myHideOnMouseOutside  = hide;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setHideOnKeyOutside(final boolean hide) {
    myHideOnKeyOutside = hide;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setShowCallout(final boolean show) {
    myShowCallout = show;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setFadeoutTime(long fadeoutTime) {
    myFadeoutTime = fadeoutTime;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setBlockClicksThroughBalloon(boolean block) {
    myBlockClicks = block;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setRequestFocus(boolean requestFocus) {
    myRequestFocus = requestFocus;
    return this;
  }

  @Override
  public BalloonBuilder setHideOnCloseClick(boolean hideOnCloseClick) {
    myHideOnCloseClick = hideOnCloseClick;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setAnimationCycle(int time) {
    myAnimationCycle = time;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setHideOnFrameResize(boolean hide) {
    myHideOnFrameResize = hide;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setHideOnLinkClick(boolean hide) {
    myHideOnLinkClick = hide;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setPositionChangeXShift(int positionChangeXShift) {
    myPositionChangeXShift = positionChangeXShift;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setPositionChangeYShift(int positionChangeYShift) {
    myPositionChangeYShift = positionChangeYShift;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setCloseButtonEnabled(boolean enabled) {
    myCloseButtonEnabled = enabled;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setClickHandler(ActionListener listener, boolean closeOnClick) {
    myClickHandler = listener;
    myCloseOnClick = closeOnClick;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setCalloutShift(int length) {
    myCalloutShift = length;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setTitle(@Nullable @NlsContexts.PopupTitle String title) {
    myTitle = title;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setContentInsets(Insets insets) {
    myContentInsets = insets;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setShadow(boolean shadow) {
    myShadow = shadow;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setSmallVariant(boolean smallVariant) {
    mySmallVariant = smallVariant;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setLayer(Balloon.Layer layer) {
    myLayer = layer;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setDisposable(@NotNull Disposable anchor) {
    myAnchor = anchor;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setPointerSize(Dimension size) {
    myPointerSize = size;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setCornerToPointerDistance(int distance) {
    myCornerToPointerDistance = distance;
    return this;
  }

  @Override
  public @NotNull BalloonBuilder setCornerRadius(int radius) {
    myCornerRadius = radius;
    return this;
  }

  @Override
  public BalloonBuilder setPointerShiftedToStart(boolean pointerShiftedToStart) {
    myPointerShiftedToStart = pointerShiftedToStart;
    return this;
  }

  @Override
  public @NotNull Balloon createBalloon() {
    final BalloonImpl result = new BalloonImpl(
      myContent, myBorder, myBorderInsets, myFill, myHideOnMouseOutside, myHideOnKeyOutside, myHideOnAction, myHideOnCloseClick,
      myShowCallout, myCloseButtonEnabled, myFadeoutTime, myHideOnFrameResize, myHideOnLinkClick, myClickHandler, myCloseOnClick,
      myAnimationCycle, myCalloutShift, myPositionChangeXShift, myPositionChangeYShift, myDialogMode, myTitle, myContentInsets, myShadow,
      mySmallVariant, myBlockClicks, myLayer, myRequestFocus, myPointerSize, myCornerToPointerDistance);
    result.setCornerRadius(myCornerRadius);
    result.setPointerShiftedToStart(myPointerShiftedToStart);

    if (myStorage != null && myAnchor != null) {
      List<Balloon> balloons = myStorage.get(myAnchor);
      if (balloons == null) {
        myStorage.put(myAnchor, balloons = new ArrayList<>());
        Disposer.register(myAnchor, () -> {
          List<Balloon> toDispose = myStorage.remove(myAnchor);
          if (toDispose != null) {
            for (Balloon balloon : toDispose) {
              if (!balloon.isDisposed()) {
                Disposer.dispose(balloon);
              }
            }
          }
        });
      }
      balloons.add(result);
      result.addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          if (!result.isDisposed()) {
            Disposer.dispose(result);
          }
        }
      });
    }

    return result;
  }
}
