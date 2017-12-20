/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInspection.inheritance.ImplicitSubclassProvider;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class UtilityClassUtil {

  private UtilityClassUtil() {}

  public static boolean hasPrivateEmptyOrNoConstructor(@NotNull PsiClass aClass) {
    final PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      return true;
    }
    if (constructors.length != 1) {
      return false;
    }
    final PsiMethod constructor = constructors[0];
    return constructor.hasModifierProperty(PsiModifier.PRIVATE) && ControlFlowUtils.isEmptyCodeBlock(constructor.getBody());
  }

  public static boolean isUtilityClass(@NotNull PsiClass aClass) {
    return isUtilityClass(aClass, true);
  }

  public static boolean isUtilityClass(@NotNull PsiClass aClass, boolean fullCheck) {
    if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
      return false;
    }
    if (aClass instanceof PsiTypeParameter || aClass instanceof PsiAnonymousClass) {
      return false;
    }
    final PsiReferenceList extendsList = aClass.getExtendsList();
    if (fullCheck && extendsList != null && extendsList.getReferenceElements().length > 0) {
      return false;
    }
    final PsiReferenceList implementsList = aClass.getImplementsList();
    if (implementsList != null && implementsList.getReferenceElements().length > 0) {
      return false;
    }
    final PsiMethod[] methods = aClass.getMethods();
    final int staticMethodCount = countStaticMethods(methods);
    if (staticMethodCount < 0) {
      return false;
    }
    final PsiField[] fields = aClass.getFields();
    if (!allFieldsStatic(fields)) {
      return false;
    }
    if (fullCheck) {
      for (ImplicitSubclassProvider subclassProvider : ImplicitSubclassProvider.EP_NAME.getExtensions()) {
        if (subclassProvider.isApplicableTo(aClass) && subclassProvider.getSubclassingInfo(aClass) != null) {
          return false;
        }
      }
    }
    return (!fullCheck || staticMethodCount != 0) || fields.length != 0;
  }

  private static boolean allFieldsStatic(PsiField[] fields) {
    for (final PsiField field : fields) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
    }
    return true;
  }

  /**
   * @return -1 if an instance method was found, else the number of static methods in the class
   */
  private static int countStaticMethods(PsiMethod[] methods) {
    int staticCount = 0;
    for (final PsiMethod method : methods) {
      if (method.isConstructor()) {
        continue;
      }
      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        return -1;
      }
      if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
        continue;
      }
      staticCount++;
    }
    return staticCount;
  }
}
