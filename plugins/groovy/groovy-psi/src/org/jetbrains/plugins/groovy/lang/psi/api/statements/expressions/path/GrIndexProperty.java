/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ilyas
 */
public interface GrIndexProperty extends GrExpression, GrCallExpression {
  @NotNull
  GrExpression getInvokedExpression();

  @Override
  @NotNull
  GrArgumentList getArgumentList();

  /**
   * infer type of getAt() applicable
   */
  @Nullable
  PsiType getGetterType();

  /**
   * infer type of putAt() applicable
   */
  @Nullable
  PsiType getSetterType();

  @NotNull
  GroovyResolveResult[] multiResolveGetter(boolean incomplete);

  @NotNull
  GroovyResolveResult[] multiResolveSetter(boolean incomplete);
}
