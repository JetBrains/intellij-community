// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteralContainer;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

public class GrLiteralEscaper extends LiteralTextEscaper<GrLiteralContainer> {
  private int[] outSourceOffsets;

  public GrLiteralEscaper(GrLiteralContainer literal) {
    super(literal);
  }

  @Override
  public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    String subText = rangeInsideHost.substring(myHost.getText());
    outSourceOffsets = new int[subText.length() + 1];

    final IElementType elementType = myHost.getFirstChild().getNode().getElementType();
    if (GroovyTokenSets.STRING_LITERALS.contains(elementType) || elementType == GroovyTokenTypes.mGSTRING_CONTENT) {
      return GrStringUtil.parseStringCharacters(subText, outChars, outSourceOffsets);
    }
    else if (elementType == GroovyTokenTypes.mREGEX_LITERAL || elementType == GroovyTokenTypes.mREGEX_CONTENT) {
      return GrStringUtil.parseRegexCharacters(subText, outChars, outSourceOffsets, true);
    }
    else if (elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL || elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT) {
      return GrStringUtil.parseRegexCharacters(subText, outChars, outSourceOffsets, false);
    }
    else return false;
  }

  @Override
  public int getOffsetInHost(int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
    int result = offsetInDecoded < outSourceOffsets.length ? outSourceOffsets[offsetInDecoded] : -1;
    if (result == -1) return -1;
    return (result <= rangeInsideHost.getLength() ? result : rangeInsideHost.getLength()) + rangeInsideHost.getStartOffset();
  }

  @Override
  public boolean isOneLine() {
    final Object value = myHost.getValue();
    return value instanceof String && ((String)value).indexOf('\n') < 0;
  }
}
