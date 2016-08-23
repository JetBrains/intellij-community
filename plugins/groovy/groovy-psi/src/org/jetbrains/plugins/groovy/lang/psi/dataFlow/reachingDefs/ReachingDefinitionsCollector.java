/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ControlFlowBuilderUtil;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

/**
 * @author ven
 */
public class ReachingDefinitionsCollector {
  private ReachingDefinitionsCollector() {
  }

  @NotNull
  public static FragmentVariableInfos obtainVariableFlowInformation(@NotNull final GrStatement first,
                                                                    @NotNull final GrStatement last,
                                                                    @NotNull final GrControlFlowOwner flowOwner,
                                                                    @NotNull final Instruction[] flow) {

    final DefinitionMap dfaResult = inferDfaResult(flow);

    final LinkedHashSet<Integer> fragmentInstructions = getFragmentInstructions(first, last, flow);
    final int[] postorder = ControlFlowBuilderUtil.postorder(flow);
    LinkedHashSet<Integer> reachableFromFragmentReads = getReachable(fragmentInstructions, flow, dfaResult, postorder);
    LinkedHashSet<Integer> fragmentReads = filterReads(fragmentInstructions, flow);

    final Map<String, VariableInfo> imap = new LinkedHashMap<>();
    final Map<String, VariableInfo> omap = new LinkedHashMap<>();

    final PsiManager manager = first.getManager();

    for (final Integer ref : fragmentReads) {
      ReadWriteVariableInstruction rwInstruction = (ReadWriteVariableInstruction)flow[ref];
      String name = rwInstruction.getVariableName();
      final int[] defs = dfaResult.getDefinitions(ref);
      if (!allDefsInFragment(defs, fragmentInstructions)) {
        addVariable(name, imap, manager, getType(rwInstruction.getElement()));
      }
    }

    for (final Integer ref : reachableFromFragmentReads) {
      ReadWriteVariableInstruction rwInstruction = (ReadWriteVariableInstruction)flow[ref];
      String name = rwInstruction.getVariableName();
      final int[] defs = dfaResult.getDefinitions(ref);
      if (anyDefInFragment(defs, fragmentInstructions)) {
        for (int def : defs) {
          if (fragmentInstructions.contains(def)) {
            PsiType outputType = getType(flow[def].getElement());
            addVariable(name, omap, manager, outputType);
          }
        }

        if (!allProperDefsInFragment(defs, ref, fragmentInstructions, postorder)) {
          PsiType inputType = getType(rwInstruction.getElement());
          addVariable(name, imap, manager, inputType);
        }
      }
    }

    addClosureUsages(imap, omap, first, last, flowOwner);

    final VariableInfo[] iarr = filterNonlocals(imap, last);
    final VariableInfo[] oarr = filterNonlocals(omap, last);

    return new FragmentVariableInfos() {
      @Override
      public VariableInfo[] getInputVariableNames() {
        return iarr;
      }

      @Override
      public VariableInfo[] getOutputVariableNames() {
        return oarr;
      }
    };
  }

  private static DefinitionMap inferDfaResult(Instruction[] flow) {
    final ReachingDefinitionsDfaInstance dfaInstance = new ReachingDefinitionsDfaInstance(flow);
    final ReachingDefinitionsSemilattice lattice = new ReachingDefinitionsSemilattice();
    final DFAEngine<DefinitionMap> engine = new DFAEngine<>(flow, dfaInstance, lattice);
    return postprocess(engine.performForceDFA(), flow, dfaInstance);
  }

