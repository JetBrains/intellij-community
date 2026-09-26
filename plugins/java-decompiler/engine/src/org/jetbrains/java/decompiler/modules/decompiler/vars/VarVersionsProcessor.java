// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.SSAConstructorSparseEx;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.DotExporter;
import org.jetbrains.java.decompiler.util.FastSparseSetFactory.FastSparseSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class VarVersionsProcessor {
  private final StructMethod method;
  private Map<Integer, VarVersion> mapOriginalVarIndices = Collections.emptyMap();
  private final VarTypeProcessor typeProcessor;

  public VarVersionsProcessor(StructMethod mt, MethodDescriptor md) {
    method = mt;
    typeProcessor = new VarTypeProcessor(mt, md);
  }

  public void setVarVersions(RootStatement root, VarVersionsProcessor previousVersionsProcessor) {
    SSAConstructorSparseEx ssa = new SSAConstructorSparseEx();
    ssa.splitVariables(root, method);

    FlattenStatementsHelper flattenHelper = new FlattenStatementsHelper();
    DirectGraph graph = flattenHelper.buildDirectGraph(root);

    DotExporter.toDotFile(graph, method, "setVarVersions");

    mergePhiVersions(ssa, graph);

    typeProcessor.calculateVarTypes(root, graph);

    //simpleMerge(typeProcessor, graph, method);

    // FIXME: advanced merging

    eliminateNonJavaTypes(typeProcessor);

    setNewVarIndices(typeProcessor, graph, previousVersionsProcessor);
  }

  private static void mergePhiVersions(SSAConstructorSparseEx ssa, DirectGraph graph) {
    // collect phi versions
    List<Set<VarVersion>> lst = new ArrayList<>();
    for (Entry<VarVersion, FastSparseSet<Integer>> ent : ssa.getPhi().entrySet()) {
      Set<VarVersion> set = new HashSet<>();
      set.add(ent.getKey());
      for (Integer version : ent.getValue()) {
        set.add(new VarVersion(ent.getKey().var, version.intValue()));
      }

      for (int i = lst.size() - 1; i >= 0; i--) {
        Set<VarVersion> tset = lst.get(i);
        Set<VarVersion> intersection = new HashSet<>(set);
        intersection.retainAll(tset);

        if (!intersection.isEmpty()) {
          set.addAll(tset);
          lst.remove(i);
        }
      }

      lst.add(set);
    }

    Map<VarVersion, Integer> phiVersions = new HashMap<>();
    for (Set<VarVersion> set : lst) {
      int min = Integer.MAX_VALUE;
      for (VarVersion paar : set) {
        if (paar.version < min) {
          min = paar.version;
        }
      }

      for (VarVersion paar : set) {
        phiVersions.put(new VarVersion(paar.var, paar.version), min);
      }
    }

    updateVersions(graph, phiVersions);
  }

  private static void updateVersions(DirectGraph graph, final Map<VarVersion, Integer> versions) {
    graph.iterateExprents(exprent -> {
      List<Exprent> lst = exprent.getAllExprents(true);
      lst.add(exprent);

      for (Exprent expr : lst) {
        if (expr.type == Exprent.EXPRENT_VAR) {
          VarExprent var = (VarExprent)expr;
          Integer version = versions.get(new VarVersion(var));
          if (version != null) {
            var.setVersion(version);
          }
        }
      }

      return 0;
    });
  }

  private static void eliminateNonJavaTypes(VarTypeProcessor typeProcessor) {
    Map<VarVersion, VarType> mapExprentMaxTypes = typeProcessor.getMaxExprentTypes();
    Map<VarVersion, VarType> mapExprentMinTypes = typeProcessor.getMinExprentTypes();

    for (VarVersion paar : new ArrayList<>(mapExprentMinTypes.keySet())) {
      VarType type = mapExprentMinTypes.get(paar);
      VarType maxType = mapExprentMaxTypes.get(paar);

      if (type.getType() == CodeConstants.TYPE_BYTECHAR || type.getType() == CodeConstants.TYPE_SHORTCHAR) {
        if (maxType != null && maxType.getType() == CodeConstants.TYPE_CHAR) {
          type = VarType.VARTYPE_CHAR;
        }
        else {
          type = type.getType() == CodeConstants.TYPE_BYTECHAR ? VarType.VARTYPE_BYTE : VarType.VARTYPE_SHORT;
        }
        mapExprentMinTypes.put(paar, type);
        //} else if(type.type == CodeConstants.TYPE_CHAR && (maxType == null || maxType.type == CodeConstants.TYPE_INT)) { // when possible, lift char to int
        //	mapExprentMinTypes.put(paar, VarType.VARTYPE_INT);
      }
      else if (type.getType() == CodeConstants.TYPE_NULL) {
        // Instead of blindly using Object, look for a concrete type from another SSA version
        // of the same bytecode variable. This allows merging null-initialized variables
        // (e.g., "A a = null; a = new A(...)") without losing the concrete type.
        // Only use a concrete type if all other versions of this slot agree on a type
        // more specific than Object. This avoids incorrectly narrowing the type when
        // a slot is reused for different purposes (e.g., coroutine state machines).
        VarType replacement = null;
        int nonNullCount = 0;
        boolean compatible = true;
        for (Map.Entry<VarVersion, VarType> other : mapExprentMinTypes.entrySet()) {
          VarVersion otherPair = other.getKey();
          if (otherPair.var == paar.var && otherPair.version != paar.version) {
            VarType otherType = other.getValue();
            if (otherType != null && otherType.getType() != CodeConstants.TYPE_NULL) {
              nonNullCount++;
              if (replacement == null) {
                replacement = otherType;
              }
              else {
                VarType common = VarType.getCommonSupertype(replacement, otherType);
                if (common == null || VarType.VARTYPE_OBJECT.equals(common)) {
                  compatible = false;
                  break;
                }
                replacement = common;
              }
            }
          }
        }
        // Use the concrete type if all non-null versions of this slot agree on a type
        // more specific than Object. Coroutine slot reuse is protected by the narrowing
        // guard in VarDefinitionHelper.remapVar.
        if (compatible && nonNullCount >= 1 && !VarType.VARTYPE_OBJECT.equals(replacement)) {
          mapExprentMinTypes.put(paar, replacement);
        }
        else {
          mapExprentMinTypes.put(paar, VarType.VARTYPE_OBJECT);
        }
      }
    }
  }

  private void setNewVarIndices(VarTypeProcessor typeProcessor, DirectGraph graph, VarVersionsProcessor previousVersionsProcessor) {
    final Map<VarVersion, VarType> mapExprentMaxTypes = typeProcessor.getMaxExprentTypes();
    Map<VarVersion, VarType> mapExprentMinTypes = typeProcessor.getMinExprentTypes();
    Map<VarVersion, Integer> mapFinalVars = typeProcessor.getFinalVariables();

    CounterContainer counters = DecompilerContext.getCounterContainer();

    final Map<VarVersion, Integer> mapVarPaar = new HashMap<>();
    Map<Integer, VarVersion> mapOriginalVarIndices = new HashMap<>(this.mapOriginalVarIndices);

    // map var-version pairs on new var indexes
    List<VarVersion> vvps = new ArrayList<>(mapExprentMinTypes.keySet());
    Collections.sort(vvps);

    for (VarVersion pair : vvps) {

      if (pair.version >= 0) {
        int newIndex = pair.version == 1 ? pair.var : counters.getCounterAndIncrement(CounterContainer.VAR_COUNTER);

        VarVersion newVar = new VarVersion(newIndex, 0);

        mapExprentMinTypes.put(newVar, mapExprentMinTypes.get(pair));
        mapExprentMaxTypes.put(newVar, mapExprentMaxTypes.get(pair));

        if (mapFinalVars.containsKey(pair)) {
          mapFinalVars.put(newVar, mapFinalVars.remove(pair));
        }

        mapVarPaar.put(pair, newIndex);
        mapOriginalVarIndices.put(newIndex, pair);
      }
    }

    // set new vars
    graph.iterateExprents(exprent -> {
      List<Exprent> lst = exprent.getAllExprents(true);
      lst.add(exprent);

      for (Exprent expr : lst) {
        if (expr.type == Exprent.EXPRENT_VAR) {
          VarExprent newVar = (VarExprent)expr;
          Integer newVarIndex = mapVarPaar.get(new VarVersion(newVar));
          if (newVarIndex != null) {
            String name = newVar.getProcessor().getAssignedVarName(new VarVersion(newVar.getIndex(), 0));
            newVar.setIndex(newVarIndex);
            newVar.setVersion(0);
            if (name != null && newVar.getLVTEntry() == null && newVar.getProcessor().getVarName(newVar.getVarVersion()) == null) {
              newVar.getProcessor().setAssignedVarName(newVar.getVarVersion(), name);
              newVar.getProcessor().setVarName(newVar.getVarVersion(), name);
            }
          }
        }
        else if (expr.type == Exprent.EXPRENT_CONST) {
          VarType maxType = mapExprentMaxTypes.get(new VarVersion(expr.id, -1));
          if (maxType != null && maxType.equals(VarType.VARTYPE_CHAR)) {
            ((ConstExprent)expr).setConstType(maxType);
          }
        }
      }

      return 0;
    });

    if (previousVersionsProcessor != null) {
      Map<Integer, VarVersion> oldIndices = previousVersionsProcessor.getMapOriginalVarIndices();
      this.mapOriginalVarIndices = new HashMap<>(mapOriginalVarIndices.size());
      for (Entry<Integer, VarVersion> entry : mapOriginalVarIndices.entrySet()) {
        VarVersion value = entry.getValue();
        VarVersion oldValue = oldIndices.get(value.var);
        value = oldValue != null ? oldValue : value;
        this.mapOriginalVarIndices.put(entry.getKey(), value);
      }
    }
    else {
      this.mapOriginalVarIndices = mapOriginalVarIndices;
    }
  }

  public VarType getVarType(VarVersion pair) {
    return typeProcessor.getVarType(pair);
  }

  public void setVarType(VarVersion pair, VarType type) {
    typeProcessor.setVarType(pair, type);
  }

  public int getVarFinal(VarVersion pair) {
    Integer fin = typeProcessor.getFinalVariables().get(pair);
    return fin == null ? VarProcessor.VAR_FINAL : fin;
  }

  public void setVarFinal(VarVersion pair, int finalType) {
    typeProcessor.getFinalVariables().put(pair, finalType);
  }

  public Map<Integer, VarVersion> getMapOriginalVarIndices() {
    return mapOriginalVarIndices;
  }

  public VarTypeProcessor getTypeProcessor() {
    return typeProcessor;
  }
}