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

  @NotNull
  @Override
  public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new GrLiteralEscaper(this);
  }
}
