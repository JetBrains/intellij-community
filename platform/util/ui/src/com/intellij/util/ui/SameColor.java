// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import java.awt.*;

/**
 * @author Eugene Belyaev
 * @deprecated use {@link com.intellij.ui.Gray} instead
 */
@Deprecated
public class SameColor extends Color {
  public SameColor(int i) {
    super(i, i, i);
  }
}
