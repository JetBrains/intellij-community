// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.dsl.psi;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public final class PsiClassCategory implements PsiEnhancerCategory {

  /**
   * Adds property `methods' into PsiClass
   *
   */
  public static Collection<PsiMethod> getMethods(PsiClass clazz) {
    return Arrays.asList(clazz.getAllMethods());
  }

  public static @Nullable String getQualName(PsiClass clazz) {
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

  public static @Nullable PsiAnnotation getAnnotation(PsiMember clazz, String annotName) {
    if (annotName == null) return null;
    final PsiModifierList list = clazz.getModifierList();
    if (list == null) return null;
    for (PsiAnnotation annotation : list.getAnnotations()) {
      if (annotName.equals(annotation.getQualifiedName())) return annotation;
    }
    return null;
  }

  public static @NotNull Collection<PsiAnnotation> getAnnotations(PsiMember clazz, String annotName) {
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
