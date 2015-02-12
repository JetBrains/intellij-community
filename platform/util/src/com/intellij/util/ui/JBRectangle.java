/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.ui;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBRectangle extends Rectangle {
  public JBRectangle() {
  }

  public JBRectangle(int x, int y, int width, int height) {
    super(JBUI.scale(x), JBUI.scale(y), JBUI.scale(width), JBUI.scale(height));
  }

  public JBRectangle(Rectangle r) {
    if (r instanceof JBRectangle) {
      x = r.x;
      y = r.y;
      width = r.width;
      height = r.height;
    } else {
      x = JBUI.scale(r.x);
      y = JBUI.scale(r.y);
      width = JBUI.scale(r.width);
      height = JBUI.scale(r.height);
    }
  }

  public void clear() {
    x = y = width = height = 0;
  }
}
