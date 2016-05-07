/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.Collection;
import java.util.List;

@SuppressWarnings("unused")
public interface TransformationContext {

  @NotNull
  GrTypeDefinition getCodeClass();

  @NotNull
  Collection<PsiMethod> getMethods();

  @NotNull
  Collection<GrField> getFields();

  @NotNull
  Collection<PsiClass> getInnerClasses();

  @NotNull
  List<PsiClassType> getImplementsTypes();

  @NotNull
  List<PsiClassType> getExtendsTypes();

  @NotNull
  PsiManager getManager();

  @NotNull
  String getClassName();

  @Nullable
  PsiClass getSuperClass();

  @Nullable
  PsiAnnotation getAnnotation(@NotNull String fqn);

  default boolean hasAnnotation(@NotNull String fqn) {
    return getAnnotation(fqn) != null;
  }

  @NotNull
  Collection<PsiMethod> findMethodsByName(@NotNull String name, boolean checkBases);

  void addMethod(@NotNull PsiMethod method);

  void addMethods(@NotNull PsiMethod[] methods);

  void addMethods(@NotNull Collection<? extends PsiMethod> methods);

  void removeMethod(@NotNull PsiMethod codeMethod);

  void addField(@NotNull GrField field);

  void addInnerClass(@NotNull PsiClass innerClass);

  void setSuperClass(@NotNull PsiClassType type);

  void addInterface(@NotNull PsiClassType type);
}
