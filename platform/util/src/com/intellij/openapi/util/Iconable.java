// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.intellij.lang.annotations.MagicConstant;

import javax.swing.*;

public interface Iconable {
  int ICON_FLAG_VISIBILITY = 0x0001;
  int ICON_FLAG_READ_STATUS = 0x0002;

  @MagicConstant(flags = {ICON_FLAG_VISIBILITY, ICON_FLAG_READ_STATUS})
  @interface IconFlags {}

  Icon getIcon(@IconFlags int flags);
}