  private static void addClosureUsages(final Map<String, VariableInfo> imap,
                                       final Map<String, VariableInfo> omap,
                                       final GrStatement first,
                                       final GrStatement last,
                                       GrControlFlowOwner flowOwner) {
    flowOwner.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitClosure(GrClosableBlock closure) {
        addUsagesInClosure(imap, omap, closure, first, last);
        super.visitClosure(closure);
      }

      private void addUsagesInClosure(final Map<String, VariableInfo> imap,
                                      final Map<String, VariableInfo> omap,
                                      final GrClosableBlock closure,
                                      final GrStatement first,
                                      final GrStatement last) {
        closure.accept(new GroovyRecursiveElementVisitor() {
          @Override
          public void visitReferenceExpression(GrReferenceExpression refExpr) {
            if (refExpr.isQualified()) {
              return;
            }
            PsiElement resolved = refExpr.resolve();
            if (!(resolved instanceof GrVariable)) {
              return;
            }
            GrVariable variable = (GrVariable)resolved;
            if (PsiTreeUtil.isAncestor(closure, variable, true)) {
              return;
            }
            if (variable instanceof ClosureSyntheticParameter &&
                PsiTreeUtil.isAncestor(closure, ((ClosureSyntheticParameter)variable).getClosure(), false)) {
              return;
            }

            String name = variable.getName();
            if (!(variable instanceof GrField)) {
              if (!isInFragment(first, last, resolved)) {
                if (isInFragment(first, last, closure)) {
                  addVariable(name, imap, variable.getManager(), variable.getType());
                }
              }
              else {
                if (!isInFragment(first, last, closure)) {
                  addVariable(name, omap, variable.getManager(), variable.getType());
                }
              }
            }
          }
        });
      }
    });
  }

  private static void addVariable(String name, Map<String, VariableInfo> map, PsiManager manager, PsiType type) {
    VariableInfoImpl info = (VariableInfoImpl)map.get(name);
    if (info == null) {
      info = new VariableInfoImpl(name, manager);
      map.put(name, info);
    }
    info.addSubtype(type);
  }

  private static LinkedHashSet<Integer> filterReads(final LinkedHashSet<Integer> instructions, final Instruction[] flow) {
    final LinkedHashSet<Integer> result = new LinkedHashSet<>();
    for (final Integer i : instructions) {
      final Instruction instruction = flow[i];
      if (isReadInsn(instruction)) {
        result.add(i);
      }
    }
    return result;
  }

  private static boolean allDefsInFragment(int[] defs, LinkedHashSet<Integer> fragmentInstructions) {
    for (int def : defs) {
      if (!fragmentInstructions.contains(def)) return false;
    }

    return true;
  }

  private static boolean allProperDefsInFragment(int[] defs, int ref, LinkedHashSet<Integer> fragmentInstructions, int[] postorder) {
    for (int def : defs) {
      if (!fragmentInstructions.contains(def) && postorder[def] < postorder[ref]) return false;
    }

    return true;
  }


  private static boolean anyDefInFragment(int[] defs, LinkedHashSet<Integer> fragmentInstructions) {
    for (int def : defs) {
      if (fragmentInstructions.contains(def)) return true;
    }

    return false;
  }

  @Nullable
  private static PsiType getType(PsiElement element) {
    if (element instanceof GrVariable) {
      return ((GrVariable)element).getTypeGroovy();
    }
    else if (element instanceof GrReferenceExpression) return ((GrReferenceExpression)element).getType();
    return null;
  }

  private static VariableInfo[] filterNonlocals(Map<String, VariableInfo> infos, GrStatement place) {
    List<VariableInfo> result = new ArrayList<>();
    for (Iterator<VariableInfo> iterator = infos.values().iterator(); iterator.hasNext(); ) {
      VariableInfo info = iterator.next();
      String name = info.getName();
      GroovyPsiElement property = ResolveUtil.resolveProperty(place, name);
      if (property instanceof GrVariable) {
        iterator.remove();
      }
      else if (property instanceof GrReferenceExpression) {
        GrMember member = PsiTreeUtil.getParentOfType(property, GrMember.class);
        if (member == null) {
          continue;
        }
        else if (!member.hasModifierProperty(PsiModifier.STATIC)) {
          if (member.getContainingClass() instanceof GroovyScriptClass) {
            //binding variable
            continue;
          }
        }
      }
      if (ResolveUtil.resolveClass(place, name) == null) {
        result.add(info);
      }
    }
    return result.toArray(new VariableInfo[result.size()]);
  }

  private static LinkedHashSet<Integer> getFragmentInstructions(GrStatement first, GrStatement last, Instruction[] flow) {
    LinkedHashSet<Integer> result = new LinkedHashSet<>();
    for (Instruction instruction : flow) {
      if (isInFragment(instruction, first, last)) {
        result.add(instruction.num());
      }
    }
    return result;
  }

  private static boolean isInFragment(Instruction instruction, GrStatement first, GrStatement last) {
    final PsiElement element = instruction.getElement();
    if (element == null) return false;
    return isInFragment(first, last, element);
  }

  private static boolean isInFragment(GrStatement first, GrStatement last, PsiElement element) {
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

  private static LinkedHashSet<Integer> getReachable(final LinkedHashSet<Integer> fragmentInsns,
                                                     final Instruction[] flow,
                                                     final DefinitionMap dfaResult,
                                                     final int[] postorder) {
    final LinkedHashSet<Integer> result = new LinkedHashSet<>();
    for (final Instruction insn : flow) {
      if (isReadInsn(insn)) {
        final int ref = insn.num();
        int[] definitions = dfaResult.getDefinitions(ref);
        if (definitions != null) {
          for (final int def : definitions) {
            if (fragmentInsns.contains(def) &&
                (!fragmentInsns.contains(ref) || postorder[ref] < postorder[def] && checkPathIsOutsideOfFragment(def, ref, flow, fragmentInsns))) {
              result.add(ref);
              break;
            }
          }
        }
      }
    }

    return result;
  }

  private static boolean checkPathIsOutsideOfFragment(int def, int ref, Instruction[] flow, LinkedHashSet<Integer> fragmentInsns) {
    Boolean path = findPath(flow[def], ref, fragmentInsns, false, new HashMap<>());
    assert path != null : "def=" + def + ", ref=" + ref;
    return path.booleanValue();
  }

  /**
   * return true if path is outside of fragment, null if there is no pathand false if path is inside fragment
   */
  @Nullable
  private static Boolean findPath(Instruction cur,
                                  int destination,
                                  LinkedHashSet<Integer> fragmentInsns,
                                  boolean wasOutside,
                                  HashMap<Instruction, Boolean> visited) {
    wasOutside = wasOutside || !fragmentInsns.contains(cur.num());
    visited.put(cur, null);
    Iterable<? extends Instruction> instructions = cur.allSuccessors();

    boolean pathExists = false;
    for (Instruction i : instructions) {
      if (i.num() == destination) return wasOutside;

      Boolean result;
      if (visited.containsKey(i)) {
        result = visited.get(i);
      }
      else {
        result = findPath(i, destination, fragmentInsns, wasOutside, visited);
        visited.put(i, result);
      }
      if (result != null) {
        if (result.booleanValue()) {
          visited.put(cur, true);
          return true;
        }
        pathExists = true;
      }
    }
    if (pathExists) {
      visited.put(cur, false);
      return false;
    }
    else {
      visited.put(cur, null);
      return null;
    }
  }


  private static boolean isReadInsn(Instruction insn) {
    return insn instanceof ReadWriteVariableInstruction && !((ReadWriteVariableInstruction)insn).isWrite();
  }

  @SuppressWarnings({"UnusedDeclaration"})
  private static String dumpDfaResult(ArrayList<TIntObjectHashMap<TIntHashSet>> dfaResult, ReachingDefinitionsDfaInstance dfa) {
    final StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < dfaResult.size(); i++) {
      TIntObjectHashMap<TIntHashSet> map = dfaResult.get(i);
      buffer.append("At ").append(i).append(":\n");
      map.forEachEntry(new TIntObjectProcedure<TIntHashSet>() {
        @Override
        public boolean execute(int i, TIntHashSet defs) {
          buffer.append(i).append(" -> ");
          defs.forEach(new TIntProcedure() {
            @Override
            public boolean execute(int i) {
              buffer.append(i).append(" ");
              return true;
            }
          });
          return true;
        }
      });
      buffer.append("\n");
    }

    return buffer.toString();
  }

  private static class VariableInfoImpl implements VariableInfo {
    @NotNull private final String myName;
    private final PsiManager myManager;

    @Nullable private
    PsiType myType;

    VariableInfoImpl(@NotNull String name, PsiManager manager) {
      myName = name;
      myManager = manager;
    }

    @Override
    @NotNull
    public String getName() {
      return myName;
    }

    @Override
    @Nullable
    public PsiType getType() {
      if (myType instanceof PsiIntersectionType) return ((PsiIntersectionType)myType).getConjuncts()[0];
      return myType;
    }

    void addSubtype(PsiType t) {
      if (t != null) {
        if (myType == null) {
          myType = t;
        }
        else {
          if (!myType.isAssignableFrom(t)) {
            if (t.isAssignableFrom(myType)) {
              myType = t;
            }
            else {
              myType = TypesUtil.getLeastUpperBound(myType, t, myManager);
            }
          }
        }
      }
    }
  }

  @NotNull
  private static DefinitionMap postprocess(@NotNull final ArrayList<DefinitionMap> dfaResult,
                                           @NotNull Instruction[] flow,
                                           @NotNull ReachingDefinitionsDfaInstance dfaInstance) {
    DefinitionMap result = new DefinitionMap();
    for (int i = 0; i < flow.length; i++) {
      Instruction insn = flow[i];
      if (insn instanceof ReadWriteVariableInstruction) {
        ReadWriteVariableInstruction rwInsn = (ReadWriteVariableInstruction)insn;
        if (!rwInsn.isWrite()) {
          int idx = dfaInstance.getVarIndex(rwInsn.getVariableName());
          result.copyFrom(dfaResult.get(i), idx, i);
        }
      }
    }
    return result;
  }
}
