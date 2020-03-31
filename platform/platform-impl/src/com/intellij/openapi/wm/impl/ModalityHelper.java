// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;

/**
 * @author Denis Fokin
 */
public final class ModalityHelper {
  private static final Logger LOG = Logger.getInstance(ModalityHelper.class);

  private static Method isModalBlockedMethod = null;
  private static Method  getModalBlockerMethod = null;

  static {
    Class [] noParams = new Class [] {};

    try {
      isModalBlockedMethod =  Window.class.getDeclaredMethod("isModalBlocked", noParams);
      getModalBlockerMethod =  Window.class.getDeclaredMethod("getModalBlocker", noParams);
      isModalBlockedMethod.setAccessible(true);
      getModalBlockerMethod.setAccessible(true);
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }

  }

  public static boolean isModalBlocked (final Window window) {
    boolean result = false;
    try {
      result = (Boolean)isModalBlockedMethod.invoke(window);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return result;
  }

  public static Dialog getModalBlockerFor (final Window window) {
    Dialog result = null;
    try {
      result = (Dialog)getModalBlockerMethod.invoke(window);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return result;
  }

  public static Dialog getBlockerForFrame(final IdeFrame ideFrame) {
    if (ideFrame == null) return null;
    Component c = ideFrame.getComponent();
    if (c == null) return null;
    Window window = SwingUtilities.getWindowAncestor(c);
    if (window == null) return null;
    if (!isModalBlocked(window)) return null;
    return getModalBlockerFor(window);
  }

  public static Dialog getBlockerForFocusedFrame() {
     return getBlockerForFrame(IdeFocusManager.getGlobalInstance().getLastFocusedFrame());
  }
}
