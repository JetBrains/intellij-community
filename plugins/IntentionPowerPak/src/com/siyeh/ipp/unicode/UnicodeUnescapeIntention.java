/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ipp.unicode;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementEditorPredicate;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnicodeUnescapeIntention extends Intention {

  @Override
  protected void processIntention(@NotNull PsiElement element) {}

  @Override
  protected void processIntention(Editor editor, @NotNull PsiElement element) {
    final CaretModel caretModel = editor.getCaretModel();
    final Document document = editor.getDocument();
    int offset = caretModel.getOffset();
    if (offset == document.getTextLength() - 1) {
      offset--;
    }
    String text = document.getText(new TextRange(offset, offset + 1));
    while (!text.equals("\\")) {
      offset--;
      text = document.getText(new TextRange(offset, offset + 1));
    }
    text = document.getText(new TextRange(offset, offset + 6));
    final int c = Integer.parseInt(text.substring(2), 16);
    document.replaceString(offset, offset + 6, String.valueOf((char)c));
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new UnicodeEscapePredicate();
  }

  private static class UnicodeEscapePredicate extends PsiElementEditorPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element, @Nullable Editor editor) {
      if (editor == null) {
        return false;
      }
      final CaretModel caretModel = editor.getCaretModel();
      final Document document = editor.getDocument();
      final int offset = caretModel.getOffset();
      final String text = document.getText(new TextRange(Math.max(offset - 6, 0), Math.min(offset + 6, document.getTextLength())));
      final int textLength = text.length();
      final int max = Math.min(7, textLength);
      for (int i = 0; i < max; i++) {
        if (i + 5 >= textLength) {
          //return false;
        }
        if (text.charAt(i) == '\\' &&
            text.charAt(i + 1) == 'u' &&
            StringUtil.isHexDigit(text.charAt(i + 2)) &&
            StringUtil.isHexDigit(text.charAt(i + 3)) &&
            StringUtil.isHexDigit(text.charAt(i + 4)) &&
            StringUtil.isHexDigit(text.charAt(i + 5))) {
          return true;
        }
      }
      return false;
    }
  }
}
