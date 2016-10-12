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

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.data.MethodCallData;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.data.OptionsData;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.data.ReplacementData;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public abstract class Transformation {
  @Nullable
  protected final IElementType operator;

  public Transformation(@Nullable IElementType operator) {
    this.operator = operator;
  }

  @Nullable
  public ReplacementData transform(GrMethodCallExpression callExpression, OptionsData optionsData) {
    GrExpression element = getExpandedElement(callExpression);
    MethodCallData methodInfo = MethodCallData.create(element);
    if (methodInfo == null) return null;

    String replacement = getReplacement(methodInfo, optionsData);
    if (replacement == null) return null;

    return new ReplacementData(element, replacement);
  }

  protected GrExpression getExpandedElement(GrMethodCallExpression callExpression) {
    return callExpression;
  }

  @Nullable
  public abstract String getReplacement(MethodCallData methodInfo, OptionsData optionsData);
}
