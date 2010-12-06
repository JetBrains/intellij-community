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

import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * Defines callback that receives information about visual size change of component that represents data in lines-based mode.
 * 
 * @author Denis Zhdanov
 * @since 12/6/10 11:04 AM
 */
public interface VisualSizeChangeListener {

  /**
   * Notifies about new visual widths of the target logical lines.
   * <p/>
   * Assumes to be called on document change events.
   * <p/>
   * <b>Note:</b> it is assumed that 
   * 
   * @param startLine     logical line that contains start offset of the changed region
   * @param oldEndLine    logical line that contained end offset of the changed region
   * @param newEndLine    logical line that contains end offset of the changed region
   * @param lineWidths    container of {@code 'logical line number -> line width in pixels} mappings
   */
  void onLineWidthsChange(int startLine, int oldEndLine, int newEndLine, @NotNull TIntIntHashMap lineWidths);
}
