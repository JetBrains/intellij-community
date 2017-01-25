/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.changeToMethod.transformations;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import static java.lang.String.format;
import static org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil.replaceExpression;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCOMPARE_TO;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.METHOD_CALL_PRECEDENCE;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.addParenthesesIfNeeded;

class CompareToTransformation extends BinaryTransformation {

  @NotNull
  private IElementType myElementType;

  public CompareToTransformation(@NotNull IElementType elementType) {

    myElementType = elementType;
  }

  @Override
  public void apply(@NotNull GrBinaryExpression expression) {
    GrExpression lhsParenthesized = addParenthesesIfNeeded(getLhs(expression), METHOD_CALL_PRECEDENCE);
    String compare = "";
    if (myElementType != mCOMPARE_TO) {
        compare = format(" %s 0", myElementType.toString());
    }

    replaceExpression(expression, format("%s.compareTo(%s) %s", lhsParenthesized.getText(), getRhs(expression).getText(), compare));
  }

  @Override
  public String getMethod() {
    return "compareTo";
  }
}