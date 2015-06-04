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
package com.intellij.openapi.util;

import com.intellij.Patches;
import com.intellij.ui.FrameState;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Sergey.Malenkov
 */
class WindowStateServiceImpl extends WindowStateService.Service {
  @Override
  public boolean loadStateOn(GraphicsDevice screen, @NotNull String key, @NotNull Component component) {
    WindowState state = getOn(screen, key);
    if (state == null) {
      return false;
    }
    Frame frame = component instanceof Frame ? (Frame)component : null;
    if (frame != null) {
      frame.setExtendedState(Frame.NORMAL);
    }
    Rectangle bounds = component.getBounds();
    Point location = state.getLocation();
    if (location != null) {
      bounds.setLocation(location);
    }
    Dimension size = state.getSize();
    if (size != null) {
      bounds.setSize(size);
    }
    component.setBounds(bounds);
    if (!Patches.JDK_BUG_ID_8007219 && frame != null && FrameState.isMaximized(state.getExtendedState())) {
      frame.setExtendedState(Frame.MAXIMIZED_BOTH);
    }
    return true;
  }

  @Override
  public void saveStateOn(GraphicsDevice screen, @NotNull String key, @NotNull Component component) {
    FrameState state = FrameState.getFrameState(component);
    putOn(screen, key, state.getLocation(), true, state.getSize(), true, state.getExtendedState(), true);
  }
}
