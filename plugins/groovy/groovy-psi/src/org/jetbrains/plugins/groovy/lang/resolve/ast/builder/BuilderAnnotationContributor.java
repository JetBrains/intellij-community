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
package org.jetbrains.plugins.groovy.lang.resolve.ast.builder;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.resolve.ast.AstTransformContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.GrBuilderStrategySupport.Members;

import java.util.Collection;

public class BuilderAnnotationContributor extends AstTransformContributor {

  @Override
  public void collectMethods(@NotNull GrTypeDefinition clazz, final Collection<PsiMethod> collector) {
    collector.addAll(collectAll(clazz).methods);
  }

  @Override
  public void collectClasses(@NotNull GrTypeDefinition clazz, Collection<PsiClass> collector) {
    collector.addAll(collectAll(clazz).classes);
  }

  @Override
  public void collectFields(@NotNull GrTypeDefinition clazz, Collection<GrField> collector) {
    collector.addAll(collectAll(clazz).fields);
  }

  private static Members collectAll(final GrTypeDefinition clazz) {
    return CachedValuesManager.getCachedValue(clazz, new CachedValueProvider<Members>() {
      @Nullable
      @Override
      public Result<Members> compute() {
        final Members result = new Members();
        for (GrBuilderStrategySupport strategySupport : GrBuilderStrategySupport.EP.getExtensions()) {
          result.addFrom(strategySupport.process(clazz));
        }
        return Result.create(result, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }
}
