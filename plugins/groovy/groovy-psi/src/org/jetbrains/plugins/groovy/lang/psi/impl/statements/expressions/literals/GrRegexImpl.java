// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import com.intellij.lang.ASTNode;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrRegex;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.List;

/**
 * @author ilyas
 */
public class GrRegexImpl extends GrStringImpl implements GrRegex {

  public GrRegexImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Compound regular expression";
  }

  @Override
  public boolean isPlainString() {
    return false;
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitRegexExpression(this);
  }

  @Override
  public Object getValue() {
    if (getInjections().length > 0) return null;

    PsiElement child = getFirstChild();
    if (child == null) return null;

    child = child.getNextSibling();
    if (child == null ||
        child.getNode().getElementType() != GroovyTokenTypes.mREGEX_CONTENT &&
        child.getNode().getElementType() != GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT) {
      return null;
    }

    final StringBuilder chars = new StringBuilder();
    final boolean isDollarSlash = child.getNode().getElementType() == GroovyTokenTypes.mREGEX_CONTENT;
    GrStringUtil.parseRegexCharacters(child.getText(), chars, null, isDollarSlash);
    return chars.toString();
  }

  @Override
  public String[] getTextParts() {
    List<PsiElement> parts = findChildrenByType(GroovyTokenTypes.mREGEX_CONTENT);

    String[] result = new String[parts.size()];
    int i = 0;
    for (PsiElement part : parts) {
      result[i++] = part.getText();
    }
    return result;
  }

  @NotNull
  @Override
  public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new GrLiteralEscaper(this);
  }
}

