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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gnu.trove.*;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.*;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ven
 */
public class ReachingDefinitionsCollector {
  public static FragmentVariableInfos obtainVariableFlowInformation(final GrStatement first, final GrStatement last) {
    GroovyPsiElement context = PsiTreeUtil.getParentOfType(first, GrMethod.class, GrClosableBlock.class, GroovyFileBase.class);
    GrControlFlowOwner flowOwner;
    if (context instanceof GrMethod) flowOwner = ((GrMethod) context).getBlock();
    else flowOwner = (GrControlFlowOwner) context;

    assert flowOwner != null;
    assert PsiTreeUtil.isAncestor(flowOwner, last, true);

    final Instruction[] flow = flowOwner.getControlFlow();
    final ReachingDefinitionsDfaInstance dfaInstance = new ReachingDefinitionsDfaInstance(flow);
    final ReachingDefinitionsSemilattice lattice = new ReachingDefinitionsSemilattice();
    final DFAEngine<TIntObjectHashMap<TIntHashSet>> engine = new DFAEngine<TIntObjectHashMap<TIntHashSet>>(flow, dfaInstance, lattice);
    final TIntObjectHashMap<TIntHashSet> dfaResult = postprocess(engine.performDFA(), flow, dfaInstance);

    final TIntHashSet fragmentInstructions = getFragmentInstructions(first, last, flow);
    TIntHashSet reachableFromFragmentReads = getReachable(fragmentInstructions, flow, dfaResult,
        ControlFlowUtil.postorder(flow));
    TIntHashSet fragmentReads = filterReads(fragmentInstructions, flow);

    final Map<String, VariableInfo> imap = new HashMap<String, VariableInfo>();
    final Set<VariableInfo> iset = new LinkedHashSet<VariableInfo>();
    final Map<String, VariableInfo> omap = new HashMap<String, VariableInfo>();
    final Set<VariableInfo> oset = new LinkedHashSet<VariableInfo>();

    final PsiManager manager = first.getManager();

    fragmentReads.forEach(new TIntProcedure() {
      public boolean execute(int ref) {
        ReadWriteVariableInstruction rwInstruction = (ReadWriteVariableInstruction) flow[ref];
        String name = rwInstruction.getVariableName();
        final int[] defs = dfaResult.get(ref).toArray();
        if (!allDefsInFragment(defs, fragmentInstructions)) {
          addVariable(name, imap, manager, getType(rwInstruction.getElement()));
        }

        return true;
      }
    });

    reachableFromFragmentReads.forEach(new TIntProcedure() {
      public boolean execute(int ref) {
        ReadWriteVariableInstruction rwInstruction = (ReadWriteVariableInstruction) flow[ref];
        String name = rwInstruction.getVariableName();
        final int[] defs = dfaResult.get(ref).toArray();
        if (anyDefInFragment(defs, fragmentInstructions)) {
          addVariable(name, omap, manager, getType(rwInstruction.getElement()));

          if (!allDefsInFragment(defs, fragmentInstructions)) {
            addVariable(name, imap, manager, getType(rwInstruction.getElement()));
          }
        }

        return true;
      }
    });

    final VariableInfo[] iarr = filterNonlocals(imap, first);
    final VariableInfo[] oarr = filterNonlocals(omap, first);

    return new FragmentVariableInfos() {
      public VariableInfo[] getInputVariableNames() {
        return iarr;
      }

      public VariableInfo[] getOutputVariableNames() {
        return oarr;
      }
    };
  }

  private static void addVariable(String name, Map<String, VariableInfo> omap, PsiManager manager, PsiType type) {
    VariableInfoImpl info = (VariableInfoImpl) omap.get(name);
    if (info == null) {
      info = new VariableInfoImpl(name, manager);
      omap.put(name, info);
    }
    info.addSubtype(type);
  }

  private static TIntHashSet filterReads(final TIntHashSet instructions, final Instruction[] flow) {
    final TIntHashSet result = new TIntHashSet();
    instructions.forEach(new TIntProcedure() {
      public boolean execute(int i) {
        final Instruction instruction = flow[i];
        if (instruction instanceof ReadWriteVariableInstruction && !((ReadWriteVariableInstruction) instruction).isWrite()) {
          result.add(i);
        }
        return true;
      }
    });
    return result;
  }

  private static boolean allDefsInFragment(int[] defs, TIntHashSet fragmentInstructions) {
    for (int def : defs) {
      if (!fragmentInstructions.contains(def)) return false;
    }

    return true;
  }

  private static boolean anyDefInFragment(int[] defs, TIntHashSet fragmentInstructions) {
    for (int def : defs) {
      if (fragmentInstructions.contains(def)) return true;
    }

    return false;
  }

