// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.ui;

import java.awt.*;

public interface NullableComponent {

  boolean isNull();

  class Check {
    private Check() {
    }

    public static boolean isNull(Component c) {
      return c == null || c instanceof NullableComponent && ((NullableComponent)c).isNull();
    }

    public static boolean isNullOrHidden(Component c) {
      return c != null && !c.isShowing() || isNull(c);
    }

    public static boolean isNotNullAndVisible(Component c) {
      return !isNull(c) && c.isVisible();
    }
  }
}
