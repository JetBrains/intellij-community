/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.util;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

enum InitializerRequirement {
  NONE_REQUIRED, VALUE_REQUIRED, OTHER_REQUIRED;

  public static InitializerRequirement calcInitializerRequirement(@Nullable PsiClass psiClass) {
    if (psiClass == null || !psiClass.isAnnotationType()) {
      return NONE_REQUIRED;
    }

    InitializerRequirement r = NONE_REQUIRED;
    final PsiMethod[] methods = psiClass.getMethods();
    for (PsiMethod method : methods) {
      if (PsiUtil.isAnnotationMethod(method)) {
        final PsiAnnotationMethod annotationMethod = (PsiAnnotationMethod)method;
        if (annotationMethod.getDefaultValue() == null) {
          if (PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(annotationMethod.getName())) {
            return VALUE_REQUIRED;
          }
          else {
            r = OTHER_REQUIRED;
          }
        }
      }
    }

    return r;
  }
}
