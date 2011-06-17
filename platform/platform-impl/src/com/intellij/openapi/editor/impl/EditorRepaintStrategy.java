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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines contract for the strategy that may adjust/control editor's repainting.
 * 
 * @author Denis Zhdanov
 * @since 6/17/11 11:04 AM
 */
public interface EditorRepaintStrategy {

  ExtensionPointName<EditorRepaintStrategy> EP_NAME = ExtensionPointName.create("com.intellij.editorRepaintStrategy");
  
  /**
   * Asks current strategy to adjust (if necessary) target document region repaint request received from the given editor's highlighter.
   *  
   * @param editor       target editor
   * @param startOffset  start offset of the document text range requested to be repainted (inclusive)
   * @param endOffset    end offset of the document text range requested to be repainted (exclusive)
   * @return             actual text range to repaint (if any); <code>null</code> as an indication that no further processing
   *                     for the current repaint request should be performed
   */
  @Nullable
  TextRange adjustHighlighterRegion(@NotNull EditorEx editor, int startOffset, int endOffset);
}
