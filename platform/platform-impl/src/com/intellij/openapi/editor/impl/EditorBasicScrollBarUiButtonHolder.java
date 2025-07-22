// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.util.MethodHandleUtil;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.lang.invoke.MethodHandle;


// not used on macOS and some other platforms - lazy creation
final class BasicScrollBarUiButtonHolder {

  static final MethodHandle decrButtonField = MethodHandleUtil.getPrivateField(
    BasicScrollBarUI.class,
    "decrButton",
    JButton.class
  );

  static final MethodHandle incrButtonField = MethodHandleUtil.getPrivateField(
    BasicScrollBarUI.class,
    "incrButton",
    JButton.class
  );
}
