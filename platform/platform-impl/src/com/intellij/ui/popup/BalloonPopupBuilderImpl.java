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
package com.intellij.ui.popup;

import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.ui.BalloonImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class BalloonPopupBuilderImpl implements BalloonBuilder {

  JComponent myContent;
  Color myBorder = Color.gray;
  Color myFill = new Color(186, 238, 186, 230);
  boolean myHideOnMouseOutside = true;
  boolean myHideOnKeyOutside = true;
  long myFadeoutTime = -1;

  private Balloon.Position myPrefferedPosition = Balloon.Position.below;

  boolean myShowCalllout = true;
  private boolean myCloseButtonEnabled;
  private boolean myHideOnFrameResize = true;

  private ActionListener myClickHandler;
  private boolean myCloseOnClick;
  private int myAnimationCycle = 500;
  private int myCalloutShift;

  public BalloonPopupBuilderImpl(@NotNull final JComponent content) {
    myContent = content;
  }

  @NotNull
  public BalloonBuilder setPreferredPosition(final Balloon.Position position) {
    myPrefferedPosition = position;
    return this;
  }

  @NotNull
  public BalloonBuilder setBorderColor(@NotNull final Color color) {
    myBorder = color;
    return this;
  }

  @NotNull
  public BalloonBuilder setFillColor(@NotNull final Color color) {
    myFill = color;
    return this;
  }

  @NotNull
  public BalloonBuilder setHideOnClickOutside(final boolean hide) {
    myHideOnMouseOutside  = hide;
    return this;
  }

  @NotNull
  public BalloonBuilder setHideOnKeyOutside(final boolean hide) {
    myHideOnKeyOutside = hide;
    return this;
  }

  @NotNull
  public BalloonBuilder setShowCallout(final boolean show) {
    myShowCalllout = show;
    return this;
  }

  @NotNull
  public BalloonBuilder setFadeoutTime(long fadeoutTime) {
    myFadeoutTime = fadeoutTime;
    return this;
  }

  @NotNull
  @Override
  public BalloonBuilder setAnimationCycle(int time) {
    myAnimationCycle = time;
    return this;
  }

  @NotNull
  public BalloonBuilder setHideOnFrameResize(boolean hide) {
    myHideOnFrameResize = hide;
    return this;
  }

  @NotNull
  public Balloon createBalloon() {
    return new BalloonImpl(myContent, myBorder, myFill, myHideOnMouseOutside, myHideOnKeyOutside, myShowCalllout, myCloseButtonEnabled, myFadeoutTime, myHideOnFrameResize, myClickHandler, myCloseOnClick, myAnimationCycle,
                           myCalloutShift);
  }

  @NotNull
  public BalloonBuilder setCloseButtonEnabled(boolean enabled) {
    myCloseButtonEnabled = enabled;
    return this;
  }

  @NotNull
  public BalloonBuilder setClickHandler(ActionListener listener, boolean closeOnClick) {
    myClickHandler = listener;
    myCloseOnClick = closeOnClick;
    return this;
  }

  @Override
  public BalloonBuilder setCalloutShift(int length) {
    myCalloutShift = length;
    return this;
  }
}