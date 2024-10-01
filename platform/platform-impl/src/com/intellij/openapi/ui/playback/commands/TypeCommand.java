// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

@ApiStatus.Internal
public abstract class TypeCommand extends AbstractCommand {

  private static final Logger LOG = Logger.getInstance(TypeCommand.class);
  private static final KeyStrokeMap ourMap = new KeyStrokeMap();
  private static boolean metaKeyPresent = true;

  public TypeCommand(String text, int line, boolean executeInAwt) {
    super(text, line, executeInAwt);
  }

  protected void type(Robot robot, int code, @JdkConstants.InputEventMask int modifiers) {
    type(robot, KeyStroke.getKeyStroke(code, modifiers));
  }

  protected void type(Robot robot, KeyStroke keyStroke) {
    assert !SwingUtilities.isEventDispatchThread() : "Robot playback must not be in EDT";

    boolean shift = (keyStroke.getModifiers() & InputEvent.SHIFT_MASK) > 0;
    boolean alt = (keyStroke.getModifiers() & InputEvent.ALT_MASK) > 0;
    boolean control = (keyStroke.getModifiers() & InputEvent.CTRL_MASK) > 0;
    boolean meta = (keyStroke.getModifiers() & InputEvent.META_MASK) > 0;

    if (shift) {
      robot.keyPress(KeyEvent.VK_SHIFT);
    }
    else {
      robot.keyRelease(KeyEvent.VK_SHIFT);
    }

    if (control) {
      robot.keyPress(KeyEvent.VK_CONTROL);
    }
    else {
      robot.keyRelease(KeyEvent.VK_CONTROL);
    }

    if (alt) {
      robot.keyPress(KeyEvent.VK_ALT);
    }
    else {
      robot.keyRelease(KeyEvent.VK_ALT);
    }

    if (meta) {
      robot.keyPress(KeyEvent.VK_META);
    }
    else if (metaKeyPresent) {
      try {
        robot.keyRelease(KeyEvent.VK_META);
      }
      catch (IllegalArgumentException e) {
        LOG.warn(e);
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        metaKeyPresent = false;
      }
    }

    if (keyStroke.getKeyCode() > 0) {
      robot.keyPress(keyStroke.getKeyCode());
      robot.delay(Registry.intValue("actionSystem.playback.delay"));
      robot.keyRelease(keyStroke.getKeyCode());
    } else {
      robot.keyPress(keyStroke.getKeyChar());
      robot.delay(Registry.intValue("actionSystem.playback.delay"));
      robot.keyRelease(keyStroke.getKeyChar());
    }


    if (shift) {
      robot.keyRelease(KeyEvent.VK_SHIFT);
    }

    if (control) {
      robot.keyRelease(KeyEvent.VK_CONTROL);
    }

    if (alt) {
      robot.keyRelease(KeyEvent.VK_ALT);
    }

    if (meta) {
      robot.keyRelease(KeyEvent.VK_META);
    }
  }

  protected KeyStroke get(char c) {
    return ourMap.get(c);
  }

  protected KeyStroke getFromShortcut(String sc) {
    return ourMap.get(sc);
  }

  public static boolean containsUnicode(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (!ourMap.containsChar(s.charAt(i))) return true;
    }

    return false;
  }

  static void inWriteSafeContext(Runnable runnable) {
    ModalityState modality = ModalityState.current();
    ApplicationManager.getApplication().invokeLater(
      () -> IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(runnable, modality),
      modality);
  }
}
