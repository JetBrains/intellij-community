/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.openapi.wm.impl.content;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

public class FlippedIcon implements Icon {
  private final Icon myDelegate;

  public FlippedIcon(Icon delegate) {
    myDelegate = delegate;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    final Graphics2D graphics = (Graphics2D)g.create();
    graphics.setTransform(AffineTransform.getQuadrantRotateInstance(2, myDelegate.getIconWidth() / 2.0, myDelegate.getIconHeight() / 2.0));
    myDelegate.paintIcon(c, graphics, x, 0);
  }

  @Override
  public int getIconWidth() {
    return myDelegate.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return myDelegate.getIconHeight();
  }
}
