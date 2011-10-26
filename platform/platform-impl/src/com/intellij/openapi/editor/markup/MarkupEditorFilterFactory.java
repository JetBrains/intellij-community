/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.editor.Editor;

/**
 * @author max
 */
public class MarkupEditorFilterFactory {
  private static final MarkupEditorFilter IS_DIFF_FILTER = DiffManager.getInstance().getDiffEditorFilter();
  private static final MarkupEditorFilter NOT_DIFF_FILTER = createNotFilter(IS_DIFF_FILTER);

  public static MarkupEditorFilter createNotFilter(final MarkupEditorFilter filter) {
    return new MarkupEditorFilter() {
      @Override
      public boolean avaliableIn(Editor editor) {
        return !filter.avaliableIn(editor);
      }
    };
  }

  public static MarkupEditorFilter createIsDiffFilter() {
    return IS_DIFF_FILTER;
  }

  public static MarkupEditorFilter createIsNotDiffFilter() {
    return NOT_DIFF_FILTER;
  }
}
