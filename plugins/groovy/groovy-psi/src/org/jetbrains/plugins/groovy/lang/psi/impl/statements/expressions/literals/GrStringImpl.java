/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
    return parts.toArray(new GrStringContent[parts.size()]);
  }

  @Override
  public GroovyPsiElement[] getAllContentParts() {
    final List<PsiElement> result = findChildrenByType(TokenSets.GSTRING_CONTENT_PARTS);
    return result.toArray(new GroovyPsiElement[result.size()]);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
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
