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

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitLabeledStatement(this);
  }

  public String toString() {
    return "Labeled statement";
  }

  @NotNull
  public String getLabelName() {
    return getName();
  }

  @NotNull
  public PsiElement getLabel() {
    final PsiElement label = findChildByType(GroovyTokenTypes.mIDENT);
    assert label != null;
    return label;
  }

  @Nullable
  public GrStatement getStatement() {
    return findChildByClass(GrStatement.class);
  }

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