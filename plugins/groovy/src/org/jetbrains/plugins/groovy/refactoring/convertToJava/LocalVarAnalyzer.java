// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiVariable;
import java.util.HashMap;
import java.util.HashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
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
final class LocalVarAnalyzer extends GroovyRecursiveElementVisitor {
  static final class Result {
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
  public void visitClosure(@NotNull GrClosableBlock closure) {
    grade++;
    super.visitClosure(closure);
    grade--;
  }

  @Override
  public void visitTypeDefinition(@NotNull GrTypeDefinition typeDefinition) {
    if (!(typeDefinition instanceof PsiAnonymousClass)) return;
    grade++;
    super.visitTypeDefinition(typeDefinition);
    grade--;
  }

  @Override
  public void visitVariable(@NotNull GrVariable variable) {
    super.visitVariable(variable);
    if (variable instanceof GrField) return;
    allVars.put(variable, grade);
  }

  @Override
  public void visitReferenceExpression(@NotNull GrReferenceExpression ref) {
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
