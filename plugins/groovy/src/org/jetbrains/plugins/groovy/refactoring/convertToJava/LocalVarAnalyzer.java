/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Map;
import java.util.Set;

/**
 * @author Medvedev Max
 */
class LocalVarAnalyzer extends GroovyRecursiveElementVisitor {
  static class Result {
    private final Set<PsiVariable> toMakeFinal;
    private final Set<PsiVariable> toWrap;
    private final Map<PsiVariable, String> varToName;

    private Result(Set<PsiVariable> toMakeFinal, Set<PsiVariable> toWrap, Map<PsiVariable, String> varToName) {
      this.toMakeFinal = toMakeFinal;
      this.toWrap = toWrap;
      this.varToName = varToName;
    }

    boolean toWrap(PsiVariable variable) {
      return toWrap.contains(variable);
    }

    boolean toMakeFinal(PsiVariable variable) {
      return toMakeFinal.contains(variable);
    }

    String toVarName(PsiVariable variable) {
      return varToName.get(variable);
    }
  }


  public static Result searchForVarsToWrap(GroovyPsiElement root, Result analyzedVars, ExpressionContext context) {
    LocalVarAnalyzer visitor = new LocalVarAnalyzer();
    root.accept(visitor);

    Map<PsiVariable, String> varToName = analyzedVars == null ? new HashMap<>() : analyzedVars.varToName;
    Set<PsiVariable> toWrap = analyzedVars == null ? new HashSet<>() : analyzedVars.toWrap;
    Set<PsiVariable> toMakeFinal = analyzedVars == null ? new HashSet<>() : analyzedVars.toMakeFinal;
    for (PsiVariable v : visitor.touched) {
      if (visitor.rewritten.contains(v)) {
        toWrap.add(v);
        if (v instanceof PsiParameter) {
          varToName.put(v, GenerationUtil.suggestVarName(v.getType(), root, context));
        }
        else {
          varToName.put(v, v.getName());
        }
      }
      else {
        toMakeFinal.add(v);
        varToName.put(v, v.getName());
      }
    }
    return analyzedVars == null ? new Result(toMakeFinal, toWrap, varToName) : analyzedVars;
  }

  public static Result initialResult() {
    return new Result(new HashSet<>(), new HashSet<>(), new HashMap<>());
  }

  private final Set<PsiVariable> touched = new HashSet<>();
  private final Set<PsiVariable> rewritten = new HashSet<>();
  private final TObjectIntHashMap<PsiVariable> allVars = new TObjectIntHashMap<>();

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
    if (variable instanceof GrField) return;
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
      touched.add((PsiVariable)resolved);
    }
  }
}
