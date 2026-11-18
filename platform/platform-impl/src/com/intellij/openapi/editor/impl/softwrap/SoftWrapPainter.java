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
package com.intellij.openapi.editor.impl.softwrap;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.Graphics;
import java.util.function.IntUnaryOperator;

/**
 * Defines contract for the service that manages soft wrap-related graphical effects.
 * <p/>
 * For example we may want to draw an arrow just before and after soft wrap-introduced line feed:
 * <p/>
 * <pre>
 *     This is a long text &#xE48B;
 *         &#xE48C;that is soft-wrapped
 * </pre>
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 */
@ApiStatus.Internal
public interface SoftWrapPainter {

  /**
   * Asks to paint drawing of target type at the given graphics buffer at the given position.
   *
   * @param g             target graphics buffer to draw in
   * @param drawingType   target drawing type
   * @param x             target {@code 'x'} coordinate to use
   * @param y             target {@code 'y'} coordinate to use
   * @param lineHeight    line height used at editor
   * @return              horizontal offset introduced to the given 'x' coordinate after target drawing painting
   */
  int paint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight);

  /**
   * Allows to ask about horizontal offset to be applied to the given {@code 'x'} coordinate if drawing of the given
   * type is performed at the given graphics buffer.
   * <p/>
   * Generally, this method is useful when we don't want to perform actual drawing for now but want to reserve
   * a space necessary to do that in future. I.e. the aim is to avoid horizontal movement of already drawn content
   * when the drawing is actually performed.
   *
   * @param g             target graphics buffer to draw in
   * @param drawingType   target drawing type
   * @param x             target {@code 'x'} coordinate to use
   * @param y             target {@code 'y'} coordinate to use
   * @param lineHeight    line height used at editor
   * @return              horizontal offset that would be introduced if the drawing is performed
   */
  int getDrawingHorizontalOffset(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight);

  /**
   * Allows to ask for the minimal width in pixels required for painting of the given type.
   *
   * @param drawingType   target drawing type
   * @return              width in pixels required for the painting of the given type
   */
  int getMinDrawingWidth(@NotNull SoftWrapDrawingType drawingType);

  /**
   * Returns the offset in pixels where the soft-wrapped parts of a logical line start.
   * The offset is measured from the line start.
   * <p>
   * The offset can point inside an inlay or a fold placeholder.
   * If no character or fold starts between the offset and the soft wrap, the editor ignores the offset.
   * The soft-wrapped parts then start at the custom soft-wrap indent from the line start.
   * The editor calls this method only with a custom soft-wrap indent, when the line gets its first soft wrap.
   *
   * @param lineStartOffset the document offset at the start of the logical line
   * @param offsetToX gives the width in pixels from the line start to a document offset before the first soft wrap.
   *                  The width stops before the inlays at that offset.
   * @return the offset in pixels, or -1 to start the soft-wrapped parts under the first non-whitespace character
   */
  default int getIndentOffset(int lineStartOffset, @NotNull IntUnaryOperator offsetToX) {
    return -1;
  }

  /**
   * Allows to answer if it's possible to use current painter implementation at local environment (e.g. there is a possible
   * case that particular painter that exploits unicode symbols for drawing can't be used because there is no font
   * at local environment that knows how to draw target symbols).
   *
   * @return    {@code true} if current painter can be used at local environment; {@code false} otherwise
   */
  boolean canUse();

  /**
   * Called after a change of font preferences in editor, so that a painter could reset any related internal caches.
   */
  void reinit();
}
