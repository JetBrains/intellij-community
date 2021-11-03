// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.GrModifierConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
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
  Collection<@NotNull GrField> getFields();

  @NotNull
  Collection<PsiField> getAllFields(boolean includeSynthetic);

  @NotNull
  Collection<PsiClass> getInnerClasses();

  @NotNull
  List<PsiClassType> getImplementsTypes();

  @NotNull
  List<PsiClassType> getExtendsTypes();

  boolean hasModifierProperty(@NotNull GrModifierList list, @GrModifierConstant @NotNull String name);

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

  @NotNull
  PsiClassType eraseClassType(@NotNull PsiClassType classType);

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

  /**
   * Adds method to the context class
   * @param method the method to add
   * @param prepend if true, adds method before others
   */
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

  void addModifier(@NotNull GrModifierList modifierList, @GrModifierConstant @NotNull String modifier);

  void addAnnotation(@NotNull GrAnnotation annotation);

  @NotNull
  MemberBuilder getMemberBuilder();
}
