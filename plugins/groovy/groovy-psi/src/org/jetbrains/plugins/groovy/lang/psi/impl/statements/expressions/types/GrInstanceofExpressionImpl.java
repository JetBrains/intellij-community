// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrInstanceOfExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_INSTANCEOF;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_NOT_INSTANCEOF;

/**
 * @author ven
 */
public class GrInstanceofExpressionImpl extends GrExpressionImpl implements GrInstanceOfExpression {

  private static final TokenSet INSTANCEOF_TOKENS = TokenSet.create(KW_INSTANCEOF, T_NOT_INSTANCEOF);

  public GrInstanceofExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitInstanceofExpression(this);
  }

  @Override
  public String toString() {
    return "Instanceof expression";
  }

  @Override
  public PsiType getType() {
    return getTypeByFQName(CommonClassNames.JAVA_LANG_BOOLEAN);
  }

  @Override
  @Nullable
  public GrTypeElement getTypeElement() {
    return findChildByClass(GrTypeElement.class);
  }

  @Override
  public @NotNull PsiElement getOperationToken() {
    return findNotNullChildByType(INSTANCEOF_TOKENS);
  }

  @Override
  @NotNull
  public GrExpression getOperand() {
    return findNotNullChildByClass(GrExpression.class);
  }
}
