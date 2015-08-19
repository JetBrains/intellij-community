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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.Collection;

/**
 * @author Max Medvedev
 */
public abstract class AstTransformContributor {

  public static final ExtensionPointName<AstTransformContributor> EP_NAME = ExtensionPointName.create("org.intellij.groovy.astTransformContributor");

  @Deprecated
  public void collectMethods(@NotNull final GrTypeDefinition clazz, Collection<PsiMethod> collector) {

  }

  @Deprecated
  public void collectFields(@NotNull final GrTypeDefinition clazz, Collection<GrField> collector) {

  }

  /**
   * Subclasses should override this method.
   */
  @NotNull
  @SuppressWarnings("deprecation")
  public Members collect(@NotNull final GrTypeDefinition clazz) {
    final Members members = Members.create();
    collectMethods(clazz, members.getMethods());
    collectFields(clazz, members.getFields());
    return members;
  }

  @NotNull
  public static Members runContributors(@NotNull final GrTypeDefinition clazz) {
    Members result = RecursionManager.doPreventingRecursion(clazz, true, new Computable<Members>() {
      @Override
      public Members compute() {
        Members members = Members.create();
        for (final AstTransformContributor contributor : EP_NAME.getExtensions()) {
          members.addFrom(contributor.collect(clazz));
        }
        return members;
      }
    });
    return result == null ? Members.EMPTY : result;
  }
}
