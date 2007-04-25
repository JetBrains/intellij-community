/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports;

import com.intellij.lang.ASTNode;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author Ilya.Sergey
 */
public class GrImportStatementImpl extends GroovyPsiElementImpl implements GrImportStatement {

  public GrImportStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Import expression";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor psiScopeProcessor, @NotNull PsiSubstitutor substitutor, PsiElement psiElement, @NotNull PsiElement psiElement1) {
    //todo
    return true;
  }
}
