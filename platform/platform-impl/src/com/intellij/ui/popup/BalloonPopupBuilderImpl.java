// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Nullable private final Map<Disposable, List<Balloon>> myStorage;
  @Nullable private Disposable myAnchor;

  private final JComponent myContent;

  private Color myBorder = MessageType.INFO.getBorderColor();
  @Nullable private Insets myBorderInsets;
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

  public BalloonPopupBuilderImpl(@Nullable Map<Disposable, List<Balloon>> storage, @NotNull final JComponent content) {
    myStorage = storage;
    myContent = content;
    if (ClientProperty.isTrue(myContent, BalloonImpl.FORCED_NO_SHADOW)) {
      myShadow = false;
    }
  }

  @NotNull
  @Override
  public BalloonBuilder setHideOnAction(boolean hideOnAction) {
    myHideOnAction = hideOnAction;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setDialogMode(boolean dialogMode) {
    myDialogMode = dialogMode;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setBorderColor(@NotNull final Color color) {
    myBorder = color;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setBorderInsets(@Nullable Insets insets) {
    myBorderInsets = insets;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setFillColor(@NotNull final Color color) {
    myFill = color;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setHideOnClickOutside(final boolean hide) {
    myHideOnMouseOutside  = hide;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setHideOnKeyOutside(final boolean hide) {
    myHideOnKeyOutside = hide;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setShowCallout(final boolean show) {
    myShowCallout = show;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setFadeoutTime(long fadeoutTime) {
    myFadeoutTime = fadeoutTime;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setBlockClicksThroughBalloon(boolean block) {
    myBlockClicks = block;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setRequestFocus(boolean requestFocus) {
    myRequestFocus = requestFocus;
    return this;
  }

  @Override
  public BalloonBuilder setHideOnCloseClick(boolean hideOnCloseClick) {
    myHideOnCloseClick = hideOnCloseClick;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setAnimationCycle(int time) {
    myAnimationCycle = time;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setHideOnFrameResize(boolean hide) {
    myHideOnFrameResize = hide;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setHideOnLinkClick(boolean hide) {
    myHideOnLinkClick = hide;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setPositionChangeXShift(int positionChangeXShift) {
    myPositionChangeXShift = positionChangeXShift;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setPositionChangeYShift(int positionChangeYShift) {
    myPositionChangeYShift = positionChangeYShift;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setCloseButtonEnabled(boolean enabled) {
    myCloseButtonEnabled = enabled;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setClickHandler(ActionListener listener, boolean closeOnClick) {
    myClickHandler = listener;
    myCloseOnClick = closeOnClick;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setCalloutShift(int length) {
    myCalloutShift = length;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setTitle(@Nullable @NlsContexts.PopupTitle String title) {
    myTitle = title;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setContentInsets(Insets insets) {
    myContentInsets = insets;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setShadow(boolean shadow) {
    myShadow = shadow;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setSmallVariant(boolean smallVariant) {
    mySmallVariant = smallVariant;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setLayer(Balloon.Layer layer) {
    myLayer = layer;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setDisposable(@NotNull Disposable anchor) {
    myAnchor = anchor;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setPointerSize(Dimension size) {
    myPointerSize = size;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setCornerToPointerDistance(int distance) {
    myCornerToPointerDistance = distance;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setCornerRadius(int radius) {
    myCornerRadius = radius;
    return this;
  }

  @NotNull
  @Override
  public Balloon createBalloon() {
    final BalloonImpl result = new BalloonImpl(
      myContent, myBorder, myBorderInsets, myFill, myHideOnMouseOutside, myHideOnKeyOutside, myHideOnAction, myHideOnCloseClick,
      myShowCallout, myCloseButtonEnabled, myFadeoutTime, myHideOnFrameResize, myHideOnLinkClick, myClickHandler, myCloseOnClick,
      myAnimationCycle, myCalloutShift, myPositionChangeXShift, myPositionChangeYShift, myDialogMode, myTitle, myContentInsets, myShadow,
      mySmallVariant, myBlockClicks, myLayer, myRequestFocus, myPointerSize, myCornerToPointerDistance);
    result.setCornerRadius(myCornerRadius);

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
