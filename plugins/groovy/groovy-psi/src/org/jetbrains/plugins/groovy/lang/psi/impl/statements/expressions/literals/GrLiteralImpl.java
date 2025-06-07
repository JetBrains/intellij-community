// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.LiteralUtilKt;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;

public class GrLiteralImpl extends GrAbstractLiteral implements GrLiteral, PsiLanguageInjectionHost {
  private static final Logger LOG = Logger.getInstance(GrLiteralImpl.class);

  public GrLiteralImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "Literal";
  }

  @Override
  public PsiType getType() {
    return LiteralUtilKt.getLiteralType(this);
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
          return LiteralUtilKt.parseInteger(text);
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
          return new BigInteger(text.substring(0, text.length() - 1)); // g or G suffix
        }
        else if (elemType == GroovyTokenTypes.mNUM_BIG_DECIMAL) {
          char lastChar = text.charAt(text.length() - 1);
          if (lastChar == 'g' || lastChar == 'G') {
            return new BigDecimal(text.substring(0, text.length() - 1));
          }
          else {
            return new BigDecimal(text);
          }
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
    else if (elemType == STRING_SQ || elemType == STRING_TSQ) {
      if (!text.startsWith("'")) return null;
      text = GrStringUtil.removeQuotes(text);
      StringBuilder chars = new StringBuilder(text.length());
      boolean result = GrStringUtil.parseStringCharacters(text, chars, null);
      return result ? chars.toString() : null;
    }
    else if (elemType == STRING_DQ || elemType == STRING_TDQ) {
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
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);
  }

  @Override
  public @Nullable PsiReference getReference() {
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
  public GrLiteralImpl updateText(final @NotNull String text) {
    final GrExpression newExpr = GroovyPsiElementFactory.getInstance(getProject()).createExpressionFromText(text);
    LOG.assertTrue(newExpr instanceof GrLiteral, text);
    LOG.assertTrue(newExpr.getFirstChild() != null, text);
    final ASTNode valueNode = getNode().getFirstChildNode();
    getNode().replaceChild(valueNode, newExpr.getFirstChild().getNode());
    return this;
  }

  @Override
  public @NotNull LiteralTextEscaper<GrLiteralContainer> createLiteralTextEscaper() {
    return new GrLiteralEscaper(this);
  }
}
