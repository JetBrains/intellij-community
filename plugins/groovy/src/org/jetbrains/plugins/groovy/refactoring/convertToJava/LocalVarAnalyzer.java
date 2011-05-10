/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.Set;

/**
 * @author Medvedev Max
 */
class LocalVarAnalyzer extends GroovyRecursiveElementVisitor {
  static class Result {
    private final Set<GrVariable> toMakeFinal;
    private final Set<GrVariable> toWrap;

    private Result(Set<GrVariable> toMakeFinal, Set<GrVariable> toWrap) {
      this.toMakeFinal = toMakeFinal;
      this.toWrap = toWrap;
    }

    boolean toWrap(GrVariable variable) {
      return toWrap.contains(variable);
    }
    boolean toMakeFinal(GrVariable variable) {
      return toMakeFinal.contains(variable);
    }
  }


  public static Result searchForVarsToWrap(GroovyPsiElement root, Result analyzedVars) {
    LocalVarAnalyzer visitor = new LocalVarAnalyzer();
    root.accept(visitor);

    Set<GrVariable> toWrap = analyzedVars == null ? new HashSet<GrVariable>() : analyzedVars.toWrap;
    Set<GrVariable> toMakeFinal = analyzedVars == null ? new HashSet<GrVariable>() : analyzedVars.toMakeFinal;
    for (GrVariable v : visitor.touched) {
      if (visitor.rewritten.contains(v)) {
        toWrap.add(v);
      }
      else {
        toMakeFinal.add(v);
      }
    }
    return analyzedVars == null ? new Result(toMakeFinal, toWrap) : analyzedVars;
  }

  public static Result initialResult() {
    return new Result(new HashSet<GrVariable>(), new HashSet<GrVariable>());
  }

  private final Set<GrVariable> touched = new HashSet<GrVariable>();
  private final Set<GrVariable> rewritten = new HashSet<GrVariable>();
  private final TObjectIntHashMap<GrVariable> allVars = new TObjectIntHashMap<GrVariable>();

  private int grade = 0;

  private LocalVarAnalyzer() {
  }


  @Override
  public void visitClosure(GrClosableBlock closure) {
    grade++;
    super.visitClosure(closure);
    grade--;
  }

  @Override
  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    if (!(typeDefinition instanceof PsiAnonymousClass)) return;
    grade++;
    super.visitTypeDefinition(typeDefinition);
    grade--;
  }

  @Override
  public void visitVariable(GrVariable variable) {
    super.visitVariable(variable);
    if (!GroovyRefactoringUtil.isLocalVariable(variable)) return;
    allVars.put(variable, grade);
  }

  @Override
  public void visitReferenceExpression(GrReferenceExpression ref) {
    super.visitReferenceExpression(ref);
    PsiElement resolved = ref.resolve();
    if (!allVars.contains(resolved)) return;
    GrVariable var = (GrVariable)resolved;

    if (PsiUtil.isAccessedForWriting(ref)) {
      rewritten.add(var);
    }

    if (allVars.get(var) < grade) {
      touched.add((GrVariable)resolved);
    }
  }


}
