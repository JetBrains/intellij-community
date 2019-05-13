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
package org.jetbrains.plugins.groovy.codeInspection.dataflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyLocalInspectionBase;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrModifierFix;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Collection;
import java.util.List;

public class GroovyVariableCanBeFinalInspection extends GroovyLocalInspectionBase {

  private static final Function<ProblemDescriptor, PsiModifierList> ID_MODIFIER_LIST_PROVIDER =
    descriptor -> {
      final PsiElement identifier = descriptor.getPsiElement();
      final PsiVariable variable = PsiTreeUtil.getParentOfType(identifier, PsiVariable.class);
      return variable == null ? null : variable.getModifierList();
    };

  private static void process(@NotNull GrControlFlowOwner owner, @NotNull GrVariable variable, @NotNull ProblemsHolder problemsHolder) {
    if (variable.hasModifierProperty(PsiModifier.FINAL)) return;
    if (!checkVariableDeclaredInsideScope(owner, variable)) return;
    if (checkVariableAssignedInsideClosureOrAnonymous(owner, variable)) return;

    final boolean isParameterTooltip = variable instanceof GrParameter && (
      ((GrParameter)variable).getDeclarationScope() instanceof GrMethod ||
      ((GrParameter)variable).getDeclarationScope() instanceof GrClosableBlock
    );

    final String tooltip = GroovyInspectionBundle.message(
      isParameterTooltip ? "parameter.can.be.final.tooltip" : "variable.can.be.final.tooltip",
      variable.getName()
    );

    problemsHolder.registerProblem(
      variable.getNameIdentifierGroovy(),
      tooltip,
      new GrModifierFix(variable, PsiModifier.FINAL, true, ID_MODIFIER_LIST_PROVIDER)
    );
  }

  private static boolean checkVariableAssignedInsideClosureOrAnonymous(@NotNull GrControlFlowOwner owner, @NotNull GrVariable variable) {
    final Collection<PsiReference> references = ReferencesSearch.search(variable, variable.getUseScope()).findAll();
    for (final PsiReference reference : references) {
      final PsiElement element = reference.getElement();
      if (!(element instanceof GroovyPsiElement)) continue;
      final GroovyPsiElement groovyElement = (GroovyPsiElement)element;
      final GroovyPsiElement closure = PsiTreeUtil.getParentOfType(groovyElement, GrClosableBlock.class, GrAnonymousClassDefinition.class);
      if (closure == null || !PsiTreeUtil.isAncestor(owner, closure, false)) continue;
      if (PsiUtil.isLValue(groovyElement)) return true;
    }
    return false;
  }

  private static boolean checkVariableDeclaredInsideScope(@NotNull GrControlFlowOwner owner, @NotNull PsiElement variable) {
    final PsiElement scope = owner.getParent() instanceof PsiMethod
                             ? owner.getParent()
                             : owner;
    return PsiTreeUtil.isAncestor(scope, variable, false);
  }

  @Override
  public void check(@NotNull final GrControlFlowOwner owner, @NotNull final ProblemsHolder problemsHolder) {
    final Instruction[] flow = owner.getControlFlow();
    final DFAEngine<TObjectIntHashMap<GrVariable>> engine = new DFAEngine<>(
      flow,
      new WritesCounterDFAInstance(),
      new WritesCounterSemilattice<>()
    );
    final List<TObjectIntHashMap<GrVariable>> dfaResult = engine.performDFAWithTimeout();
    if (dfaResult == null || dfaResult.isEmpty()) return;

    dfaResult.get(dfaResult.size() - 1).forEachEntry(new TObjectIntProcedure<GrVariable>() {
      @Override
      public boolean execute(GrVariable variable, int writesCount) {
        if (writesCount == 1) {
          process(owner, variable, problemsHolder);
        }
        return true;
      }
    });
  }
}
