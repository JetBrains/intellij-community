// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

public class GrLabeledStatementImpl extends GroovyPsiElementImpl implements GrLabeledStatement {
  public GrLabeledStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitLabeledStatement(this);
  }

  @Override
  public String toString() {
    return "Labeled statement";
  }

  public @NotNull String getLabelName() {
    return getName();
  }

  @Override
  public @NotNull PsiElement getLabel() {
    final PsiElement label = findChildByType(GroovyTokenTypes.mIDENT);
    assert label != null;
    return label;
  }

  @Override
  public @Nullable GrStatement getStatement() {
    return findChildByClass(GrStatement.class);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    GrStatement statement = getStatement();
    return statement == null || statement == lastParent || statement.processDeclarations(processor, state, lastParent, place);
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    return new LocalSearchScope(this);
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    final PsiElement labelElement = getLabel();
    final PsiElement newLabel = GroovyPsiElementFactory.getInstance(getProject()).createReferenceNameFromText(name);
    labelElement.replace(newLabel);
    return this;
  }

  @Override
  public @NotNull String getName() {
    final PsiElement label = getLabel();
    return label.getText();
  }

  @Override
  public @NotNull PsiElement getNameIdentifierGroovy() {
    return getLabel();
  }

  @Override
  public @Nullable PsiElement getNameIdentifier() {
    return getNameIdentifierGroovy();
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    GrStatement statement = getStatement();
    if (statement != null && child == statement.getNode()) {
      delete();
    }
    else {
      super.deleteChildInternal(child);
    }
  }
}
