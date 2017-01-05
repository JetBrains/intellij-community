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
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.data.MethodCallData;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.data.OptionsData;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLNOT;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.BoolUtils.isNegation;

class AsBooleanTransformation extends UnaryTransformation {
  public static final String NEGATION = mLNOT.toString();
  public static final String DOUBLE_NEGATION = NEGATION + NEGATION;

  @NotNull
  @Override
  protected GrExpression getExpandedElement(@NotNull GrMethodCallExpression callExpression) {
    PsiElement parent = callExpression.getParent();
    if (isNegation(parent)) {
      return (GrExpression)parent;
    }
    else {
      return super.getExpandedElement(callExpression);
    }
  }

  @Override
  @Nullable
  protected String getPrefix(MethodCallData methodInfo, OptionsData optionsData) {
    if (methodInfo.isNegated()) {
      return NEGATION;
    }
    else if (isImplicitlyBoolean(methodInfo)) {
      return "";
    }
    else if (optionsData.useDoubleNegation()) {
      return DOUBLE_NEGATION;
    }
    else {
      return null;
    }
  }

  private static boolean isImplicitlyBoolean(MethodCallData methodInfo) {
    return methodInfo.getBackingElement().getParent() instanceof GrIfStatement;
  }
}
