// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl.psi;

import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PsiMethodCategory implements PsiEnhancerCategory {
  public static @Nullable PsiClass getClassType(PsiField field) {
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
