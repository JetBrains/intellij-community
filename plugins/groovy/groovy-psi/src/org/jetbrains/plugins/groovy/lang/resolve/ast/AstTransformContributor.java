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
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

  @NotNull
  public static Collection<PsiMethod> runContributorsForMethods(@NotNull final GrTypeDefinition clazz) {
    Collection<PsiMethod> result = RecursionManager.doPreventingRecursion(clazz, true, new Computable<Collection<PsiMethod>>() {
      @Override
      public Collection<PsiMethod> compute() {
        Collection<PsiMethod> collector = new ArrayList<PsiMethod>();
        for (final AstTransformContributor contributor : EP_NAME.getExtensions()) {
          contributor.collectMethods(clazz, collector);
        }
        return collector;
      }
    });
    return result == null ? Collections.<PsiMethod>emptyList() : result;
  }

  @NotNull
  public static List<GrField> runContributorsForFields(@NotNull final GrTypeDefinition clazz) {
    List<GrField> fields = RecursionManager.doPreventingRecursion(clazz, true, new Computable<List<GrField>>() {
      @Override
      public List<GrField> compute() {
        List<GrField> collector = new ArrayList<GrField>();
        for (final AstTransformContributor contributor : EP_NAME.getExtensions()) {
          contributor.collectFields(clazz, collector);
        }
        return collector;
      }
    });
    return fields != null ? fields : Collections.<GrField>emptyList();
  }

  @NotNull
  public static List<PsiClass> runContributorsForClasses(@NotNull final GrTypeDefinition clazz) {
    List<PsiClass> fields = RecursionManager.doPreventingRecursion(clazz, true, new Computable<List<PsiClass>>() {
      @Override
      public List<PsiClass> compute() {
        List<PsiClass> collector = ContainerUtil.newArrayList();
        for (final AstTransformContributor contributor : EP_NAME.getExtensions()) {
          contributor.collectClasses(clazz, collector);
        }
        return collector;
      }
    });
    return fields != null ? fields : Collections.<PsiClass>emptyList();
  }
}
