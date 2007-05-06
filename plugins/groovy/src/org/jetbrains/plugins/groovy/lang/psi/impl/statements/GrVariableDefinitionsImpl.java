/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifiers;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclarations;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 27.03.2007
 */
public class GrVariableDefinitionsImpl extends GroovyPsiElementImpl implements GrVariableDeclarations {
  public GrVariableDefinitionsImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Variable definitions";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    for (final GrVariableImpl variable : findChildrenByClass(GrVariableImpl.class)) {
      if (!processor.execute(variable, PsiSubstitutor.EMPTY)) return false;
    }

    return true;
  }

  @NotNull
  public GrModifiers getModifiersList() {
    GrModifiers modifiers = findChildByClass(GrModifiers.class);
    assert modifiers != null;
    return modifiers;
  }

  public GrVariable[] getVariables() {
    return findChildrenByClass(GrVariable.class);
  }

  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    return findChildByClass(GrTypeElement.class);
  }
}
