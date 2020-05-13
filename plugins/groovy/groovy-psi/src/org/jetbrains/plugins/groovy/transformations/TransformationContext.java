// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.transformations.dsl.MemberBuilder;

import java.util.Collection;
import java.util.List;

public interface TransformationContext {

  @NotNull
  Project getProject();

  @NotNull
  PsiManager getManager();

  @NotNull
  JavaPsiFacade getPsiFacade();

  @NotNull
  GrTypeDefinition getCodeClass();

  @NotNull
  PsiClass getHierarchyView();

  @NotNull
  PsiClassType getClassType();

  @NotNull
  Collection<PsiMethod> getMethods();

  @NotNull
  Collection<GrField> getFields();

  @NotNull
  Collection<PsiField> getAllFields(boolean includeSynthetic);

  @NotNull
  Collection<PsiClass> getInnerClasses();

  @NotNull
  List<PsiClassType> getImplementsTypes();

  @NotNull
  List<PsiClassType> getExtendsTypes();

  @NotNull
  default List<PsiClassType> getSuperTypes() {
    return ContainerUtil.concat(getExtendsTypes(), getImplementsTypes());
  }

  @NotNull
  default GlobalSearchScope getResolveScope() {
    return getCodeClass().getResolveScope();
  }

  @Nullable
  String getClassName();

  @Nullable
  PsiClass getSuperClass();

  @Nullable
  PsiAnnotation getAnnotation(@NotNull String fqn);

  default boolean isInheritor(@NotNull String fqn) {
    PsiClass baseClass = getPsiFacade().findClass(fqn, getResolveScope());
    return baseClass != null && isInheritor(baseClass);
  }

  boolean isInheritor(@NotNull PsiClass baseClass);

  @NotNull
  Collection<PsiMethod> findMethodsByName(@NotNull String name, boolean checkBases);

  default void addMethod(@NotNull PsiMethod method) {
    addMethod(method, false);
  }

  void addMethod(@NotNull PsiMethod method, boolean prepend);

  void addMethods(PsiMethod @NotNull [] methods);

  void addMethods(@NotNull Collection<? extends PsiMethod> methods);

  void removeMethod(@NotNull PsiMethod codeMethod);

  void addField(@NotNull GrField field);

  void addInnerClass(@NotNull PsiClass innerClass);

  void setSuperType(@NotNull String fqn);

  void setSuperType(@NotNull PsiClassType type);

  void addInterface(@NotNull String fqn);

  void addInterface(@NotNull PsiClassType type);

  @NotNull
  MemberBuilder getMemberBuilder();
}
