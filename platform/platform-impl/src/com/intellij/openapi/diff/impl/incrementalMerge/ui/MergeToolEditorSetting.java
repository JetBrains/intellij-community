/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.incrementalMerge.ui;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;

/**
 * <p>
 *   Properties of the editors used in the merge tool. Most of them are customizable by the user.
 * </p>
 *
 * @author Kirill Likhodedov
 * @see MergeToolSettings
 */
enum MergeToolEditorSetting {
  WHITESPACES(false) {
    @Override
    protected void apply(Editor editor, boolean state) {
      editor.getSettings().setWhitespacesShown(state);
      editor.getComponent().repaint();
    }
  },
  LINE_NUMBERS(false) {
    @Override
    protected void apply(Editor editor, boolean state) {
      editor.getSettings().setLineNumbersShown(state);
      editor.getComponent().repaint();
    }
  },
  INDENT_LINES(false) {
    @Override
    protected void apply(Editor editor, boolean state) {
      editor.getSettings().setIndentGuidesShown(state);
      editor.getComponent().repaint();
    }
  },
  SOFT_WRAPS(false) {
    @Override
    protected void apply(Editor editor, boolean state) {
      editor.getSettings().setUseSoftWraps(state);
      if (editor instanceof EditorEx) {
        ((EditorEx)editor).reinitSettings();
      }
    }
  };

  private final boolean myDefault;

  MergeToolEditorSetting(boolean aDefault) {
    myDefault = aDefault;
  }

  protected abstract void apply(Editor editor, boolean state);

  boolean getDefault() {
    return myDefault;
  }

}
