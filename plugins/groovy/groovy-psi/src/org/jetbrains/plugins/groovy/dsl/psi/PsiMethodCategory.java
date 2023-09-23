package org.jetbrains.plugins.groovy.dsl.psi;

import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class PsiMethodCategory implements PsiEnhancerCategory {
  @Nullable
  public static PsiClass getClassType(PsiField field) {
    final PsiType type = field.getType();
    return PsiCategoryUtil.getClassType(type, field);
  }

  @SuppressWarnings({"unused", "rawtypes", "unchecked"})
  public static Map getParamStringVector(PsiMethod method) {
    Map result = new LinkedHashMap<>();
    int idx = 1;
    for (PsiParameter p : method.getParameterList().getParameters()) {
      result.put("value" + idx, p.getType().getCanonicalText());
      idx++;
    }
    return result;
  }
}
