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

package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;

/**
 * @author ilyas
 */
public interface GrBinaryExpression extends GrExpression, PsiPolyVariantReference {

  /**
   * @return left operand of binary expression
   */
  @NotNull
  GrExpression getLeftOperand();

  /**
   * @return right operand of binary expression
   */
  @Nullable
  GrExpression getRightOperand();

  @NotNull
  IElementType getOperationTokenType();

  @NotNull
  PsiElement getOperationToken();

  @NotNull
  @Override
  GroovyResolveResult[] multiResolve(final boolean incompleteCode);
}
