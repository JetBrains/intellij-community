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

package org.jetbrains.plugins.groovy.dsl.psi;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

  public static boolean hasAnnotation(PsiMember clazz, String annotName) {
    if (annotName == null) return false;
    final PsiModifierList list = clazz.getModifierList();
    if (list == null) return false;
    for (PsiAnnotation annotation : list.getAnnotations()) {
      if (annotName.equals(annotation.getQualifiedName())) return true;
    }

    return false;
  }

  @Nullable
  public static PsiAnnotation getAnnotation(PsiMember clazz, String annotName) {
    if (annotName == null) return null;
    final PsiModifierList list = clazz.getModifierList();
    if (list == null) return null;
    for (PsiAnnotation annotation : list.getAnnotations()) {
      if (annotName.equals(annotation.getQualifiedName())) return annotation;
    }
    return null;
  }

  @NotNull
  public static Collection<PsiAnnotation> getAnnotations(PsiMember clazz, String annotName) {
   final ArrayList<PsiAnnotation> list = new ArrayList<>();
    if (annotName == null) return list;
    final PsiModifierList mlist = clazz.getModifierList();
    if (mlist == null) return list;
    for (PsiAnnotation annotation : mlist.getAnnotations()) {
      if (annotName.equals(annotation.getQualifiedName())) {
        list.add(annotation);
      }
    }
    return list;

  }

}
