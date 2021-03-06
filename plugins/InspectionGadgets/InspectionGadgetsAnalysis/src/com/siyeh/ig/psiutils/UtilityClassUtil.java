/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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

public final class UtilityClassUtil {

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
    if (fullCheck && extendsList != null && extendsList.getReferencedTypes().length > 0) {
      return false;
    }
    final PsiReferenceList implementsList = aClass.getImplementsList();
    if (implementsList != null && implementsList.getReferencedTypes().length > 0) {
      return false;
    }
    final int staticMethodCount = countStaticMethods(aClass.getMethods());
    if (staticMethodCount < 0) {
      return false;
    }
    final int staticFieldCount = countStaticFields(aClass.getFields());
    if (staticFieldCount < 0) {
      return false;
    }
    if (fullCheck) {
      for (ImplicitSubclassProvider subclassProvider : ImplicitSubclassProvider.EP_NAME.getExtensions()) {
        if (subclassProvider.isApplicableTo(aClass) && subclassProvider.getSubclassingInfo(aClass) != null) {
          return false;
        }
      }
    }
    return !fullCheck || staticMethodCount != 0 || staticFieldCount != 0;
  }

  /**
   * @param fields  the fields to inspect
   * @return the number of non-private static fields in the array, or -1 if an instance field was found.
   */
  private static int countStaticFields(PsiField[] fields) {
    int count = 0;
    for (PsiField field : fields) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return -1;
      }
      if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
        continue;
      }
      count++;
    }
    return count;
  }

  /**
   * @return -1 if an instance method was found, else the number of non-private static methods in the specified array.
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
