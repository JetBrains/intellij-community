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
import com.intellij.util.containers.HashMap;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author ven
 */
public class TypesSemilattice implements Semilattice<Map<String, PsiType>> {
  PsiManager myManager;

  public TypesSemilattice(PsiManager manager) {
    myManager = manager;
  }

  public Map<String, PsiType> join(ArrayList<Map<String, PsiType>> ins) {
    if (ins.size() == 0) return new HashMap<String, PsiType>();

    Map<String, PsiType> result = new HashMap<String, PsiType>(ins.get(0));

    for (int i = 1; i < ins.size(); i++) {
      Map<String, PsiType> map = ins.get(i);

      for (Map.Entry<String, PsiType> entry : map.entrySet()) {
        final String name = entry.getKey();
        final PsiType t1 = entry.getValue();
        if (result.containsKey(name)) {
          final PsiType t2 = result.get(name);
          if (t1 != null && t2 != null) {
            result.put(name, TypesUtil.getLeastUpperBound(t1, t2, myManager));
          }
          else {
            result.put(name, null);
          }
        }
      }
    }

    return result;
  }

  public boolean eq(Map<String, PsiType> e1, Map<String, PsiType> e2) {
    if (e1.size() != e2.size()) return false;

    for (Map.Entry<String, PsiType> entry : e1.entrySet()) {
      final String name = entry.getKey();
      if (!e2.containsKey(name)) return false;
      final PsiType t1 = entry.getValue();
      final PsiType t2 = e2.get(name);
      if (t1 == null || t2 == null) {
        if (t1 != null || t2 != null) return false;
      }
      else {
        if (!t1.equals(t2)) return false;
      }
    }
    return true;
  }
}
