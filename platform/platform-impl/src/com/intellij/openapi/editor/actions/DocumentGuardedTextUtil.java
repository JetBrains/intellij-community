// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import org.jetbrains.annotations.NotNull;

public final class DocumentGuardedTextUtil {
  private DocumentGuardedTextUtil() { }

  /**
   * try to replace delete operation with equivalent if original operation forbidden due to guarded block
   * In text "\na\n" with guarded last symbol "\n" deleteString(1, 3) will be replaced with deleteString(0, 2).
   * equivalent replace are searched to the left from original
   */
  public static void deleteString(@NotNull Document document, int startOffset, int endOffset) {
    try {
      document.deleteString(startOffset, endOffset);
    }
    catch (ReadOnlyFragmentModificationException ex) {
      RangeMarker block = ex.getGuardedBlock();
      if (block.getEndOffset() < endOffset) {
        throw ex;
      }

      CharSequence blockPrefix = document.getImmutableCharSequence().subSequence(block.getStartOffset(), endOffset);
      CharSequence textBefore =
        document.getImmutableCharSequence().subSequence(Math.max(0, startOffset - blockPrefix.length()), startOffset);

      if (blockPrefix.toString().equals(textBefore.toString())) {
        deleteString(document, startOffset - blockPrefix.length(), endOffset - blockPrefix.length());
      }
      else {
        throw ex;
      }
    }
  }

  /**
   * try to replace insert operation with equivalent if original operation forbidden due to guarded block
   * for guarded text "\nx" insertString(1, "a\n") will be replaced with insertString(0, "\na").
   * equivalent insert are searched to the left from original
   */
  public static void insertString(@NotNull Document document, int offset, @NotNull CharSequence s) {
    try {
      document.insertString(offset, s);
    }
    catch (ReadOnlyFragmentModificationException ex) {
      String blockPrefix = document.getImmutableCharSequence().subSequence(ex.getGuardedBlock().getStartOffset(), offset).toString();
      if (s.toString().endsWith(blockPrefix)) {
        int newOffset = offset - blockPrefix.length();
        String newString = blockPrefix + s.subSequence(0, s.length() - blockPrefix.length());
        insertString(document, newOffset, newString);
      }
      else {
        throw ex;
      }
    }
  }
}
