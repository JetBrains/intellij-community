// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GroovyControlFlow;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtilKt;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult;
import org.jetbrains.plugins.groovy.lang.resolve.GrReferenceResolveRunnerKt;

import java.util.*;
import java.util.function.IntConsumer;

import static org.jetbrains.plugins.groovy.lang.psi.controlFlow.OrderUtil.reversedPostOrder;

/**
 * @author ven
 */
public final class ReachingDefinitionsCollector {
  private ReachingDefinitionsCollector() {
  }

  @NotNull
  public static FragmentVariableInfos obtainVariableFlowInformation(@NotNull final GrStatement first,
                                                                    @NotNull final GrStatement last,
                                                                    @NotNull final GrControlFlowOwner flowOwner,
                                                                    @NotNull GroovyControlFlow flow) {

    final Int2ObjectMap<int[]> dfaResult = inferDfaResult(flow.getFlow());

    final LinkedHashSet<Integer> fragmentInstructions = getFragmentInstructions(first, last, flow.getFlow());
    final int[] postorder = reversedPostOrder(flow.getFlow());
    LinkedHashSet<Integer> reachableFromFragmentReads = getReachable(fragmentInstructions, flow.getFlow(), dfaResult, postorder);
    LinkedHashSet<Integer> fragmentReads = filterReads(fragmentInstructions, flow.getFlow());

    final Map<String, VariableInfo> imap = new LinkedHashMap<>();
    final Map<String, VariableInfo> omap = new LinkedHashMap<>();

    final PsiManager manager = first.getManager();

    for (final Integer ref : fragmentReads) {
      ReadWriteVariableInstruction rwInstruction = (ReadWriteVariableInstruction)flow.getFlow()[ref];
      final int[] defs = dfaResult.get((int)ref);
      assert defs != null;
      int descriptorId = rwInstruction.getDescriptor();
      VariableDescriptor descriptor = flow.getVarIndices()[descriptorId];
      if (!allDefsInFragment(defs, fragmentInstructions) || isLocallyDeclaredOutOf(rwInstruction.getElement(), descriptor, flowOwner)) {
        addVariable(descriptor.getName(), imap, manager, getType(rwInstruction.getElement()));
      }
    }

    Set<Integer> outerBound = getFragmentOuterBound(fragmentInstructions, flow.getFlow());
    for (final Integer ref : reachableFromFragmentReads) {
      ReadWriteVariableInstruction rwInstruction = (ReadWriteVariableInstruction)flow.getFlow()[ref];
      int descriptorId = rwInstruction.getDescriptor();
      VariableDescriptor descriptor = flow.getVarIndices()[descriptorId];
      final int[] defs = dfaResult.get((int)ref);
      assert defs != null;
      if (anyDefInFragment(defs, fragmentInstructions)) {
        for (int insnNum : outerBound) {
          addVariable(descriptor.getName(), omap, manager, getVariableTypeAt(flowOwner, flow.getFlow()[insnNum], descriptorId, descriptor));
        }

        if (!allProperDefsInFragment(defs, ref, fragmentInstructions, postorder)) {
          PsiType inputType = getType(rwInstruction.getElement());
          addVariable(descriptor.getName(), imap, manager, inputType);
        }
      }
    }

    addClosureUsages(imap, omap, first, last, flowOwner);

    return new FragmentVariableInfos() {
      @Override
      public VariableInfo[] getInputVariableNames() {
        return imap.values().toArray(VariableInfo.EMPTY_ARRAY);
      }

      @Override
      public VariableInfo[] getOutputVariableNames() {
        return omap.values().toArray(VariableInfo.EMPTY_ARRAY);
      }
    };
  }

  private static PsiType getVariableTypeAt(GrControlFlowOwner flowOwner, Instruction instruction, int descriptorId, VariableDescriptor descriptor) {
    PsiElement context = instruction.getElement();
    PsiType outputType = TypeInferenceHelper.getInferredType(descriptorId, instruction, flowOwner);
    if (outputType == null) {
      GrVariable variable = resolveToLocalVariable(context, descriptor.getName());
      if (variable != null) {
        outputType = variable.getDeclaredType();
      }
    }
    return outputType;
  }


  @Nullable
  private static GrVariable resolveToLocalVariable(@Nullable PsiElement element, @NotNull String name) {
    if (element == null) return null;
    ElementResolveResult<GrVariable> result = GrReferenceResolveRunnerKt.resolveToLocalVariable(element, name);
    return result != null ? result.getElement() : null;
  }

