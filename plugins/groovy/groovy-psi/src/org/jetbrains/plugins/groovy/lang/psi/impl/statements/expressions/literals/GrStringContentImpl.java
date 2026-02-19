// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import com.intellij.lang.ASTNode;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

/**
 * @author Max Medvedev
 */
public class GrStringContentImpl extends GroovyPsiElementImpl implements GrStringContent, PsiLanguageInjectionHost {
  public GrStringContentImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String getValue() {
    final String text = getText();
    StringBuilder chars = new StringBuilder(text.length());
    boolean result = GrStringUtil.parseStringCharacters(text, chars, null);
    return result ? chars.toString() : null;
  }

  @Override
  public boolean isValidHost() {
    return getValue() != null;
  }

  @Override
  public GrStringContentImpl updateText(@NotNull String text) {
    if (getFirstChild() != null) {
      getFirstChild().delete();
    }
    getNode().addLeaf(GroovyTokenTypes.mGSTRING_CONTENT, text, null);
    return this;
  }

  @Override
  public @NotNull LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new GrLiteralEscaper(this);
  }
}
