package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.psi.GenericsUtil;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.HashMap;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.Map;

/**
 * @author ven
 */
public class TypesSemilattice implements Semilattice<Map<String, PsiType>> {
  PsiManager myManager;

  public TypesSemilattice(PsiManager manager) {
    myManager = manager;
  }

  public Map<String, PsiType> cap(Map<String, PsiType> e1, Map<String, PsiType> e2) {
    Map<String, PsiType> result = new HashMap<String, PsiType>();
    for (Map.Entry<String, PsiType> entry : e1.entrySet()) {
      final String name = entry.getKey();
      final PsiType t1 = entry.getValue();
      if (e2.containsKey(name)) {
        final PsiType t2 = e2.get(name);
        if (t1.isAssignableFrom(t2)) result.put(name, t1);
        else if (t2.isAssignableFrom(t1)) result.put(name, t2);
        else result.put(name, GenericsUtil.getLeastUpperBound(t1, t2, myManager));
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
      if (!t1.equals(t2)) return false;
    }
    return true;
  }
}
