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
import com.intellij.psi.PsiMethod;
import com.intellij.util.Consumer;
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
  public static final ExtensionPointName<AstTransformContributor> EP_NAME =
    ExtensionPointName.create("org.intellij.groovy.astTransformContributor");

  public void collectMethods(@NotNull final GrTypeDefinition clazz, Consumer<PsiMethod> collector) {

  }

  public void collectFields(@NotNull final GrTypeDefinition clazz, Consumer<GrField> collector) {

  }

  @NotNull
  public static Collection<PsiMethod> runContributorsForMethods(@NotNull final GrTypeDefinition clazz) {
    Collection<PsiMethod> result = RecursionManager.doPreventingRecursion(clazz, true, new Computable<Collection<PsiMethod>>() {
      @Override
      public Collection<PsiMethod> compute() {
        final Collection<PsiMethod> collector = new ArrayList<PsiMethod>();
        for (final AstTransformContributor contributor : EP_NAME.getExtensions()) {
          contributor.collectMethods(clazz, new Consumer<PsiMethod>() {
            @Override
            public void consume(PsiMethod method) {
              collector.add(method);
            }
          });
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
        final List<GrField> collector = new ArrayList<GrField>();
        for (final AstTransformContributor contributor : EP_NAME.getExtensions()) {
          contributor.collectFields(clazz, new Consumer<GrField>() {
            @Override
            public void consume(GrField field) {
              collector.add(field);
            }
          });
        }
        return collector;
      }
    });
    return fields != null ? fields : Collections.<GrField>emptyList();
  }
}
