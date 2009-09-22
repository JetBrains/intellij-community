/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.dsl.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author ilyas
 */
public class PsiClassCategory implements PsiEnhancerCategory {

  /**
   * Adds property `methods' into PsiClass
   *
   * @param clazz
   * @return
   */
  public static Collection<PsiMethod> getMethods(PsiClass clazz) {
    return Arrays.asList(clazz.getAllMethods());
  }

  @Nullable
  public static String getQualName(PsiClass clazz) {
    return clazz.getQualifiedName();
  }

  public static boolean hasAnnotation(PsiClass clazz, String annotName) {
    if (annotName == null) return false;
    final Project project = clazz.getProject();
    final String fqn = clazz.getQualifiedName();
    if (fqn == null) return false;
    final PsiClassType type =
      JavaPsiFacade.getInstance(project).getElementFactory().createTypeByFQClassName(fqn, GlobalSearchScope.allScope(project));
    for (PsiAnnotation annotation : type.getAnnotations()) {
      if (annotName.equals(annotation.getQualifiedName())) return true;
    }

    return false;
  }

  @Nullable
  public static PsiAnnotation getAnnotation(PsiClass clazz, String annotName) {
    if (annotName == null) return null;
    final Project project = clazz.getProject();
    final String fqn = clazz.getQualifiedName();
    if (fqn == null) return null;
    final PsiClassType type =
      JavaPsiFacade.getInstance(project).getElementFactory().createTypeByFQClassName(fqn, GlobalSearchScope.allScope(project));
    for (PsiAnnotation annotation : type.getAnnotations()) {
      if (annotName.equals(annotation.getQualifiedName())) return annotation;
    }
    return null;
  }

}