  private static boolean isLocallyDeclaredOutOf(@Nullable PsiElement element,
                                                @NotNull VariableDescriptor descriptor,
                                                @NotNull GrControlFlowOwner flowOwner) {
    GrVariable variable = resolveToLocalVariable(element, descriptor.getName());
    if (variable == null) return false;
    return !PsiImplUtilKt.isDeclaredIn(variable, flowOwner);
  }

  private static @NotNull Int2ObjectMap<int[]> inferDfaResult(Instruction @NotNull [] flow) {
    final ReachingDefinitionsDfaInstance dfaInstance = new ReachingDefinitionsDfaInstance();
    final ReachingDefinitionsSemilattice lattice = new ReachingDefinitionsSemilattice();
    final DFAEngine<DefinitionMap> engine = new DFAEngine<>(flow, dfaInstance, lattice);
    return postprocess(engine.performForceDFA(), flow);
  }

  private static void addClosureUsages(final Map<String, VariableInfo> imap,
                                       final Map<String, VariableInfo> omap,
                                       final GrStatement first,
                                       final GrStatement last,
                                       GrControlFlowOwner flowOwner) {
    flowOwner.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitClosure(@NotNull GrClosableBlock closure) {
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
          public void visitReferenceExpression(@NotNull GrReferenceExpression refExpr) {
            if (refExpr.isQualified()) {
              return;
            }
            String name = refExpr.getReferenceName();
            if (name == null) return;
            GrVariable variable = resolveToLocalVariable(refExpr, name);
            if (variable == null) return;
            if (PsiImplUtilKt.isDeclaredIn(variable, closure)) return;

            if (!(variable instanceof GrField)) {
              if (!isInFragment(first, last, variable)) {
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
                                                     final @NotNull Int2ObjectMap<int[]> dfaResult,
                                                     final int[] postorder) {
    final LinkedHashSet<Integer> result = new LinkedHashSet<>();
    for (final Instruction insn : flow) {
      if (isReadInsn(insn)) {
        final int ref = insn.num();
        int[] definitions = dfaResult.get(ref);
        if (definitions != null) {
          for (final int def : definitions) {
            if (fragmentInsns.contains(def) &&
                (!fragmentInsns.contains(ref) ||
                 postorder[ref] < postorder[def] && checkPathIsOutsideOfFragment(def, ref, flow, fragmentInsns))) {
              result.add(ref);
              break;
            }
          }
        }
      }
    }

    return result;
  }

  private static Set<Integer> getFragmentOuterBound(@NotNull LinkedHashSet<Integer> fragmentInstructions, Instruction @NotNull [] flow) {
    final Set<Integer> result = new HashSet<>();
    for (Integer num : fragmentInstructions) {
      for (Instruction successor : flow[num].allSuccessors()) {
        if (!fragmentInstructions.contains(successor.num())) {
          result.add(num);
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

  @SuppressWarnings("UnusedDeclaration")
  @NonNls
  private static String dumpDfaResult(ArrayList<Int2ObjectMap<IntSet>> dfaResult, ReachingDefinitionsDfaInstance dfa) {
    @NonNls final StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < dfaResult.size(); i++) {
      Int2ObjectMap<IntSet> map = dfaResult.get(i);
      buffer.append("At ").append(i).append(":\n");
      for (IntSet v : map.values()) {
        buffer.append(i).append(" -> ");
        v.forEach((IntConsumer)i1 -> {
          buffer.append(i1).append(" ");
        });
      }
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

  /**
   * @return map instruction index -> definitions for variable in the instruction
   */
  @NotNull
  private static Int2ObjectMap<int[]> postprocess(@NotNull final List<DefinitionMap> dfaResult,
                                                  Instruction @NotNull [] flow) {
    Int2ObjectMap<int[]> result = new Int2ObjectOpenHashMap<>();
    for (int i = 0; i < flow.length; i++) {
      Instruction insn = flow[i];
      if (!(insn instanceof ReadWriteVariableInstruction)) {
        continue;
      }
      ReadWriteVariableInstruction rwInsn = (ReadWriteVariableInstruction)insn;
      if (rwInsn.isWrite()) {
        continue;
      }
      DefinitionMap definitionMap = dfaResult.get(i);
      definitionMap = definitionMap == null ? DefinitionMap.NEUTRAL : definitionMap;
      int varIndex = rwInsn.getDescriptor();
      IntSet defs = definitionMap.getDefinitions(varIndex);
      if (defs == null) {
        defs = IntSet.of();
      }
      result.put(i, defs.toIntArray());
    }
    return result;
  }
}
