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
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.data.MethodCallData;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.data.OptionsData;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import static com.google.common.base.MoreObjects.firstNonNull;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ComparisonUtils.isComparison;

class CompareToTransformation extends BinaryTransformation {
  public CompareToTransformation() {
    super(null);
  }

  @Override
  protected GrExpression getExpandedElement(GrMethodCallExpression callExpression) {
    PsiElement parent = callExpression.getParent();
    return isComparison(parent) ? (GrExpression)parent 
                                : super.getExpandedElement(callExpression);
  }

  @Override
  protected IElementType getOperator(MethodCallData methodInfo, OptionsData optionsData) {
    IElementType comparison = methodInfo.getComparison();
    if (shouldChangeCompareToEqualityToEquals(optionsData, comparison)) {
      return firstNonNull(comparison, mCOMPARE_TO);
    }
    else {
      return null;
    }
  }

  private static boolean shouldChangeCompareToEqualityToEquals(OptionsData optionsData, IElementType comparison) {
    return !isEquality(comparison) || optionsData.shouldChangeCompareToEqualityToEquals();
  }

  private static boolean isEquality(IElementType comparison) {
    return (comparison == mNOT_EQUAL) || (comparison == mEQUAL);
  }
}