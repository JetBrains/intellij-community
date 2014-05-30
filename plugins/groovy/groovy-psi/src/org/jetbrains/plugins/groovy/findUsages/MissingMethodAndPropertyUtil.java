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
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;

/**
 * @author Max Medvedev
 */
public class MissingMethodAndPropertyUtil {
  public static boolean isMethodMissing(PsiMethod method) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    return method.getName().equals("methodMissing") && (parameters.length == 2 || parameters.length == 1);
  }


  public static boolean isPropertyMissing(PsiMethod method) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    return method.getName().equals("propertyMissing") && (parameters.length == 2 || parameters.length == 1);
  }
}
