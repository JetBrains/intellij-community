// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.VarNamesCollector;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.util.*;
import java.util.Map.Entry;

public class VarProcessor {
  public static final int VAR_NON_FINAL = 1;
  public static final int VAR_EXPLICIT_FINAL = 2;
  public static final int VAR_FINAL = 3;

  private final VarNamesCollector varNamesCollector = new VarNamesCollector();
  private final StructMethod method;
  private final MethodDescriptor methodDescriptor;
  private Map<VarVersionPair, String> mapVarNames = new HashMap<>();
  private VarVersionsProcessor varVersions;
  private final Map<VarVersionPair, String> thisVars = new HashMap<>();
  private final Set<VarVersionPair> externalVars = new HashSet<>();
  private final BitSet finalParameters = new BitSet();
  private final int firstParameterVarIndex;
  private final int firstParameterPosition;

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
  }

  public void setDebugVarNames(Map<Integer, String> mapDebugVarNames) {
    if (varVersions == null) {
      return;
    }

    Map<Integer, Integer> mapOriginalVarIndices = varVersions.getMapOriginalVarIndices();

    List<VarVersionPair> listVars = new ArrayList<>(mapVarNames.keySet());
    listVars.sort(Comparator.comparingInt(o -> o.var));

    Map<String, Integer> mapNames = new HashMap<>();

    for (VarVersionPair pair : listVars) {
      String name = mapVarNames.get(pair);

      Integer index = mapOriginalVarIndices.get(pair.var);
      if (index != null) {
        String debugName = mapDebugVarNames.get(index);
        if (debugName != null && TextUtil.isValidIdentifier(debugName, method.getBytecodeVersion())) {
          name = debugName;
        }
      }

      Integer counter = mapNames.get(name);
      mapNames.put(name, counter == null ? counter = 0 : ++counter);

      if (counter > 0) {
        name += String.valueOf(counter);
      }

      mapVarNames.put(pair, name);
    }
  }

  public Integer getVarOriginalIndex(int index) {
    return varVersions == null ? null : varVersions.getMapOriginalVarIndices().get(index);
  }

  public void refreshVarNames(VarNamesCollector vc) {
    Map<VarVersionPair, String> tempVarNames = new HashMap<>(mapVarNames);
    for (Entry<VarVersionPair, String> ent : tempVarNames.entrySet()) {
      mapVarNames.put(ent.getKey(), vc.getFreeName(ent.getValue()));
    }
  }

  public VarNamesCollector getVarNamesCollector() {
    return varNamesCollector;
  }

  public VarType getVarType(VarVersionPair pair) {
    return varVersions == null ? null : varVersions.getVarType(pair);
  }

  public void setVarType(VarVersionPair pair, VarType type) {
    varVersions.setVarType(pair, type);
  }

  public String getVarName(VarVersionPair pair) {
    return mapVarNames == null ? null : mapVarNames.get(pair);
  }

  public void setVarName(VarVersionPair pair, String name) {
    mapVarNames.put(pair, name);
  }

  public Collection<String> getVarNames() {
    return mapVarNames != null ? mapVarNames.values() : Collections.emptySet();
  }

  public int getVarFinal(VarVersionPair pair) {
    return varVersions == null ? VAR_FINAL : varVersions.getVarFinal(pair);
  }

  public void setVarFinal(VarVersionPair pair, int finalType) {
    varVersions.setVarFinal(pair, finalType);
  }

  public Map<VarVersionPair, String> getThisVars() {
    return thisVars;
  }

  public Set<VarVersionPair> getExternalVars() {
    return externalVars;
  }

  public boolean isParameterFinal(VarVersionPair pair) {
    return finalParameters.get(pair.var);
  }

  public void setParameterFinal(VarVersionPair pair) {
    finalParameters.set(pair.var);
  }

  public int getFirstParameterVarIndex() {
    return firstParameterVarIndex;
  }

  public int getFirstParameterPosition() {
    return firstParameterPosition;
  }
}
