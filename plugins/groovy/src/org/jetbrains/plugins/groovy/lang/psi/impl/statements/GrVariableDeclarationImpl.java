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
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 27.03.2007
 */
public class GrVariableDeclarationImpl extends GroovyPsiElementImpl implements GrVariableDeclaration {
  public GrVariableDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Variable definitions";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    for (final GrVariableImpl variable : findChildrenByClass(GrVariableImpl.class)) {
      if (!ResolveUtil.processElement(processor, variable)) return false;
    }

    return true;
  }

  public GrModifierList getModifierList() {
    return findChildByClass(GrModifierList.class);
  }

  public GrVariable[] getVariables() {
    return findChildrenByClass(GrVariable.class);
  }

  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    return findChildByClass(GrTypeElement.class);
  }
}
