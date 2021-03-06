// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.tryCast;

public final class SealedUtils {

  private SealedUtils() {}

  public static void fillPermitsList(@NotNull PsiClass parent, @NotNull Collection<String> missingInheritors) {
    PsiReferenceList permitsList = parent.getPermitsList();
    PsiFileFactory factory = PsiFileFactory.getInstance(parent.getProject());
    if (permitsList == null) {
      PsiReferenceList implementsList = Objects.requireNonNull(parent.getImplementsList());
      String permitsClause = StreamEx.of(missingInheritors).sorted().joining(",", "permits ", "");
      parent.addAfter(createPermitsClause(factory, permitsClause), implementsList);
    }
    else {
      Stream<String> curClasses = Arrays.stream(permitsList.getReferenceElements()).map(PsiJavaCodeReferenceElement::getQualifiedName);
      String permitsClause = StreamEx.of(missingInheritors).append(curClasses).sorted().joining(",", "permits ", "");
      permitsList.replace(createPermitsClause(factory, permitsClause));
    }
  }

  @NotNull
  private static PsiReferenceList createPermitsClause(@NotNull PsiFileFactory factory, @NotNull String permitsClause) {
    PsiJavaFile javaFile = (PsiJavaFile)factory.createFileFromText(JavaLanguage.INSTANCE, "class __Dummy " + permitsClause + "{}");
    PsiClass newClass = javaFile.getClasses()[0];
    return Objects.requireNonNull(newClass.getPermitsList());
  }

  public static boolean hasSealedParent(@NotNull PsiClass psiClass) {
    return StreamEx.of(psiClass.getExtendsListTypes())
      .append(psiClass.getImplementsListTypes())
      .map(r -> r.resolve())
      .anyMatch(parent -> parent != null && parent.hasModifierProperty(PsiModifier.SEALED));
  }

  public static Collection<String> findSameFileInheritors(@NotNull PsiClass psiClass, PsiClass @NotNull ... classesToExclude) {
    GlobalSearchScope fileScope = GlobalSearchScope.fileScope(psiClass.getContainingFile());
    return DirectClassInheritorsSearch.search(psiClass, fileScope)
      .filtering(inheritor -> !ArrayUtil.contains(inheritor, classesToExclude))
      .mapping(inheritor -> inheritor.getQualifiedName())
      .findAll();
  }

  /**
   * Removes exChild class reference from permits list of a parent.
   * If this was the last element in permits list then sealed modifier of parent class is removed.
   */
  public static void removeFromPermitsList(@NotNull PsiClass parent, @NotNull PsiClass exChild) {
    PsiReferenceList permitsList = parent.getPermitsList();
    if (permitsList == null) return;
    PsiJavaCodeReferenceElement[] childRefs = permitsList.getReferenceElements();
    PsiJavaCodeReferenceElement exChildRef = ContainerUtil.find(childRefs, ref -> ref.resolve() == exChild);
    if (exChildRef == null) return;
    exChildRef.delete();
    if (childRefs.length != 1) return;
    PsiModifierList modifiers = parent.getModifierList();
    if (modifiers == null) return;
    modifiers.setModifierProperty(PsiModifier.SEALED, false);
  }

  public static @Nullable String checkInheritor(@NotNull PsiJavaFile parentFile, @Nullable PsiJavaModule module, @NotNull PsiClass inheritor) {
    @PropertyKey(resourceBundle = JavaBundle.BUNDLE)
    String result = null;
    if (PsiUtil.isLocalOrAnonymousClass(inheritor)) {
      result = "intention.error.make.sealed.class.has.anonymous.or.local.inheritors";
    }
    else if (module == null) {
      PsiJavaFile file = tryCast(inheritor.getContainingFile(), PsiJavaFile.class);
      if (file == null) {
        result = "intention.error.make.sealed.class.inheritors.not.in.java.file";
      }
      else if (!parentFile.getPackageName().equals(file.getPackageName())) {
        result = "intention.error.make.sealed.class.different.packages";
      }
    }
    else if (JavaModuleGraphUtil.findDescriptorByElement(inheritor) != module) {
      result = "intention.error.make.sealed.class.different.modules";
    }
    return result;
  }
}