  private static PsiType getType(PsiElement element) {
    if (element instanceof GrVariable) return ((GrVariable) element).getTypeGroovy();
    else if (element instanceof GrReferenceExpression) return ((GrReferenceExpression) element).getType();
    return null;
  }

  private static VariableInfo[] filterNonlocals(Map<String, VariableInfo> infos, GrStatement place) {
    List<VariableInfo> result = new ArrayList<VariableInfo>();
    for (Iterator<VariableInfo> iterator = infos.values().iterator(); iterator.hasNext();) {
      VariableInfo info = iterator.next();
      final GroovyPsiElement resolved = ResolveUtil.resolveVariable(place, info.getName());
      if (resolved instanceof PsiField) iterator.remove();
      else if (resolved instanceof GrReferenceExpression) {
        GrMember member = PsiTreeUtil.getParentOfType(resolved, GrMember.class);
        if (member == null) continue;
        else if (!member.hasModifierProperty(PsiModifier.STATIC)) {
          if (member.getContainingClass() instanceof GroovyScriptClass) {
            //binding variable
            continue;
          }
        }
      }
      result.add(info);
    }
    return result.toArray(new VariableInfo[result.size()]);
  }

  private static TIntHashSet getFragmentInstructions(GrStatement first, GrStatement last, Instruction[] flow) {
    TIntHashSet result;
    result = new TIntHashSet();
    for (int i = 0; i < flow.length; i++) {
      Instruction instruction = flow[i];
      if (isInFragment(instruction, first, last)) {
        result.add(instruction.num());
      }
    }
    return result;
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

  private static TIntHashSet getReachable(final TIntHashSet fragmentInsns, final Instruction[] flow, TIntObjectHashMap<TIntHashSet> dfaResult, final int[] postorder) {
    final TIntHashSet result = new TIntHashSet();
    for (Instruction insn : flow) {
      if (insn instanceof ReadWriteVariableInstruction &&
          !((ReadWriteVariableInstruction) insn).isWrite()) {
        final int ref = insn.num();
        TIntHashSet defs = dfaResult.get(ref);
        defs.forEach(new TIntProcedure() {
          public boolean execute(int def) {
            if (fragmentInsns.contains(def)) {
              if (!fragmentInsns.contains(ref) || postorder[ref] < postorder[def]) {
                result.add(ref);
                return false;
              }
            }
            return true;
          }
        });
      }
    }

    return result;
  }

  private static String dumpDfaResult(ArrayList<TIntObjectHashMap<TIntHashSet>> dfaResult, ReachingDefinitionsDfaInstance dfa) {
    final StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < dfaResult.size(); i++) {
      TIntObjectHashMap<TIntHashSet> map = dfaResult.get(i);
      buffer.append("At " + i + ":\n");
      map.forEachEntry(new TIntObjectProcedure<TIntHashSet>() {
        public boolean execute(int i, TIntHashSet defs) {
          buffer.append(i).append(" -> ");
          defs.forEach(new TIntProcedure() {
            public boolean execute(int i) {
              buffer.append(i).append(" ");
              return true;
            }
          });
          return true;
        }

        ;
      });
      buffer.append("\n");
    }

    return buffer.toString();
  }

  private static class VariableInfoImpl implements VariableInfo {
    private
    @NotNull
    String myName;
    private PsiManager myManager;

    private
    @Nullable
    PsiType myType;

    VariableInfoImpl(@NotNull String name, PsiManager manager) {
      myName = name;
      myManager = manager;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @Nullable
    public PsiType getType() {
      if (myType instanceof PsiIntersectionType) return ((PsiIntersectionType) myType).getConjuncts()[0];
      return myType;
    }

    void addSubtype(PsiType t) {
      if (t != null) {
        if (myType == null) myType = t;
        else {
          if (!myType.isAssignableFrom(t)) {
            if (t.isAssignableFrom(myType)) {
              myType = t;
            } else {
              myType = GenericsUtil.getLeastUpperBound(myType, t, myManager);
            }
          }
        }
      }
    }
  }

  private static TIntObjectHashMap<TIntHashSet> postprocess(final ArrayList<TIntObjectHashMap<TIntHashSet>> dfaResult, Instruction[] flow, ReachingDefinitionsDfaInstance dfaInstance) {
    TIntObjectHashMap<TIntHashSet> result = new TIntObjectHashMap<TIntHashSet>();
    for (int i = 0; i < flow.length; i++) {
      Instruction insn = flow[i];
      if (insn instanceof ReadWriteVariableInstruction) {
        ReadWriteVariableInstruction rwInsn = (ReadWriteVariableInstruction) insn;
        if (!rwInsn.isWrite()) {
          int idx = dfaInstance.getVarIndex(rwInsn.getVariableName());
          TIntHashSet defs = dfaResult.get(i).get(idx);
          if (defs == null) defs = new TIntHashSet();
          result.put(i, defs);
        }
      }
    }
    return result;
  }
}
