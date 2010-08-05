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

/**
 * Strategy interface for various utility methods used for representing document text at the editor.
 * <p/>
 * The main idea of this interface is to encapsulate that logic in order to allow relief unit testing.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * <p/>
 * <b>Note:</b> it's assumed that values provided by this service are not cached for further use because it's not guaranteed
 * that the same values are returned on subsequent calls (due to document change, editor config change etc).
 *
 * @author Denis Zhdanov
 * @since Jul 27, 2010 3:56:54 PM
 */
public interface EditorTextRepresentationHelper {

  /**
   * Allows to answer how many visual columns is necessary for representing target fragment of the given text assuming
   * that it belongs to the single visual line and should be shown at given <code>'x'</code> offset from the visual line start.
   *
   * @param text    target text holder
   * @param start   start offset of the target text sub-sequence (inclusive)
   * @param end     end offset of the target text sub-sequence (exclusive)
   * @param x       <code>'x'</code> offset from the visual line start
   * @return        number of visual columns necessary for the target text sub-sequence representation
   */
  int toVisualColumnSymbolsNumber(@NotNull CharSequence text, int start, int end, int x);

  /**
   * Allows to retrieve width (in pixels) necessary to represent given region (<code>[start; end)</code>) starting
   * at the given <code>'x'</code> offset from visual line start using given font type.
   * <p/>
   * <b>Note:</b> target region is allows to contain line feeds, the width is calculated as a difference between <code>'x'</code>
   * coordinates of the last and first symbols.
   *
   * @param text        target text holder
   * @param start       start offset of the target text sub-sequence (inclusive)
   * @param end         end offset of the target text sub-sequence (exclusive)
   * @param fontType    font type used for the given substring representation
   * @param x           <code>'x'</code> offset from the visual line start
   * @return            width in pixels necessary for the target text sub-sequence representation
   */
  int textWidth(@NotNull CharSequence text, int start, int end, int fontType, int x);
}
