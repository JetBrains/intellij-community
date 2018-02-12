/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * IDEA editors use highly-optimized drawing algorithm that is tuned for painting large amounts of text data.
 * <p/>
 * However, there is a possible case that particular editor extension or third-party component may want to draw text at
 * IDEA editor on it own. It's not supposed to do that directly, contrary, it's expected to delegate the processing
 * to the code that knows how to do that.
 * <p/>
 * Current interface defines a contract for such a text drawing delegation task.
 *
 * @author Denis Zhdanov
 * @since Jul 1, 2010 8:01:30 PM
 */
public interface TextDrawingCallback {

  /**
   * Asks to draw symbols from {@code [start; end)} range of given char array at given graphics buffer using given
   * font info and color.
   *
   * @param g         graphics buffer to use
   * @param data      target symbols holder
   * @param start     start offset within the symbols holder to use (inclusive)
   * @param end       end offset within the symbols holder to use (inclusive)
   * @param x         {@code 'x'} coordinate to use as a start position at the given graphics buffer
   * @param y         {@code 'y'} coordinate to use as a start position at the given graphics buffer
   * @param fontInfo  font info to use during drawing target text at the given graphics buffer
   * @param color     color to use during drawing target text at the given graphics buffer
   */
  void drawChars(@NotNull Graphics g, @NotNull char[] data, int start, int end, int x, int y, Color color, FontInfo fontInfo);
}
