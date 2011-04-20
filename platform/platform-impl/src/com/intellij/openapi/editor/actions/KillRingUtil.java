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
   * Cuts target region from the given document and puts it to the kill ring.
   * 
   * @param document      target document
   * @param start         start offset of the target text region within the given document (inclusive)
   * @param end           end offset of the target text region within the given document (exclusive)
   */
  public static void cut(@NotNull Document document, int start, int end) {
    copyToKillRing(document, start, end, true);
    document.deleteString(start, end);
  }
  
  /**
   * Copies target region from the given offset to the kill ring, i.e. combines it with the previously
   * copied/cut adjacent text if necessary and puts to the clipboard.
   *
   * @param document    target document
   * @param startOffset start offset of the target region within the given document
   * @param endOffset   end offset of the target region within the given document
   * @param cut         flag that identifies if target text region will be cut from the given document
   */
  public static void copyToKillRing(@NotNull final Document document, int startOffset, int endOffset, boolean cut) {
    String s = document.getCharsSequence().subSequence(startOffset, endOffset).toString();
    s = StringUtil.convertLineSeparators(s);
    CopyPasteManager.getInstance().setContents(new KillRingTransferable(s, document, startOffset, endOffset, cut));
  }
}