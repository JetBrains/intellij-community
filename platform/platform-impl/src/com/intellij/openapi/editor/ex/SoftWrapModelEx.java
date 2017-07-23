/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * Extends {@link SoftWrapModel} in order to define implementation-specific contract.
 *
 * @author Denis Zhdanov
 * @since Jun 16, 2010 10:53:59 AM
 */
public interface SoftWrapModelEx extends SoftWrapModel {

  /**
   * @return    unmodifiable collection of soft wraps currently registered within the current model
   */
  List<? extends SoftWrap> getRegisteredSoftWraps();

  /**
   * Tries to find index of the target soft wrap at {@link #getRegisteredSoftWraps() soft wraps collection}.
   * {@code 'Target'} soft wrap is the one that starts at the given offset.
   *
   * @param offset    target offset
   * @return          index that conforms to {@link Collections#binarySearch(List, Object)} contract, i.e. non-negative returned
   *                  index points to soft wrap that starts at the given offset; {@code '-(negative value) - 1'} points
   *                  to position at {@link #getRegisteredSoftWraps() soft wraps collection} where soft wrap for the given index
   *                  should be inserted
   */
  int getSoftWrapIndex(int offset);

  /**
   * Asks to paint drawing of target type at the given graphics buffer at the given position.
   *
   * @param g             target graphics buffer to draw in
   * @param drawingType   target drawing type
   * @param x             target {@code 'x'} coordinate to use
   * @param y             target {@code 'y'} coordinate to use
   * @param lineHeight    line height used at editor
   * @return              painted drawing width
   */
  int paint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight);

  /**
   * Allows to ask for the minimal width in pixels required for painting of the given type.
   *
   * @param drawingType   target drawing type
   * @return              width in pixels required for the painting of the given type
   */
  int getMinDrawingWidthInPixels(@NotNull SoftWrapDrawingType drawingType);

  /**
   * Registers given listener within the current model
   *
   * @param listener    listener to register
   * @return            {@code true} if given listener was not registered before; {@code false} otherwise
   */
  boolean addSoftWrapChangeListener(@NotNull SoftWrapChangeListener listener);

  /**
   * IJ editor defines a notion of {@link EditorSettings#getAdditionalColumnsCount() additional columns}. They define additional
   * amount of space to be used during editor component's width calculation (IJ editor perform 'preventive UI component expansion'
   * when user types near the right edge).
   * <p/>
   * The main idea of soft wraps is to avoid horizontal scrolling, so, when soft wrapping is enabled and succeeds (so that resulting
   * text layout fits view area's width), additional columns wont be added to the preferred editor width.
   * This method answers whether the above behaviour should be overridden, and additional columns setting should be respected regardless of
   * soft wrapping success. This happens when {@link #forceAdditionalColumnsUsage()} has been invoked previously.
   */
  boolean isRespectAdditionalColumns();

  /**
   * Allows to instruct current model to return {@code 'true'} from {@link #isRespectAdditionalColumns()}.
   */
  void forceAdditionalColumnsUsage();

  EditorTextRepresentationHelper getEditorTextRepresentationHelper();
}
