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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ide.KillRingTransferable;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Holds utility methods for {@link KillRingTransferable kill ring}-aware processing.
 */
public class KillRingUtil {

  private KillRingUtil() {
  }

  /**
   * Cuts target region from the given editor and puts it to the kill ring.
   * 
   * @param editor        target editor
   * @param start         start offset of the target text region within the given editor (inclusive)
   * @param end           end offset of the target text region within the given editor (exclusive)
   */
  public static void cut(@NotNull Editor editor, int start, int end) {
    copyToKillRing(editor, start, end, true);
    editor.getDocument().deleteString(start, end);
  }
  
  /**
   * Copies target region from the given offset to the kill ring, i.e. combines it with the previously
   * copied/cut adjacent text if necessary and puts to the clipboard.
   *
   * @param editor      target editor
   * @param startOffset start offset of the target region within the given editor
   * @param endOffset   end offset of the target region within the given editor
   * @param cut         flag that identifies if target text region will be cut from the given editor
   */
  public static void copyToKillRing(@NotNull final Editor editor, int startOffset, int endOffset, boolean cut) {
    Document document = editor.getDocument();
    String s = document.getCharsSequence().subSequence(startOffset, endOffset).toString();
    s = StringUtil.convertLineSeparators(s);
    CopyPasteManager.getInstance().setContents(new KillRingTransferable(s, document, startOffset, endOffset, cut));
    if (editor instanceof EditorEx) {
      EditorEx ex = (EditorEx)editor;
      if (ex.isStickySelection()) {
        ex.setStickySelection(false);
      }
    }
  }
}