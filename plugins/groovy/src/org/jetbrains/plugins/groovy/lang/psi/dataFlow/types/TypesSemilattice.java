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
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author ven
 */
public class TypesSemilattice implements Semilattice<TypeDfaState> {
  private final PsiManager myManager;

  public TypesSemilattice(PsiManager manager) {
    myManager = manager;
  }

  public TypeDfaState join(ArrayList<TypeDfaState> ins) {
    if (ins.size() == 0) return new TypeDfaState();

    TypeDfaState result = new TypeDfaState(ins.get(0));
    for (int i = 1; i < ins.size(); i++) {
      result.joinState(ins.get(i), myManager);
    }
    return result;
  }

  public boolean eq(TypeDfaState e1, TypeDfaState e2) {
    return e1.contentsEqual(e2);
  }
}

class TypeDfaState {
  private final Map<String, PsiType> myVarTypes;

  TypeDfaState() {
    myVarTypes = ContainerUtil.newHashMap();
  }

  TypeDfaState(TypeDfaState another) {
    myVarTypes = ContainerUtil.newHashMap(another.myVarTypes);
  }

  void joinState(TypeDfaState another, PsiManager manager) {
    for (Map.Entry<String, PsiType> entry : another.myVarTypes.entrySet()) {
      final String name = entry.getKey();
      final PsiType t1 = entry.getValue();
      if (myVarTypes.containsKey(name)) {
        final PsiType t2 = myVarTypes.get(name);
        if (t1 != null && t2 != null) {
          myVarTypes.put(name, TypesUtil.getLeastUpperBound(t1, t2, manager));
        }
        else {
          myVarTypes.put(name, null);
        }
      }
    }
  }

  boolean contentsEqual(TypeDfaState another) {
    if (myVarTypes.size() != another.myVarTypes.size()) {
      return false;
    }

    for (String name : myVarTypes.keySet()) {
      if (!areTypesErasureEqual(another.myVarTypes, name)) {
        return false;
      }
    }
    return true;
  }

  private boolean areTypesErasureEqual(Map<String, PsiType> another, String name) {
    if (!another.containsKey(name)) return false;
    final PsiType t1 = myVarTypes.get(name);
    final PsiType t2 = another.get(name);
    if (t1 == null || t2 == null) {
      if (t1 != null || t2 != null) return false;
    }
    else {
      if (!TypeConversionUtil.erasure(t1).equals(TypeConversionUtil.erasure(t2))) return false;
    }
    return true;
  }

  Map<String, PsiType> getBindings() {
    return myVarTypes;
  }

  void putType(String variableName, PsiType type) {
    myVarTypes.put(variableName, type);
  }
}