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
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;

public class GrDelegatingClosureParameter implements GrClosureParameter {
  private final GrClosureParameter myDelegate;

  public GrDelegatingClosureParameter(GrClosureParameter delegate) {
    myDelegate = delegate;
  }

  @Nullable
  @Override
  public PsiType getType() {
    return myDelegate.getType();
  }

  @Override
  public boolean isOptional() {
    return myDelegate.isOptional();
  }

  @Nullable
  @Override
  public GrExpression getDefaultInitializer() {
    return myDelegate.getDefaultInitializer();
  }

  @Override
  public boolean isValid() {
    return myDelegate.isValid();
  }

  @Nullable
  @Override
  public String getName() {
    return myDelegate.getName();
  }
}
