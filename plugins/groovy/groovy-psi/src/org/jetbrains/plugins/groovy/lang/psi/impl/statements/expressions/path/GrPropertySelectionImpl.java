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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrPropertySelection;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;

/**
 * @author ilyas
 */
public class GrPropertySelectionImpl extends GrExpressionImpl implements GrPropertySelection {
  private static final Logger LOG = Logger.getInstance(GrPropertySelectionImpl.class);

  public GrPropertySelectionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitPropertySelection(this);
  }

  public String toString() {
    return "Property selection";
  }

  @NotNull
  @Override
  public PsiElement getDotToken() {
    return findNotNullChildByType(TokenSets.DOTS);
  }

  @NotNull
  @Override
  public GrExpression getQualifier() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @NotNull
  @Override
  public PsiElement getReferenceNameElement() {
    final PsiElement last = getLastChild();
    LOG.assertTrue(last != null);
    return last;
  }

  @Nullable
  @Override
  public PsiType getType() {
    return null;
  }
}
