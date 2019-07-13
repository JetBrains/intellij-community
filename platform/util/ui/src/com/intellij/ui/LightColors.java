// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import java.awt.*;

/**
 * @author max
 */
public interface LightColors {
  Color YELLOW = new JBColor(new Color(0xffffcc), new Color(0x525229));
  Color GREEN = new JBColor(new Color(0xccffcc), new Color(0x356936));
  Color BLUE = new JBColor(new Color(0xccccff), new Color(0x589df6));
  Color RED = JBColor.namedColor("SearchField.errorBackground", new JBColor(0xffcccc, 0x743A3A));
  Color CYAN = new JBColor(new Color(0xccffff), new Color(100, 138, 138));

  Color SLIGHTLY_GREEN = new JBColor(new Color(0xeeffee), new Color(0x515B51));
  Color SLIGHTLY_GRAY = new JBColor(new Color(0xf5f5f5), new Color(0xc0c0c0));
}
