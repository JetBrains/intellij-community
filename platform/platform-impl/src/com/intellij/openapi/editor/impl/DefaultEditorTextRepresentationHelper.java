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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since Jul 27, 2010 4:06:27 PM
 */
public class DefaultEditorTextRepresentationHelper implements EditorTextRepresentationHelper {

  private final Editor myEditor;

  public DefaultEditorTextRepresentationHelper(Editor editor) {
    myEditor = editor;
  }

  @Override
  public int toVisualColumnSymbolsNumber(@NotNull CharSequence text, int start, int end, int x) {
    return EditorUtil.textWidthInColumns(myEditor, text, start, end, x);
  }

  @Override
  public int toVisualColumnSymbolsNumber(int width) {
    return EditorUtil.columnsNumber(width, EditorUtil.getSpaceWidth(Font.PLAIN, myEditor));
  }

  @Override
  public int textWidth(@NotNull CharSequence text, int start, int end, int x) {
    return EditorUtil.textWidth(myEditor, text, start, end, Font.PLAIN, x);
  }
}
