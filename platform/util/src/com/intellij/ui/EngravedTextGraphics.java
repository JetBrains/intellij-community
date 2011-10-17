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
package com.intellij.ui;

import java.awt.*;
import java.text.AttributedCharacterIterator;

public class EngravedTextGraphics extends Graphics2DDelegate {
  public EngravedTextGraphics(Graphics2D g2d) {
    super(g2d);
  }

  @Override
  public Graphics create() {
    return new EngravedTextGraphics((Graphics2D)myDelegate.create());
  }

  @Override
  public void drawChars(char[] data, int offset, int length, int x, int y) {
    final Color color = getColor();
    setColor(new Color(245, 245, 245));
    super.drawChars(data, offset, length, x, y + 1);

    setColor(color);
    super.drawChars(data, offset, length, x, y);
  }

  @Override
  public void drawString(String str, int x, int y) {
    final Color color = getColor();

    setColor(new Color(245, 245, 245));
    super.drawString(str, x, y + 1);

    setColor(color);
    super.drawString(str, x, y);
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, float x, float y) {
    final Color color = getColor();

    setColor(new Color(245, 245, 245));
    super.drawString(iterator, x, y + 1);

    setColor(color);
    super.drawString(iterator, x, y);
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, int x, int y) {
    final Color color = getColor();

    setColor(new Color(245, 245, 245));
    super.drawString(iterator, x, y + 1);

    setColor(color);
    super.drawString(iterator, x, y);
  }

  @Override
  public void drawString(String s, float x, float y) {
    final Color color = getColor();

    setColor(new Color(245, 245, 245));
    super.drawString(s, x, y + 1);

    setColor(color);
    super.drawString(s, x, y);
  }
}
