// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.EditorBundle;

public enum CopiedFromEmptySelectionPasteMode {
  ENTIRE_LINE_ABOVE_CARET {
    @Override
    public String toString() {
      return EditorBundle.message("combobox.editor.paste.line.copied.from.empty.selection.entire.line.above.caret");
    }
  },
  AT_CARET {
    @Override
    public String toString() {
      return EditorBundle.message("combobox.editor.paste.line.copied.from.empty.selection.at.caret.position");
    }
  },
  TRIM_IF_MIDDLE_LINE {
    @Override
    public String toString() {
      return EditorBundle.message("combobox.editor.paste.line.copied.from.empty.selection.trim.if.middle.line");
    }
  }
}
