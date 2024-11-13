// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.VarNamesCollector;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTableAttribute.LocalVariable;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.StatementIterator;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class VarProcessor {
  public static final int VAR_NON_FINAL = 1;
  public static final int VAR_EXPLICIT_FINAL = 2;
  public static final int VAR_FINAL = 3;

  public StructMethod getMethod() {
    return method;
  }

  private final VarNamesCollector varNamesCollector = new VarNamesCollector();
  private final StructMethod method;
  private final MethodDescriptor methodDescriptor;
  private Map<VarVersion, String> mapVarNames = new HashMap<>();
  private final Map<VarVersion, String> mapPurgedAssignmentNames = new HashMap<>();
  private final Map<VarVersion, LocalVariable> mapVarLVTs = new HashMap<>();
  private VarVersionsProcessor varVersions;
  private final Map<VarVersion, String> thisVars = new HashMap<>();
  private final Set<VarVersion> externalVars = new HashSet<>();
  private final BitSet finalParameters = new BitSet();
  private final int firstParameterVarIndex;
  private final int firstParameterPosition;
  private final Set<VarVersion> hasNotInsideLVT = new HashSet<>();

  public VarProcessor(StructClass cl, StructMethod mt, MethodDescriptor md) {
    method = mt;
    methodDescriptor = md;
    boolean isEnum = cl.hasModifier(CodeConstants.ACC_ENUM) && DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_ENUM);
    boolean isEnumInit = isEnum && CodeConstants.INIT_NAME.equals(mt.getName());
    firstParameterVarIndex = isEnumInit ? 3 : !mt.hasModifier(CodeConstants.ACC_STATIC) ? 1 : 0;
    firstParameterPosition = isEnumInit ? 2 : 0;
  }

  public void setVarVersions(RootStatement root) {
    VarVersionsProcessor oldProcessor = varVersions;
    varVersions = new VarVersionsProcessor(method, methodDescriptor);
    varVersions.setVarVersions(root, oldProcessor);
  }

  public void setVarDefinitions(Statement root) {
    mapVarNames = new HashMap<>();
    new VarDefinitionHelper(root, method, this).setVarDefinitions();
    fillVariablesOutsideLVT(root);
  }

  private void fillVariablesOutsideLVT(Statement root) {
    Set<VarVersion> definitions = new HashSet<>();
    Set<Integer> insideVariables = new HashSet<>();
    Set<VarVersion> needToRefresh = new HashSet<>();

    StatementIterator.iterate(root, new DirectGraph.ExprentIterator() {
      @Override
      public int processExprent(Exprent exprent) {
        if (exprent.type == Exprent.EXPRENT_VAR) {
          VarExprent var = (VarExprent)exprent;
          if (var.isDefinition()) {
            definitions.add(new VarVersion(var.getVarVersion().var, 0));
          }
          VarVersion version = var.getVarVersion();
          boolean lvt = var.isInsideLVT();
          if (lvt) {
            insideVariables.add(version.var);
            needToRefresh.remove(new VarVersion(version.var, 0) );
          }
          else if (!insideVariables.contains(version.var)) {
            needToRefresh.add(new VarVersion(version.var, 0));
          }
        }
        return 0;
      }
    });
    needToRefresh.retainAll(definitions);
    hasNotInsideLVT.addAll(needToRefresh);
  }

  public void setDebugVarNames(Map<VarVersion, String> mapDebugVarNames) {
    if (varVersions == null) {
      return;
    }

    Map<Integer, VarVersion> mapOriginalVarIndices = varVersions.getMapOriginalVarIndices();

    List<VarVersion> listVars = new ArrayList<>(mapVarNames.keySet());
    listVars.sort(Comparator.<VarVersion, Boolean>comparing(o -> !mapPurgedAssignmentNames.containsKey(o))
                    .thenComparingInt(o -> o.var));

    Map<String, Integer> mapNames = new HashMap<>();

    for (VarVersion pair : listVars) {
      String name = mapVarNames.get(pair);

      boolean lvtName = false;
      VarVersion key = mapOriginalVarIndices.get(pair.var);
      if (key != null && !hasNotInsideLVT.contains(pair)) {
        String debugName = mapDebugVarNames.get(key);
        if (debugName != null && TextUtil.isValidIdentifier(debugName, method.getBytecodeVersion())) {
          name = debugName;
          lvtName = true;
        }
      }


      Integer counter = mapNames.get(name);
      mapNames.put(name, counter == null ? counter = 0 : ++counter);

      if (counter > 0 && !lvtName && !mapPurgedAssignmentNames.containsKey(pair)) {
        name += String.valueOf(counter);
      }

      mapVarNames.put(pair, name);
    }
  }

  public Integer getVarOriginalIndex(int index) {
    if (varVersions == null) {
      return null;
    }
    final VarVersion pair = varVersions.getMapOriginalVarIndices().get(index);
    return pair == null ? null : pair.var;
  }

  public void refreshVarNames(VarNamesCollector vc) {
    Map<VarVersion, String> tempVarNames = new HashMap<>(mapVarNames);
    for (Entry<VarVersion, String> ent : tempVarNames.entrySet()) {
      mapVarNames.put(ent.getKey(), mapPurgedAssignmentNames.containsKey(ent.getKey()) ?
                                    ent.getValue() : vc.getFreeName(ent.getValue()));
    }
  }

  public VarNamesCollector getVarNamesCollector() {
    return varNamesCollector;
  }

  public VarType getVarType(VarVersion pair) {
    return varVersions == null ? null : varVersions.getVarType(pair);
  }

  public void setVarType(VarVersion pair, VarType type) {
    if (varVersions != null) {
      varVersions.setVarType(pair, type);
    }
  }

  public String getVarName(VarVersion pair) {
    return mapVarNames == null ? null : mapVarNames.get(pair);
  }

  public void setVarName(VarVersion pair, String name) {
    mapVarNames.put(pair, name);
  }

  public String getAssignedVarName(VarVersion pair) {
    return mapPurgedAssignmentNames.get(pair);
  }

  public void setAssignedVarName(VarVersion pair, String name) {
    if (name == null) {
      mapPurgedAssignmentNames.remove(pair);
      return;
    }
    mapPurgedAssignmentNames.put(pair, name);
  }

  public Collection<String> getVarNames() {
    return mapVarNames != null ? mapVarNames.values() : Collections.emptySet();
  }

  public int getVarFinal(VarVersion pair) {
    return varVersions == null ? VAR_FINAL : varVersions.getVarFinal(pair);
  }

  public void setVarFinal(VarVersion pair, int finalType) {
    varVersions.setVarFinal(pair, finalType);
  }

  public Map<VarVersion, String> getThisVars() {
    return thisVars;
  }

  public Set<VarVersion> getExternalVars() {
    return externalVars;
  }

  public boolean isParameterFinal(VarVersion pair) {
    return finalParameters.get(pair.var);
  }

  public void setParameterFinal(VarVersion pair) {
    finalParameters.set(pair.var);
  }

  public int getFirstParameterVarIndex() {
    return firstParameterVarIndex;
  }

  public int getFirstParameterPosition() {
    return firstParameterPosition;
  }

  public List<LocalVariable> getCandidates(int origindex) {
    if (!hasLVT())
        return null;
    return method.getLocalVariableAttr().matchingVars(origindex).collect(Collectors.toList());
  }

  public void findLVT(VarExprent exprent, int start) {
    if (!hasLVT()) {
      return;
    }

    method.getLocalVariableAttr().getVariables()
      .filter(v -> v.getVersion().var == exprent.getIndex() && v.getStart() == start).findFirst().ifPresent(exprent::setLVTEntry);

    if (exprent.getIndex() >= firstParameterPosition + methodDescriptor.params.length) {
      method.getLocalVariableAttr().getVariables()
        .filter(v -> v.getVersion().var == exprent.getIndex() && v.getStart() <= start).findFirst()
        .ifPresent(ignore -> exprent.setInsideLVT(true));
    }
  }

  public boolean hasLVT() {
    return method.getLocalVariableAttr() != null;
  }


  public VarVersionsProcessor getVarVersions() {
    return varVersions;
  }

  public void setVarLVTEntry(VarVersion var, LocalVariable lvt) {
    mapVarLVTs.put(var, lvt);
  }

  public LocalVariable getVarLVTEntry(VarVersion var) {
    return mapVarLVTs.get(var);
  }
}
