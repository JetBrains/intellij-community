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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines contract for the strategy that knows how to map one document dimension to another (e.g. visual position to logical position).
 *
 * @param <T>     target dimension type
 */
interface MappingStrategy<T> {

  /**
   * @return    target mapped dimension if it's possible to perform the mapping eagerly; <code>null</code> otherwise
   */
  @Nullable
  T eagerMatch();

  /**
   * Builds initial context to start calculation from.
   * <p/>
   * It's assumed that we store information about 'anchor' document positions like visual line starts and calculate
   * target result starting from the nearest position.
   *
   * @return    initial context to use for target result calculation
   */
  @NotNull
  ProcessingContext buildInitialContext();

  /**
   * Notifies current strategy that there are no special symbols and regions between the document position identified
   * by the current state of the given context and given offset. I.e. it's safe to assume that all symbols between the offset
   * identified by the given context and given offset have occupy one visual and logical column.
   *
   * @param context   context that identifies currently processed position
   * @param offset    nearest offset to the one identified by the given context that conforms to requirement that every symbol
   *                  between them increments offset, visual and logical position by one
   * @return          resulting dimension if it's located between the document position identified by the given context
   *                  and given offset if any; <code>null</code> otherwise
   */
  @Nullable
  T advance(ProcessingContext context, int offset);

  /**
   * Notifies current strategy that soft wrap is encountered during the processing. There are two ways to continue the processing then:
   * <pre>
   * <ul>
   *   <li>Target dimension is located after the given soft wrap;</li>
   *   <li>Target dimension is located within the given soft wrap bounds;</li>
   * </ul>
   * </pre>
   *
   * @param context     current processing context
   * @param softWrap    soft wrap encountered during the processing
   * @return            target document dimension if it's located within the bounds of the given soft wrap; <code>null</code> otherwise
   */
  @Nullable
  T processSoftWrap(ProcessingContext context, SoftWrap softWrap);

  /**
   * Notifies current strategy that collapsed fold region is encountered during the processing. There are two ways to
   * continue the processing then:
   * <pre>
   * <ul>
   *   <li>Target dimension is located after the given fold region;</li>
   *   <li>Target dimension is located within the given fold region bounds;</li>
   * </ul>
   * </pre>
   *
   * @param context       current processing context
   * @param foldRegion    collapsed fold region encountered during the processing
   * @return              target document dimension if it's located within the bounds of the given fold region;
   *                      <code>null</code> otherwise
   */
  @Nullable
  T processFoldRegion(ProcessingContext context, FoldRegion foldRegion);

  /**
   * Notifies current strategy that tabulation symbols is encountered during the processing. Tabulation symbols
   * have special treatment because they may occupy different number of visual and logical columns.
   * See {@link EditorUtil#nextTabStop(int, Editor)} for more details. So, there are two ways to continue the processing then:
   * <pre>
   * <ul>
   *   <li>Target dimension is located after the given tabulation symbol bounds;</li>
   *   <li>Target dimension is located within the given tabulation symbol bounds;</li>
   * </ul>
   * </pre>
   *
   * @param context       current processing context
   * @param tabData       document position for the active tabulation symbol encountered during the processing
   * @return              target document dimension if it's located within the bounds of the given fold region;
   *                      <code>null</code> otherwise
   */
  @Nullable
  T processTabulation(ProcessingContext context, TabData tabData);

  /**
   * This method is assumed to be called when there are no special symbols between the document position identified by the
   * given context and target dimension. E.g. this method may be called when we perform {@code visual -> logical} mapping
   * and there are no soft wraps, collapsed fold regions and tabulation symbols on a current visual line.
   *
   * @param context   current processing context that identifies active document position
   * @return          resulting dimension that is built on the basis of the given context and target anchor dimension
   */
  @NotNull
  T build(ProcessingContext context);
}
