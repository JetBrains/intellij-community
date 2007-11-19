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
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.*;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.*;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

/**
 * @author ven
 */
public class ReachingDefinitionsCollector {
  public static VariableInfo obtainVariableFlowInformation(final GrStatement first, final GrStatement last) {
    GrControlFlowOwner flowOwner = PsiTreeUtil.getParentOfType(first, GrControlFlowOwner.class);
    assert flowOwner != null;
    assert PsiTreeUtil.isAncestor(flowOwner, last, true);

    final Instruction[] flow = flowOwner.getControlFlow();
    final ReachingDefinitionsDfaInstance dfaInstance = new ReachingDefinitionsDfaInstance(flow);
    final ReachingDefinitionsSemilattice lattice = new ReachingDefinitionsSemilattice();
    final DFAEngine<TIntObjectHashMap<TIntHashSet>> engine = new DFAEngine<TIntObjectHashMap<TIntHashSet>>(flow, dfaInstance, lattice);
    final ArrayList<TIntObjectHashMap<TIntHashSet>> dfaResult = engine.performDFA();

    TIntHashSet fragmentReads = getFragmentReads(first, last, flow);
    TIntHashSet reachableFromFragmentReads = getReachable(fragmentReads, flow);

    final Set<String> inames = new LinkedHashSet<String>();
    final Set<String> onames = new LinkedHashSet<String>();

    addNames(fragmentReads, inames, flow, dfaResult, first, last, false);
    addNames(reachableFromFragmentReads, onames, flow, dfaResult, first, last, true);

    filterFields(inames, first);
    filterFields(onames, first);

    final String[] iarr = inames.toArray(new String[inames.size()]);
    final String[] oarr = onames.toArray(new String[onames.size()]);

    return new VariableInfo() {
      public String[] getInputVariableNames() {
        return iarr;
      }

      public String[] getOutputVariableNames() {
        return oarr;
      }
    };
  }

  private static void addNames(final TIntHashSet reads,
                               final Set<String> names,
                               final Instruction[] flow,
                               final ArrayList<TIntObjectHashMap<TIntHashSet>> dfaResult,
                               final GrStatement first,
                               final GrStatement last, final boolean isInfragment) {
    reads.forEach(new TIntProcedure() {
      public boolean execute(int insNum) {
        final TIntObjectHashMap<TIntHashSet> info = dfaResult.get(insNum);
        final String useName = ((ReadWriteVariableInstruction) flow[insNum]).getVariableName();
        info.forEachValue(new TObjectProcedure<TIntHashSet>() {
          public boolean execute(TIntHashSet defs) {
            defs.forEach(new TIntProcedure() {
              public boolean execute(int def) {
                final String defName = ((ReadWriteVariableInstruction) flow[def]).getVariableName();
                if (defName.equals(useName) && isInFragment(flow[def], first, last) == isInfragment) {
                  names.add(((ReadWriteVariableInstruction) flow[def]).getVariableName());
                }
                return true;
              }
            });
            return true;
          }
        });
        return true;
      }
    });
  }

  private static void filterFields(Set<String> names, GrStatement place) {
    for (Iterator<String> iterator = names.iterator(); iterator.hasNext();) {
      String name =  iterator.next();
      final GroovyPsiElement resolved = ResolveUtil.resolveVariable(place, name);
      if (resolved instanceof PsiField || resolved instanceof GrReferenceExpression) iterator.remove();
    }
  }

  private static TIntHashSet getFragmentReads(GrStatement first, GrStatement last, Instruction[] flow) {
    TIntHashSet fragmentReads;
    fragmentReads = new TIntHashSet();
    for (int i = 0; i < flow.length; i++) {
      Instruction instruction = flow[i];
      if (instruction instanceof ReadWriteVariableInstruction &&
          !((ReadWriteVariableInstruction) instruction).isWrite() && isInFragment(instruction, first, last)) {
        fragmentReads.add(instruction.num());
      }
    }
    return fragmentReads;
  }

  private static boolean isInFragment(Instruction instruction, GrStatement first, GrStatement last) {
    final PsiElement element = instruction.getElement();
    if (element == null) return false;
    final PsiElement parent = first.getParent();
    if (!PsiTreeUtil.isAncestor(parent, element, true)) return false;
    PsiElement run = element;
    while (run.getParent() != parent) run = run.getParent();
    return isBetween(first, last, run);
  }

  private static boolean isBetween(PsiElement first, PsiElement last, PsiElement run) {
    while (first != null && first != run) first = first.getNextSibling();
    if (first == null) return false;
    while (last != null && last != run) last = last.getPrevSibling();
    if (last == null) return false;

    return true;
  }

  private static TIntHashSet getReachable(final TIntHashSet fragmentReads, final Instruction[] flow) {
    final TIntHashSet visited = new TIntHashSet();
    final TIntHashSet result = new TIntHashSet();
    final CallEnvironment env = new CallEnvironment.DepthFirstCallEnvironment();
    fragmentReads.forEach(new TIntProcedure() {
      public boolean execute(int insnNum) {
        processInsn(flow[insnNum], visited, result);
        return true;
      }

      private void processInsn(Instruction instruction, TIntHashSet visited, TIntHashSet result) {
        final int num = instruction.num();
        if (visited.contains(num)) return;
        visited.add(num);
        if (instruction instanceof ReadWriteVariableInstruction &&
            !((ReadWriteVariableInstruction) instruction).isWrite() &&
            !fragmentReads.contains(num)) {
          result.add(num);
        }

        for (Instruction succ : instruction.succ(env)) {
          processInsn(succ, visited, result);
        }
      }
    });

    return result;
  }
}
