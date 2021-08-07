// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.EditorBundle;

public enum TabCharacterPaintMode {
  LONG_ARROW {
    @Override
    public String toString() {
      return EditorBundle.message("radio.editor.tab.long.arrow");
    }
  },
  ARROW {
    @Override
    public String toString() {
      return EditorBundle.message("radio.editor.tab.arrow");
    }
  },
  HORIZONTAL_LINE {
    @Override
    public String toString() {
      return EditorBundle.message("radio.editor.tab.horizontal.line");
    }
  }
}
