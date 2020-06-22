// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

@Service
public final class MouseGestureManager {
  private static final Logger LOG = Logger.getInstance("MouseGestureManager");

  private final Map<IdeFrame, Object> myListeners = new HashMap<>();
  private boolean hasTrackPad = false;

  public static MouseGestureManager getInstance() {
    return ServiceManager.getService(MouseGestureManager.class);
  }

  public void add(@NotNull IdeFrame frame) {
    if (!SystemInfo.isMacOSSnowLeopard || !Registry.is("actionSystem.mouseGesturesEnabled")) {
      return;
    }

    try {
      if (myListeners.containsKey(frame)) {
        remove(frame);
      }
      myListeners.put(frame, new MacGestureAdapter(this, frame));
    }
    catch (Throwable e) {
      LOG.error("Can't initialize MacGestureAdapter", e);
    }
  }

  void activateTrackpad() {
    hasTrackPad = true;
  }

  public boolean hasTrackpad() {
    return hasTrackPad;
  }

  public void remove(@NotNull IdeFrame frame) {
    if (!SystemInfo.isMacOSSnowLeopard || !Registry.is("actionSystem.mouseGesturesEnabled")) {
      return;
    }

    try {
      Object listener = myListeners.get(frame);
      JComponent cmp = frame.getComponent();
      myListeners.remove(frame);
      if (listener != null && cmp != null && cmp.isShowing()) {
        ((MacGestureAdapter)listener).remove(cmp);
      }
    }
    catch (Throwable e) {
      LOG.debug(e);
    }
  }
}
