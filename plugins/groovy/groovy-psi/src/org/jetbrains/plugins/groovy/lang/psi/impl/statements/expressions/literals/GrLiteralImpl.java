// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteralContainer;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author ilyas
 */
public class GrLiteralImpl extends GrAbstractLiteral implements GrLiteral, PsiLanguageInjectionHost {
  private static final Logger LOG = Logger.getInstance(GrLiteralImpl.class);

  public GrLiteralImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Literal";
  }

  @Override
  public PsiType getType() {
    IElementType elemType = getLiteralType(this);
    return TypesUtil.getPsiType(this, elemType);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitLiteralExpression(this);
  }

  @Override
  public Object getValue() {
    return getLiteralValue(getFirstChild());
  }

  public static Object getLiteralValue(PsiElement child) {
    IElementType elemType = child.getNode().getElementType();
    String text = child.getText();

    if (TokenSets.NUMBERS.contains(elemType)) {
      try {
        if (elemType == GroovyTokenTypes.mNUM_INT) {
          char lastChar = text.charAt(text.length() - 1);
          if (lastChar == 'i' || lastChar == 'I') {
            text = text.substring(0, text.length() - 1);
          }
          return PsiLiteralUtil.parseInteger(text);
        }
        else if (elemType == GroovyTokenTypes.mNUM_LONG) {
          return PsiLiteralUtil.parseLong(text);
        }
        else if (elemType == GroovyTokenTypes.mNUM_FLOAT) {
          return PsiLiteralUtil.parseFloat(text);
        }
        else if (elemType == GroovyTokenTypes.mNUM_DOUBLE) {
          return PsiLiteralUtil.parseDouble(text);
        }
        else if (elemType == GroovyTokenTypes.mNUM_BIG_INT) {
          return new BigInteger(text);
        }
        else if (elemType == GroovyTokenTypes.mNUM_BIG_DECIMAL) {
          return new BigDecimal(text);
        }
      }
      catch (NumberFormatException ignored) {
      }
    }

    else if (elemType == GroovyTokenTypes.kFALSE) {
      return Boolean.FALSE;
    }
    else if (elemType == GroovyTokenTypes.kTRUE) {
      return Boolean.TRUE;
    }
    else if (elemType == GroovyTokenTypes.mSTRING_LITERAL) {
      if (!text.startsWith("'")) return null;
      text = GrStringUtil.removeQuotes(text);
      StringBuilder chars = new StringBuilder(text.length());
      boolean result = GrStringUtil.parseStringCharacters(text, chars, null);
      return result ? chars.toString() : null;
    }
    else if (elemType == GroovyTokenTypes.mGSTRING_LITERAL) {
      if (!text.startsWith("\"")) return null;
      text = GrStringUtil.removeQuotes(text);
      StringBuilder chars = new StringBuilder(text.length());
      boolean result = GrStringUtil.parseStringCharacters(text, chars, null);
      return result ? chars.toString() : null;
    }
    else if (elemType == GroovyTokenTypes.mREGEX_LITERAL) {
      final PsiElement cchild = child.getFirstChild();
      if (cchild == null) return null;
      final PsiElement sibling = cchild.getNextSibling();
      if (sibling == null) return null;
      text = sibling.getText();
      final StringBuilder chars = new StringBuilder(text.length());
      boolean result = GrStringUtil.parseRegexCharacters(text, chars, null, true);
      return result ? chars.toString() : null;
    }
    else if (elemType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL) {
      final PsiElement cchild = child.getFirstChild();
      if (cchild == null) return null;
      final PsiElement sibling = cchild.getNextSibling();
      if (sibling == null) return null;
      text = sibling.getText();
      final StringBuilder chars = new StringBuilder(text.length());
      boolean result = GrStringUtil.parseRegexCharacters(text, chars, null, false);
      return result ? chars.toString() : null;
    } else if (elemType == GroovyTokenTypes.kNULL) {
      return ObjectUtils.NULL;
    }

    return null;
  }

  public static IElementType getLiteralType(GrLiteral literal) {
    PsiElement firstChild = literal.getFirstChild();
    assert firstChild != null;
    return firstChild.getNode().getElementType();
  }

  public boolean isStringLiteral() {
    PsiElement child = getFirstChild();
    if (child == null) return false;

    IElementType elementType = child.getNode().getElementType();
    return TokenSets.STRING_LITERAL_SET.contains(elementType);
  }

  @Override
  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);
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

  @Override
  public boolean isValidHost() {
    Object value = getValue();
    return value instanceof String && !((String)value).isEmpty();
  }

  @Override
  public GrLiteralImpl updateText(@NotNull final String text) {
    final GrExpression newExpr = GroovyPsiElementFactory.getInstance(getProject()).createExpressionFromText(text);
    LOG.assertTrue(newExpr instanceof GrLiteral, text);
    LOG.assertTrue(newExpr.getFirstChild() != null, text);
    final ASTNode valueNode = getNode().getFirstChildNode();
    getNode().replaceChild(valueNode, newExpr.getFirstChild().getNode());
    return this;
  }

  @Override
  @NotNull
  public LiteralTextEscaper<GrLiteralContainer> createLiteralTextEscaper() {
    return new GrLiteralEscaper(this);
  }
}
