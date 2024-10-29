// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.utils;

import org.jetbrains.annotations.ApiStatus;

import java.awt.event.KeyEvent;

@ApiStatus.Internal
public final class KeyboardUtils {
  public static boolean isEnterKey(int keyCode) {
    return keyCode == KeyEvent.VK_ENTER;
  }

  public static boolean isUpDownKey(int keyCode) {
    return keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN;
  }

  public static boolean isBackSpace(int keyCode) {
    return keyCode == KeyEvent.VK_BACK_SPACE;
  }

  public static boolean isPartOfJavaClassName(char ch) {
    return Character.isLetterOrDigit(ch) || ch == '.' || ch == '$' || ch == '[' || ch == ']';
  }
}
