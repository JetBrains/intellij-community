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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTupleDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

import java.util.Arrays;

/**
 * @author Dmitry.Krasilschikov
 */
public class GrTupleDeclarationImpl extends GroovyPsiElementImpl implements GrTupleDeclaration {
  public GrTupleDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  public GrVariable[] getVariables() {
    return findChildrenByClass(GrVariable.class);
  }

  @Nullable
  public GrExpression getInitializerGroovy() {
    final PsiElement parent = getParent();
    if (parent == null || !(parent instanceof GrMultipleVariableDeclarationImpl)) return null;
    return ((GrMultipleVariableDeclarationImpl)parent).getInitializerGroovy();
  }

  public int getVariableNumber(@NotNull GrVariable variable) {
    return Arrays.asList(getChildren()).indexOf(variable);
  }

  public String toString() {
    return "Tuple";
  }
}
