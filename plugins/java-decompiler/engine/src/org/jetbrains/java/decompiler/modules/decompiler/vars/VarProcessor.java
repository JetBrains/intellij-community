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

  private HashMap<VarVersionPaar, String> mapVarNames = new HashMap<VarVersionPaar, String>();

  private VarVersionsProcessor varvers;

  private HashMap<VarVersionPaar, String> thisvars = new HashMap<VarVersionPaar, String>();

  private HashSet<VarVersionPaar> externvars = new HashSet<VarVersionPaar>();

  public void setVarVersions(RootStatement root) {

    varvers = new VarVersionsProcessor();
    varvers.setVarVersions(root);
  }

  public void setVarDefinitions(Statement root) {
    mapVarNames = new HashMap<VarVersionPaar, String>();

    VarDefinitionHelper defproc = new VarDefinitionHelper(root,
                                                          (StructMethod)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD),
                                                          this);
    defproc.setVarDefinitions();
  }

  public void setDebugVarNames(Map<Integer, String> mapDebugVarNames) {
    if (varvers == null) {
      return;
    }

    HashMap<Integer, Integer> mapOriginalVarIndices = varvers.getMapOriginalVarIndices();

    List<VarVersionPaar> listVars = new ArrayList<VarVersionPaar>(mapVarNames.keySet());
    Collections.sort(listVars, new Comparator<VarVersionPaar>() {
      public int compare(VarVersionPaar o1, VarVersionPaar o2) {
        return o1.var > o2.var ? 1 : (o1.var == o2.var ? 0 : -1);
      }
    });

    HashMap<String, Integer> mapNames = new HashMap<String, Integer>();

    for (VarVersionPaar varpaar : listVars) {
      String name = mapVarNames.get(varpaar);

      Integer orindex = mapOriginalVarIndices.get(varpaar.var);
      if (orindex != null && mapDebugVarNames.containsKey(orindex)) {
        name = mapDebugVarNames.get(orindex);
      }

      Integer counter = mapNames.get(name);
      mapNames.put(name, counter == null ? counter = new Integer(0) : ++counter);

      if (counter > 0) {
        name += String.valueOf(counter);
      }

      mapVarNames.put(varpaar, name);
    }
  }

  public void refreshVarNames(VarNamesCollector vc) {

    HashMap<VarVersionPaar, String> tempVarNames = new HashMap<VarVersionPaar, String>(mapVarNames);
    for (Entry<VarVersionPaar, String> ent : tempVarNames.entrySet()) {
      mapVarNames.put(ent.getKey(), vc.getFreeName(ent.getValue()));
    }
  }


  public VarType getVarType(VarVersionPaar varpaar) {
    return varvers == null ? null : varvers.getVarType(varpaar);
  }

  public void setVarType(VarVersionPaar varpaar, VarType type) {
    varvers.setVarType(varpaar, type);
  }

  public String getVarName(VarVersionPaar varpaar) {
    return mapVarNames == null ? null : mapVarNames.get(varpaar);
  }

  public void setVarName(VarVersionPaar varpaar, String name) {
    mapVarNames.put(varpaar, name);
  }

  public int getVarFinal(VarVersionPaar varpaar) {
    return varvers == null ? VarTypeProcessor.VAR_FINAL : varvers.getVarFinal(varpaar);
  }

  public void setVarFinal(VarVersionPaar varpaar, int finaltype) {
    varvers.setVarFinal(varpaar, finaltype);
  }

  public HashMap<VarVersionPaar, String> getThisvars() {
    return thisvars;
  }

  public HashSet<VarVersionPaar> getExternvars() {
    return externvars;
  }
}
