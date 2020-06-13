// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.ui;

import java.awt.*;

public interface NullableComponent {

  boolean isNull();

  final class Check {
    public static boolean isNull(Component c) {
      return c == null || c instanceof NullableComponent && ((NullableComponent)c).isNull();
    }

    public static boolean isNullOrHidden(Component c) {
      return c != null && !c.isShowing() || isNull(c);
    }
  }
}
