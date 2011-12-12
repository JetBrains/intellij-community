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
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
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
   * Asks current model to map given visual position to logical.
   *
   * @param visual            target visual position for which logical position should be mapped
   * @return                  logical position that corresponds to the given visual position
   */
  @NotNull
  LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visual);

  /**
   * Asks current model to map given document offset to logical position.
   *
   * @param offset    target editor document offset
   * @return          logical position for the given editor document offset
   */
  @NotNull
  LogicalPosition offsetToLogicalPosition(int offset);

  /**
   * Asks current model to adjust visual position that corresponds to the given logical position if necessary.
   * <p/>
   * Given visual position is assumed to be the one that is obtained during soft wraps unaware processing.
   *
   * @param logical         target logical position for {@code 'logical' -> visual} conversion
   * @param defaultVisual   visual position of {@code 'logical' -> visual} conversion that is unaware about soft wraps
   * @return                resulting visual position for the given logical position
   */
  @NotNull
  VisualPosition adjustVisualPosition(@NotNull LogicalPosition logical, @NotNull VisualPosition defaultVisual);

  /**
   * @return    unmodifiable collection of soft wraps currently registered within the current model
   */
  List<? extends SoftWrap> getRegisteredSoftWraps();

  /**
   * Tries to find index of the target soft wrap at {@link #getRegisteredSoftWraps() soft wraps collection}.
   * <code>'Target'</code> soft wrap is the one that starts at the given offset.
   *
   * @param offset    target offset
   * @return          index that conforms to {@link Collections#binarySearch(List, Object)} contract, i.e. non-negative returned
   *                  index points to soft wrap that starts at the given offset; <code>'-(negative value) - 1'</code> points
   *                  to position at {@link #getRegisteredSoftWraps() soft wraps collection} where soft wrap for the given index
   *                  should be inserted
   */
  int getSoftWrapIndex(int offset);

  /**
   * Asks to paint drawing of target type at the given graphics buffer at the given position.
   *
   * @param g             target graphics buffer to draw in
   * @param drawingType   target drawing type
   * @param x             target <code>'x'</code> coordinate to use
   * @param y             target <code>'y'</code> coordinate to use
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
   * @return            <code>true</code> if given listener was not registered before; <code>false</code> otherwise
   */
  boolean addSoftWrapChangeListener(@NotNull SoftWrapChangeListener listener);

  /**
   * Instructs current soft wrap model about the place where corresponding editor is used.
   * <p/>
   * {@link SoftWrapAppliancePlaces#MAIN_EDITOR} is used by default.
   *
   * @param place   place where corresponding editor is used
   */
  void setPlace(@NotNull SoftWrapAppliancePlaces place);

  /** Asks the model to completely recalculate soft wraps. */
  void recalculate();

  /**
   * IJ editor defines a notion of {@link EditorSettings#getAdditionalColumnsCount() additional columns}. They define additional
   * amount of space to be used during editor component's width calculation (IJ editor perform 'preventive UI component expansion'
   * when user types near the right edge).
   * <p/>
   * The main idea of soft wraps is to avoid horizontal scrolling, however, there is a possible case that particular line
   * of text can't be soft-wrapped, i.e. we need to show horizontal scroll bar. So, we have the following use-cases:
   * <pre>
   * <ol>
   *   <li>
   *     <b>Long line is soft-wrapped</b>.
   *     <p/>
   *     Example:
   *     <p/>
   *     this a long lin[caret] |&lt;-- viewport's edge
   *     <p/>
   *     As soon as 'e' is typed, soft wrapping is performed and 'line' word is displayed at the next visual line, we need
   *     not to consider {@link EditorSettings#getAdditionalColumnsCount() additional columns} during width recalculation;
   *   </li>
   *   <li>
   *     <b>Long line can't be soft-wrapped</b>
   *     <p/>
   *     <code>Example:</code>
   *     thisisaratherlonglin[caret]|&lt;-- viewport's edge
   *     <p/>
   *     When 'e' is typed we need to increase component's width and use
   *     {@link EditorSettings#getAdditionalColumnsCount() additional columns} for its calculation;
   *   </li>
   * </ol>
   * </pre>
   * This method allows to answer if {@link EditorSettings#getAdditionalColumnsCount() additional columns} should be used
   * during editor component's width calculation.
   * 
   * @return      <code>true</code> if {@link EditorSettings#getAdditionalColumnsCount() additional columns} should be used
   *              during editor component's width recalculation;
   *              <code>false</code> otherwise
   */
  boolean isRespectAdditionalColumns();

  /**
   * Allows to instruct current model to always return <code>'true'</code> from {@link #isRespectAdditionalColumns()}.
   */
  void forceAdditionalColumnsUsage();
}
