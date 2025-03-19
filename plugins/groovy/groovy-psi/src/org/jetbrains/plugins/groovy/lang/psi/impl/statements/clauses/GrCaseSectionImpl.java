// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GrExpressionList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.util.List;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.processLocals;

public class GrCaseSectionImpl extends GroovyPsiElementImpl implements GrCaseSection {
  public GrCaseSectionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitCaseSection(this);
  }

  @Override
  public String toString() {
    return "Case section";
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return processLocals(this, processor, state, lastParent, place);
  }

  @Override
  public void removeVariable(GrVariable variable) {
    PsiImplUtil.removeVariable(variable);
  }

  @Override
  public GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException {
    GrStatement statement = addStatementBefore(declaration, anchor);
    assert statement instanceof GrVariableDeclaration;
    return ((GrVariableDeclaration) statement);
  }

  @Override
  public GrExpression @NotNull [] getExpressions() {
    final List<GrExpressionList> labels = findChildrenByType(GroovyElementTypes.EXPRESSION_LIST);
    if (labels.isEmpty() || labels.get(0) == null) {
      return GrExpression.EMPTY_ARRAY;
    } else {
      return labels.get(0).getExpressions().toArray(GrExpression.EMPTY_ARRAY);
    }
  }

  @Override
  public boolean isDefault() {
    PsiElement label = getFirstChild();
    return org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_DEFAULT.equals(label.getNode().getElementType());
  }

  @Override
  public @Nullable PsiElement getArrow() {
    return findChildByType(org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_ARROW);
  }

  @Override
  public @Nullable PsiElement getColon() {
    return findChildByType(org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_COLON);
  }

  @Override
  public GrStatement @NotNull [] getStatements() {
    return PsiImplUtil.getStatements(this);
  }

  @Override
  public @NotNull GrStatement addStatementBefore(@NotNull GrStatement element, @Nullable GrStatement anchor) throws IncorrectOperationException {
    ASTNode elemNode = element.copy().getNode();
    assert elemNode != null;
    final ASTNode anchorNode = anchor != null ? anchor.getNode() : null;
    getNode().addChild(elemNode, anchorNode);
    if (mayUseNewLinesAsSeparators()) {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", anchorNode);
    }
    else {
      getNode().addLeaf(GroovyTokenTypes.mSEMI, ";", anchorNode);
    }
    return (GrStatement)elemNode.getPsi();
  }

  private boolean mayUseNewLinesAsSeparators() {
    PsiElement parent = this;
    while (parent != null) {
      if (parent instanceof GrString grString) {
        return !grString.isPlainString();
      }
      parent = parent.getParent();
    }
    return true;
  }


}
