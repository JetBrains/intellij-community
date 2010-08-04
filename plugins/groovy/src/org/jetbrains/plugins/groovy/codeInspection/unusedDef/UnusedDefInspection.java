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
package org.jetbrains.plugins.groovy.codeInspection.unusedDef;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntProcedure;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyLocalInspectionBase;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrPostfixExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsDfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsSemilattice;

import java.util.ArrayList;

/**
 & @author ven
 */
public class UnusedDefInspection extends GroovyLocalInspectionBase {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return GroovyInspectionBundle.message("groovy.dfa.issues");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return GroovyInspectionBundle.message("unused.assignment");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "GroovyUnusedAssignment";
  }


  protected void check(final GrControlFlowOwner owner, final ProblemsHolder problemsHolder) {
    final Instruction[] flow = owner.getControlFlow();
    final ReachingDefinitionsDfaInstance dfaInstance = new ReachingDefinitionsDfaInstance(flow);
    final ReachingDefinitionsSemilattice lattice = new ReachingDefinitionsSemilattice();
    final DFAEngine<TIntObjectHashMap<TIntHashSet>> engine = new DFAEngine<TIntObjectHashMap<TIntHashSet>>(flow, dfaInstance, lattice);
    final ArrayList<TIntObjectHashMap<TIntHashSet>> dfaResult = engine.performDFA();
    final TIntHashSet unusedDefs = new TIntHashSet();
    for (Instruction instruction : flow) {
      if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction) instruction).isWrite()) {
        unusedDefs.add(instruction.num());
      }
    }

    for (int i = 0; i < dfaResult.size(); i++) {
      final Instruction instruction = flow[i];
      if (instruction instanceof ReadWriteVariableInstruction) {
        final ReadWriteVariableInstruction varInsn = (ReadWriteVariableInstruction) instruction;
        if (!varInsn.isWrite()) {
          final String varName = varInsn.getVariableName();
          TIntObjectHashMap<TIntHashSet> e = dfaResult.get(i);
          e.forEachValue(new TObjectProcedure<TIntHashSet>() {
            public boolean execute(TIntHashSet reaching) {
              reaching.forEach(new TIntProcedure() {
                public boolean execute(int defNum) {
                  final String defName = ((ReadWriteVariableInstruction) flow[defNum]).getVariableName();
                  if (varName.equals(defName)) {
                    unusedDefs.remove(defNum);
                  }
                  return true;
                }
              });
              return true;
            }
          });
        }
      }
    }

    unusedDefs.forEach(new TIntProcedure() {
      public boolean execute(int num) {
        final ReadWriteVariableInstruction instruction = (ReadWriteVariableInstruction)flow[num];
        final PsiElement element = instruction.getElement();
        if (element == null) return true;
        PsiElement toHighlight = null;
        if (isLocalAssignment(element) && isUsedInToplevelFlowOnly(element)) {
          if (element instanceof GrReferenceExpression) {
            PsiElement parent = element.getParent();
            if (parent instanceof GrAssignmentExpression) {
              toHighlight = ((GrAssignmentExpression)parent).getLValue();
            }
            if (parent instanceof GrPostfixExpression) {
              toHighlight = parent;
            }
          }
          else if (element instanceof GrVariable) {
            toHighlight = ((GrVariable)element).getNameIdentifierGroovy();
          }
          if (toHighlight == null) toHighlight = element;
          problemsHolder.registerProblem(toHighlight, GroovyInspectionBundle.message("unused.assignment.tooltip"),
                                         ProblemHighlightType.LIKE_UNUSED_SYMBOL);
        }
        return true;
      }
    });
  }

  private boolean isUsedInToplevelFlowOnly(PsiElement element) {
    GrVariable var = null;
    if (element instanceof GrVariable) {
      var = (GrVariable) element;
    } else if (element instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression) element).resolve();
      if (resolved instanceof GrVariable) var = (GrVariable) resolved;
    }

    if (var != null) {
      final GroovyPsiElement scope = getScope(var);
      assert scope != null;

      return ReferencesSearch.search(var, new LocalSearchScope(scope)).forEach(new Processor<PsiReference>() {
        public boolean process(PsiReference ref) {
          return getScope(ref.getElement()) == scope;
        }
      });
    }

    return true;
  }

  private GroovyPsiElement getScope(PsiElement var) {
    return PsiTreeUtil.getParentOfType(var, GrClosableBlock.class, GrMethod.class, GrClassInitializer.class, GroovyFileBase.class);
  }

  private boolean isLocalAssignment(PsiElement element) {
    if (element instanceof GrVariable) {
      return isLocalVariable((GrVariable) element, false);
    } else if (element instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression) element).resolve();
      return resolved instanceof GrVariable && isLocalVariable((GrVariable) resolved, true);
    }

    return false;
  }

  private boolean isLocalVariable(GrVariable var, boolean parametersAllowed) {
    if (var instanceof GrField) return false;
    else if (var instanceof GrParameter && !parametersAllowed) return false;

    return true;
  }

  public boolean isEnabledByDefault() {
    return true;
  }
}
