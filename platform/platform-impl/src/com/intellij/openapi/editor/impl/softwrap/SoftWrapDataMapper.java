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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import org.jetbrains.annotations.NotNull;

/**
 * Every document that is exposed to end-user via IJ editor has a number of various dimensions ({@link LogicalPosition logical}
 * and {@link VisualPosition visual} positions, {@link Document#getCharsSequence() text offset} etc. It's very important to be
 * able to map one dimension to another and do that effectively.
 * <p/>
 * Current interface defines a contract for such a mapper.
 *
 * @author Denis Zhdanov
 * @since Aug 31, 2010 10:23:15 AM
 */
public interface SoftWrapDataMapper {

  /**
   * Maps given visual position to corresponding logical.
   *
   * @param visual    visual position to map
   * @return          logical position that corresponds to the given visual position
   * @throws IllegalStateException    if it's not possible to perform a mapping
   */
  @NotNull
  LogicalPosition visualToLogical(@NotNull VisualPosition visual) throws IllegalStateException;

  /**
   * Maps given offset to corresponding logical position.
   *
   * @param offset      offset to map
   * @return            logical position that corresponds to the given offset
   * @throws IllegalStateException    if it's not possible to perform a mapping
   */
  @NotNull
  LogicalPosition offsetToLogicalPosition(int offset) throws IllegalStateException;

  /**
   * Maps given logical position to corresponding visual position.
   *
   * @param logical                 logical position to map
   * @param softWrapUnawareVisual   soft wrap-unaware visual position that corresponds to the given logical position
   * @return                        visual position that corresponds to the given logical position
   * @throws IllegalStateException  if it's not possible to perform a mapping
   */
  VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logical, @NotNull VisualPosition softWrapUnawareVisual)
    throws IllegalStateException;
}
