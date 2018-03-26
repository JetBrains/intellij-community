/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.IdeFrame;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;

/**
 * @author Denis Fokin
 */
public class ModalityHelper {

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
     return getBlockerForFrame(IdeFocusManagerImpl.getGlobalInstance().getLastFocusedFrame());
  }

}
