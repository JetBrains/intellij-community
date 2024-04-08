// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.github.weisj.jsvg.nodes.SVG;

import java.awt.geom.Rectangle2D;

public class SVGUtil {
  public static Rectangle2D.Float getViewBox(SVG svg) {
    return svg.b;
  }
}
