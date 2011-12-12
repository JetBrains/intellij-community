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
package org.jetbrains.plugins.groovy.lang.editor;

import com.intellij.codeInsight.editorActions.StringLiteralCopyPasteProcessor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 * @author peter
 */
public class GroovyLiteralCopyPasteProcessor extends StringLiteralCopyPasteProcessor {

  @Override
  protected boolean isCharLiteral(@NotNull PsiElement token) {
    return false;
  }

  @Override
  protected boolean isStringLiteral(@NotNull PsiElement token) {
    ASTNode node = token.getNode();
    return node != null && TokenSets.STRING_LITERALS.contains(node.getElementType());
  }

  @Nullable
  protected PsiElement findLiteralTokenType(PsiFile file, int selectionStart, int selectionEnd) {
    PsiElement elementAtSelectionStart = file.findElementAt(selectionStart);
    if (elementAtSelectionStart == null) {
      return null;
    }
    IElementType elementType = elementAtSelectionStart.getNode().getElementType();
    if ((elementType == mREGEX_END || elementType == mDOLLAR_SLASH_REGEX_END || elementType == mGSTRING_END) &&
        elementAtSelectionStart.getTextOffset() == selectionStart) {
      elementAtSelectionStart = elementAtSelectionStart.getPrevSibling();
      if (elementAtSelectionStart == null) return null;
      elementType = elementAtSelectionStart.getNode().getElementType();
    }

    if (!isStringLiteral(elementAtSelectionStart) && !isCharLiteral(elementAtSelectionStart)) {
      return null;
    }

    if (elementAtSelectionStart.getTextRange().getEndOffset() < selectionEnd) {
      final PsiElement elementAtSelectionEnd = file.findElementAt(selectionEnd);
      if (elementAtSelectionEnd == null) {
        return null;
      }
      if (elementAtSelectionEnd.getNode().getElementType() == elementType &&
          elementAtSelectionEnd.getTextRange().getStartOffset() < selectionEnd) {
        return elementAtSelectionStart;
      }
    }

    final TextRange textRange = elementAtSelectionStart.getTextRange();

    //content elements don't have quotes, so they are shorter than whole string literals
    if (elementType == mREGEX_CONTENT || elementType == mGSTRING_CONTENT || elementType == mDOLLAR_SLASH_REGEX_CONTENT) {
      selectionStart++;
      selectionEnd--;
    }
    if (selectionStart <= textRange.getStartOffset() || selectionEnd >= textRange.getEndOffset()) {
      return null;
    }
    return elementAtSelectionStart;
  }


  @Override
  protected String getLineBreaker(PsiElement token) {
    final String text = token.getText();
    if (text.contains("'''") || text.contains("\"\"\"")) {
      return "\n";
    }

    final IElementType type = token.getNode().getElementType();
    if (type == mGSTRING_LITERAL || type == mGSTRING_CONTENT) {
      return super.getLineBreaker(token);
    }
    if (type == mSTRING_LITERAL) {
      return super.getLineBreaker(token).replace('"', '\'');
    }

    return "\n";

  }

  @NotNull
  @Override
  protected String escapeCharCharacters(@NotNull String s, @NotNull PsiElement token, boolean escapeSlashes) {
    IElementType tokenType = token.getNode().getElementType();

    if (tokenType == mREGEX_CONTENT || tokenType == mDOLLAR_SLASH_REGEX_CONTENT) {
      if (escapeSlashes) {
        return StringUtil.escapeSlashes(s);
      }
      else {
        return s;
      }
    }

    String chars;
    if (tokenType == mGSTRING_CONTENT || tokenType == mGSTRING_LITERAL) {
      if (token.getText().contains("\"\"\"")) {
        chars = "$";
      }
      else {
        chars = "\"$";
      }
    }
    else if (tokenType == mSTRING_LITERAL && !token.getText().contains("'''")) {
      chars = "'";
    }
    else {
      chars = "";
    }

    StringBuilder buffer = new StringBuilder();
    StringUtil.escapeStringCharacters(s.length(), s, chars, escapeSlashes, buffer);
    return buffer.toString();
  }

  @NotNull
  @Override
  protected String unescape(String text, PsiElement token) {
    final IElementType tokenType = token.getNode().getElementType();

    if (tokenType == mREGEX_CONTENT || tokenType == mDOLLAR_SLASH_REGEX_CONTENT) {
      return StringUtil.unescapeSlashes(text);
    }

    return super.unescape(text, token);
  }

}
