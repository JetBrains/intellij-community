// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.editor.selection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.List;

public class GroovyLiteralSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    PsiElement parent = e.getParent();
    return isLiteral(e) || isLiteral(parent);
  }

  private static boolean isLiteral(PsiElement element) {
    return element instanceof GrListOrMap ||
           element instanceof GrArgumentLabel ||
           element instanceof GrLiteral && ((GrLiteral)element).isString();
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    if (e instanceof GrListOrMap) return result;

    assert result != null;

    int startOffset = -1;
    int endOffset = -1;
    final String text = e.getText();
    final int stringOffset = e.getTextOffset();
    final IElementType elementType = e.getNode().getElementType();
    if (elementType == GroovyTokenTypes.mGSTRING_CONTENT ||
        elementType == GroovyTokenTypes.mREGEX_CONTENT ||
        elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT) {
      int cur;
      int index = -1;
      while (true) {
        cur = text.indexOf('\n', index + 1);
        if (cur < 0 || cur + stringOffset > cursorOffset) break;
        index = cur;
      }
      if (index >= 0) {
        startOffset = stringOffset + index + 1;
      }

      index = text.indexOf('\n', cursorOffset - stringOffset);
      if (index >= 0) {
        endOffset = stringOffset + index + 1;
      }
    }

    if (startOffset >= 0 && endOffset >= 0) {
      result.add(new TextRange(startOffset, endOffset));
    }

    final String content = GrStringUtil.removeQuotes(text);

    String trimmedContent = content.trim();
    result.addAll(expandToWholeLine(editorText, TextRange.from(stringOffset + text.indexOf(trimmedContent), trimmedContent.length())));

    result.add(TextRange.from(stringOffset + text.indexOf(content), content.length()));

    return result;
  }
}