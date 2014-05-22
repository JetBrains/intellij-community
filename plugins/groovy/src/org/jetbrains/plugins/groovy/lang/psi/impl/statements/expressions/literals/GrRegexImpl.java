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
  public void accept(GroovyElementVisitor visitor) {
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

