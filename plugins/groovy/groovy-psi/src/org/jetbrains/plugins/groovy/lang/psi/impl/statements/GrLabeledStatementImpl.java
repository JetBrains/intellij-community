// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

/**
 * @author ilyas
 */
public class GrLabeledStatementImpl extends GroovyPsiElementImpl implements GrLabeledStatement {
  public GrLabeledStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitLabeledStatement(this);
  }

  public String toString() {
    return "Labeled statement";
  }

  @NotNull
  public String getLabelName() {
    return getName();
  }

  @Override
  @NotNull
  public PsiElement getLabel() {
    final PsiElement label = findChildByType(GroovyTokenTypes.mIDENT);
    assert label != null;
    return label;
  }

  @Override
  @Nullable
  public GrStatement getStatement() {
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

  @NotNull
  @Override
  public SearchScope getUseScope() {
    return new LocalSearchScope(this);
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    final PsiElement labelElement = getLabel();
    final PsiElement newLabel = GroovyPsiElementFactory.getInstance(getProject()).createReferenceNameFromText(name);
    labelElement.replace(newLabel);
    return this;
  }

  @NotNull
  @Override
  public String getName() {
    final PsiElement label = getLabel();
    return label.getText();
  }

  @NotNull
  @Override
  public PsiElement getNameIdentifierGroovy() {
    return getLabel();
  }

  @Nullable
  @Override
  public PsiElement getNameIdentifier() {
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
