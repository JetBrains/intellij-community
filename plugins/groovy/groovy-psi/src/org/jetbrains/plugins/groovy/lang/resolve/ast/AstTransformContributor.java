/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Max Medvedev
 */
public abstract class AstTransformContributor {

  public static final ExtensionPointName<AstTransformContributor> EP_NAME = ExtensionPointName.create("org.intellij.groovy.astTransformContributor");

  public void collectMethods(@NotNull final GrTypeDefinition clazz, Collection<PsiMethod> collector) {
  }

  public void collectFields(@NotNull final GrTypeDefinition clazz, Collection<GrField> collector) {
  }

  public void collectClasses(@NotNull final GrTypeDefinition clazz, Collection<PsiClass> collector) {
  }

  public void collectImplementsTypes(GrTypeDefinition clazz, Collection<PsiClassType> collector) {
  }

  public static Collection<PsiMethod> runContributorsForMethods(final GrTypeDefinition clazz) {
    Collection<PsiMethod> result = RecursionManager.doPreventingRecursion(clazz, true, new Computable<Collection<PsiMethod>>() {
      @Override
      public Collection<PsiMethod> compute() {
        final ArrayList<PsiMethod> result = ContainerUtil.newArrayList();
        for (final AstTransformContributor contributor : EP_NAME.getExtensions()) {
          contributor.collectMethods(clazz, result);
        }
        return result;
      }
    });
    return result == null ? Collections.<PsiMethod>emptyList() : result;
  }

  public static Collection<GrField> runContributorsForFields(final GrTypeDefinition clazz) {
    Collection<GrField> result = RecursionManager.doPreventingRecursion(clazz, true, new Computable<Collection<GrField>>() {
      @Override
      public Collection<GrField> compute() {
        final ArrayList<GrField> result = ContainerUtil.newArrayList();
        for (final AstTransformContributor contributor : EP_NAME.getExtensions()) {
          contributor.collectFields(clazz, result);
        }
        return result;
      }
    });
    return result == null ? Collections.<GrField>emptyList() : result;
  }

  public static Collection<PsiClass> runContributorsForClasses(final GrTypeDefinition clazz) {
    Collection<PsiClass> result = RecursionManager.doPreventingRecursion(clazz, true, new Computable<Collection<PsiClass>>() {
      @Override
      public Collection<PsiClass> compute() {
        final ArrayList<PsiClass> result = ContainerUtil.newArrayList();
        for (final AstTransformContributor contributor : EP_NAME.getExtensions()) {
          contributor.collectClasses(clazz, result);
        }
        return result;
      }
    });
    return result == null ? Collections.<PsiClass>emptyList() : result;
  }

  public static Collection<PsiClassType> runContributorsForImplementsTypes(final GrTypeDefinition clazz) {
    Collection<PsiClassType> result = RecursionManager.doPreventingRecursion(clazz, true, new Computable<Collection<PsiClassType>>() {
      @Override
      public Collection<PsiClassType> compute() {
        final ArrayList<PsiClassType> result = ContainerUtil.newArrayList();
        for (final AstTransformContributor contributor : EP_NAME.getExtensions()) {
          contributor.collectImplementsTypes(clazz, result);
        }
        return result;
      }
    });
    return result == null ? Collections.<PsiClassType>emptyList() : result;
  }
}
