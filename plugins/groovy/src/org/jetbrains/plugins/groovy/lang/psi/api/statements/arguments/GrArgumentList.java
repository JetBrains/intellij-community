/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ilyas
 * Plain Argumanet list with parentheses. Cannot contain closure arguments, they are placed outside.
 */
public interface GrArgumentList extends GroovyPsiElement {
  @NotNull GrNamedArgument[] getNamedArguments();

  @NotNull GrExpression[] getExpressionArguments();

  GrArgumentList replaceWithArgumentList(GrArgumentList newArgList) throws IncorrectOperationException;

  boolean isIndexPropertiesList();

  @Nullable
  PsiElement getLeftParen();

  @Nullable
  PsiElement getRightParen();

  int getExpressionArgumentIndex(GrExpression arg);

  @Nullable
  GrExpression removeArgument(int argNumber);

  GrNamedArgument addNamedArgument(GrNamedArgument namedArgument);
}