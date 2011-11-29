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
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

public class MouseGestureManager implements ApplicationComponent {

  private static final Logger LOG = Logger.getInstance("MouseGestureManager");
  private ActionManagerImpl myActionManager;

  private Map<IdeFrame, Object> myListeners = new HashMap<IdeFrame, Object>();
  private boolean HAS_TRACKPAD = false;
  

  public MouseGestureManager(ActionManagerImpl actionManager) {
    myActionManager = actionManager;
  }

  public void add(final IdeFrame frame) {
    if (!Registry.is("actionSystem.mouseGesturesEnabled")) return;

    if (SystemInfo.isMacOSSnowLeopard) {
      try {
        if (myListeners.containsKey(frame)) {
          remove(frame);
        }

        GestureAdapter listener = new GestureAdapter() {
          double magnification = 0;
          @Override
          public void gestureBegan(GesturePhaseEvent event) {
            activateTrackpad();
            magnification = 0;
          }

          @Override
          public void gestureEnded(GesturePhaseEvent event) {
            activateTrackpad();
            if (magnification != 0) {
              processMagnification(frame, magnification);
              magnification = 0;
            }
          }

          @Override
          public void swipedLeft(SwipeEvent event) {
            activateTrackpad();
            processLeftSwipe(frame);
          }

          @Override
          public void swipedRight(SwipeEvent event) {
            activateTrackpad();
            processRightSwipe(frame);
          }

          @Override
          public void magnify(MagnificationEvent event) {
            activateTrackpad();
            magnification += event.getMagnification();
          }
        };

        GestureUtilities.addGestureListenerTo(frame.getComponent(), listener);

        myListeners.put(frame, listener);
      }
      catch (Throwable e) {
        LOG.debug(e);
      }
    }
  }

  private void activateTrackpad() {
    HAS_TRACKPAD = true;
  }
  
  public boolean hasTrackpad() {
    return HAS_TRACKPAD;
  }

  private static void processMagnification(IdeFrame frame, double magnification) {
    Point mouse = MouseInfo.getPointerInfo().getLocation();
    SwingUtilities.convertPointFromScreen(mouse, frame.getComponent());
    Component componentAt = SwingUtilities.getDeepestComponentAt(frame.getComponent(), mouse.x, mouse.y);
    if (componentAt != null) {
      Editor editor = PlatformDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext(componentAt));
      if (editor != null) {
        double currentSize = editor.getColorsScheme().getEditorFontSize();
        int defaultFontSize = EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize();
        ((EditorEx)editor).setFontSize((int)(Math.max(currentSize + magnification * 3, defaultFontSize)));
      }
    }
  }

  private void processLeftSwipe(IdeFrame frame) {
    AnAction forward = myActionManager.getAction("Forward");
    if (forward == null) return;

    myActionManager.tryToExecute(forward, createMouseEventWrapper(frame), null, null, false);
  }

  private void processRightSwipe(IdeFrame frame) {
    AnAction back = myActionManager.getAction("Back");
    if (back == null) return;

    myActionManager.tryToExecute(back, createMouseEventWrapper(frame), null, null, false);
  }

  private static MouseEvent createMouseEventWrapper(IdeFrame frame) {
    return new MouseEvent(frame.getComponent(), ActionEvent.ACTION_PERFORMED, System.currentTimeMillis(), 0, 0, 0, 0, false, 0);
  }

  public void remove(IdeFrame frame) {
    if (!Registry.is("actionSystem.mouseGesturesEnabled")) return;

    if (SystemInfo.isMacOSSnowLeopard) {
      try {
        Object listener = myListeners.get(frame);
        JComponent cmp = frame.getComponent();
        myListeners.remove(frame);
        if (listener != null && cmp != null && cmp.isShowing()) {
          GestureUtilities.removeGestureListenerFrom(cmp, (GestureListener)listener);
        }
      }
      catch (Throwable e) {
        LOG.debug(e);
      }
    }

  }

  @Override
  public void initComponent() {

  }

  @Override
  public void disposeComponent() {

  }

  @NotNull
  @Override
  public String getComponentName() {
    return "MouseGestureListener";
  }

  public static MouseGestureManager getInstance() {
    return ApplicationManager.getApplication().getComponent(MouseGestureManager.class);
  }
}
