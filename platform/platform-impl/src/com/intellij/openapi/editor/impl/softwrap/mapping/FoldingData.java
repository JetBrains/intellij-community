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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;

/**
 * Caches information about number of logical columns inside the collapsed single line folding.
 */
class FoldingData {

  int widthInColumns = -1;
  int startX;
  private final Editor myEditor;
  private final EditorTextRepresentationHelper myRepresentationHelper;
  private final FoldRegion myFoldRegion;

  FoldingData(FoldRegion foldRegion, int startX, EditorTextRepresentationHelper representationHelper, Editor editor) {
    myFoldRegion = foldRegion;
    this.startX = startX;
    myRepresentationHelper = representationHelper;
    myEditor = editor;
  }

  public int getCollapsedSymbolsWidthInColumns() {
    if (widthInColumns < 0) {
      Document document = myEditor.getDocument();
      widthInColumns = myRepresentationHelper.toVisualColumnSymbolsNumber(
        document.getCharsSequence(), myFoldRegion.getStartOffset(), myFoldRegion.getEndOffset(), startX
      );
    }

    return widthInColumns;
  }
}
