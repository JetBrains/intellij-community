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
package org.jetbrains.java.decompiler.modules.decompiler.vars;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.VarNamesCollector;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.VarType;

import java.util.*;
import java.util.Map.Entry;

public class VarProcessor {

  private Map<VarVersionPair, String> mapVarNames = new HashMap<VarVersionPair, String>();
  private VarVersionsProcessor varVersions;
  private final Map<VarVersionPair, String> thisVars = new HashMap<VarVersionPair, String>();
  private final Set<VarVersionPair> externalVars = new HashSet<VarVersionPair>();

  public void setVarVersions(RootStatement root) {
    varVersions = new VarVersionsProcessor();
    varVersions.setVarVersions(root);
  }

  public void setVarDefinitions(Statement root) {
    mapVarNames = new HashMap<VarVersionPair, String>();

    StructMethod mt = (StructMethod)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD);
    new VarDefinitionHelper(root, mt, this).setVarDefinitions();
  }

  public void setDebugVarNames(Map<Integer, String> mapDebugVarNames) {
    if (varVersions == null) {
      return;
    }

    Map<Integer, Integer> mapOriginalVarIndices = varVersions.getMapOriginalVarIndices();

    List<VarVersionPair> listVars = new ArrayList<VarVersionPair>(mapVarNames.keySet());
    Collections.sort(listVars, new Comparator<VarVersionPair>() {
      @Override
      public int compare(VarVersionPair o1, VarVersionPair o2) {
        return o1.var - o2.var;
      }
    });

    Map<String, Integer> mapNames = new HashMap<String, Integer>();

    for (VarVersionPair pair : listVars) {
      String name = mapVarNames.get(pair);

      Integer index = mapOriginalVarIndices.get(pair.var);
      if (index != null && mapDebugVarNames.containsKey(index)) {
        name = mapDebugVarNames.get(index);
      }

      Integer counter = mapNames.get(name);
      mapNames.put(name, counter == null ? counter = new Integer(0) : ++counter);

      if (counter > 0) {
        name += String.valueOf(counter);
      }

      mapVarNames.put(pair, name);
    }
  }

  public void refreshVarNames(VarNamesCollector vc) {
    Map<VarVersionPair, String> tempVarNames = new HashMap<VarVersionPair, String>(mapVarNames);
    for (Entry<VarVersionPair, String> ent : tempVarNames.entrySet()) {
      mapVarNames.put(ent.getKey(), vc.getFreeName(ent.getValue()));
    }
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

  public int getVarFinal(VarVersionPair pair) {
    return varVersions == null ? VarTypeProcessor.VAR_FINAL : varVersions.getVarFinal(pair);
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
}
