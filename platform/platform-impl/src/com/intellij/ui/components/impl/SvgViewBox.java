// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.impl;

import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.nodes.SVG;

/**
 * This class serves as a workaround for K2 compiler bug KT-71916
 */
class SvgViewBox {

  private final ViewBox myViewBox;

  SvgViewBox(SVG svg) {
    myViewBox = svg.b;
  }

  float getWidth() {
    return myViewBox != null ? myViewBox.width : -1f;
  }

  float getHeight() {
    return myViewBox != null ? myViewBox.height : -1f;
  }
}
