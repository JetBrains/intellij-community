/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 * @author ilyas
 */
public class GrLiteralImpl extends GrExpressionImpl implements GrLiteral, PsiLanguageInjectionHost {

  public GrLiteralImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Literal";
  }

  public PsiType getType() {
    IElementType elemType = getFirstChild().getNode().getElementType();
    return TypesUtil.getPsiType(this, elemType);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitLiteralExpression(this);
  }

  public Object getValue() {
    final PsiElement child = getFirstChild();
    IElementType elemType = child.getNode().getElementType();
    String text = child.getText();
    if (elemType == mNUM_INT) {
      try {
        return Integer.parseInt(text);
      } catch (NumberFormatException e) {
      }
    } else if (elemType == mNUM_LONG) {
      try {
        return Long.parseLong(text);
      } catch (NumberFormatException e) {
      }
    } else if (elemType == mNUM_FLOAT) {
      try {
        return Float.parseFloat(text);
      } catch (NumberFormatException e) {
      }
    } else if (elemType == mNUM_DOUBLE) {
      try {
        return Double.parseDouble(text);
      } catch (NumberFormatException e) {
      }
    } else if (elemType == mNUM_BIG_INT) {
      try {
        return new BigInteger(text);
      } catch (NumberFormatException e) {
      }
    } else if (elemType == mNUM_BIG_DECIMAL) {
      try {
        return new BigDecimal(text);
      } catch (NumberFormatException e) {
      }
    } else if (elemType == kFALSE) {
      return Boolean.FALSE;
    } else if (elemType == kTRUE) {
      return Boolean.TRUE;
    } else if (elemType == mSTRING_LITERAL) {
      if (!text.startsWith("'")) return null;
      text = text.substring(1);
      if (text.endsWith("'")) {
        text = text.substring(0, text.length() - 1);
      }
      return StringUtil.unescapeStringCharacters(text);
    } else if (elemType == mGSTRING_LITERAL) {
      if (!text.startsWith("\"")) return null;
      if (text.startsWith("\"\"\"")) {
        text = StringUtil.trimEnd(text.substring(3), "\"\"\"");
      } else {
        text = StringUtil.trimEnd(text.substring(1), "\"");
      }
      return StringUtil.unescapeStringCharacters(text);
    }
    return null; //todo
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, GrLiteral.class);
  }

  @Nullable
  @Override
  public PsiReference getReference() {
    final PsiReference[] references = getReferences();
    if (references.length == 1) {
      return references[0];
    }
    if (references.length > 1) {
      return new PsiMultiReference(references, this);
    }
    return null;
  }

  public List<Pair<PsiElement, TextRange>> getInjectedPsi() {
    if (!(getValue() instanceof String)) return null;
    return InjectedLanguageUtil.getInjectedPsiFiles(this);
  }

  public void processInjectedPsi(@NotNull final InjectedPsiVisitor visitor) {
    InjectedLanguageUtil.enumerate(this, visitor);
  }

  public PsiLanguageInjectionHost updateText(@NotNull final String text) {
    final ASTNode valueNode = getNode().getFirstChildNode();
    assert valueNode instanceof LeafElement;
    ((LeafElement)valueNode).replaceWithText(text);
    return this;
  }

  @NotNull
  public LiteralTextEscaper<GrLiteralImpl> createLiteralTextEscaper() {
    return new GrLiteralEscaper(this);
  }
}