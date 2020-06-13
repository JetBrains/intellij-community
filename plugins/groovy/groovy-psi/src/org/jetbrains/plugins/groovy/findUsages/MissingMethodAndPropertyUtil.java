// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;

/**
 * @author Max Medvedev
 */
public final class MissingMethodAndPropertyUtil {
  public static boolean isMethodMissing(PsiMethod method) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    return method.getName().equals("methodMissing") && (parameters.length == 2 || parameters.length == 1);
  }


  public static boolean isPropertyMissing(PsiMethod method) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    return method.getName().equals("propertyMissing") && (parameters.length == 2 || parameters.length == 1);
  }
}
