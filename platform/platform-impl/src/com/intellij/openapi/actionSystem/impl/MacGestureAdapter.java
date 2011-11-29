/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.impl;

import com.apple.eawt.event.*;
import com.intellij.openapi.wm.IdeFrame;

import javax.swing.*;

/**
* User: anna
* Date: 11/29/11
*/
class MacGestureAdapter extends GestureAdapter {
  double magnification;
  private final IdeFrame myFrame;
  private MouseGestureManager myManager;

  public MacGestureAdapter(MouseGestureManager manager, IdeFrame frame) {
    myFrame = frame;
    magnification = 0;
    myManager = manager;
    GestureUtilities.addGestureListenerTo(frame.getComponent(), this);
  }

  @Override
  public void gestureBegan(GesturePhaseEvent event) {
    myManager.activateTrackpad();
    magnification = 0;
  }

  @Override
  public void gestureEnded(GesturePhaseEvent event) {
    myManager.activateTrackpad();
    if (magnification != 0) {
      MouseGestureManager.processMagnification(myFrame, magnification);
      magnification = 0;
    }
  }

  @Override
  public void swipedLeft(SwipeEvent event) {
    myManager.activateTrackpad();
    myManager.processLeftSwipe(myFrame);
  }

  @Override
  public void swipedRight(SwipeEvent event) {
    myManager.activateTrackpad();
    myManager.processRightSwipe(myFrame);
  }

  @Override
  public void magnify(MagnificationEvent event) {
    myManager.activateTrackpad();
    magnification += event.getMagnification();
  }

  public void remove(JComponent cmp) {
    GestureUtilities.removeGestureListenerFrom(cmp, this);
  }
}
