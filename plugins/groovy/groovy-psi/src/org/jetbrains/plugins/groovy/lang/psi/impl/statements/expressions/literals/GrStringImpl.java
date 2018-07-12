// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.List;

/**
 * @author ilyas
 */
public class GrStringImpl extends GrAbstractLiteral implements GrString {

  public GrStringImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Compound Gstring";
  }

  @Override
  public PsiType getType() {
    return getTypeByFQName(findChildByClass(GrStringInjection.class) != null ? GroovyCommonClassNames.GROOVY_LANG_GSTRING
                                                                             : CommonClassNames.JAVA_LANG_STRING);
  }

  @Override
  public boolean isPlainString() {
    return !getText().startsWith("\"\"\"");
  }

  @Override
  public GrStringInjection[] getInjections() {
    return findChildrenByClass(GrStringInjection.class);
  }

  @Override
  public String[] getTextParts() {
    List<PsiElement> parts = findChildrenByType(GroovyElementTypes.GSTRING_CONTENT);

    String[] result = new String[parts.size()];
    int i = 0;
    for (PsiElement part : parts) {
      result[i++] = part.getText();
    }
    return result;
  }

  @Override
  public GrStringContent[] getContents() {
    final List<PsiElement> parts = findChildrenByType(GroovyElementTypes.GSTRING_CONTENT);
    return parts.toArray(new GrStringContent[0]);
  }

  @Override
  public GroovyPsiElement[] getAllContentParts() {
    final List<PsiElement> result = findChildrenByType(TokenSets.GSTRING_CONTENT_PARTS);
    return result.toArray(GroovyPsiElement.EMPTY_ARRAY);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitGStringExpression(this);
  }

  @Override
  public Object getValue() {
    if (findChildByClass(GrStringInjection.class) != null) return null;

    final PsiElement fchild = getFirstChild();
    if (fchild == null) return null;

    final PsiElement content = fchild.getNextSibling();
    if (content == null || content.getNode().getElementType() != GroovyElementTypes.GSTRING_CONTENT) return null;

    final String text = content.getText();
    StringBuilder chars = new StringBuilder(text.length());
    boolean result = GrStringUtil.parseStringCharacters(text, chars, null);
    return result ? chars.toString() : null;
  }

  @Override
  public boolean isValidHost() {
    return false;
  }

  @Override
  public GrStringImpl updateText(@NotNull String text) {
    return this;
  }

  @NotNull
  @Override
  public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new GrLiteralEscaper(this);
  }
}
