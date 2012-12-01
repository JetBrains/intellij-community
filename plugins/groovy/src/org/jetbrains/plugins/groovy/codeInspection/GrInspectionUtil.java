/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Max Medvedev
 */
public class GrInspectionUtil {
  public static boolean isNull(@NotNull GrExpression expression) {
    return "null".equals(expression.getText());
  }

  public static boolean isEquality(@NotNull GrBinaryExpression binaryCondition) {
    final IElementType tokenType = binaryCondition.getOperationTokenType();
    return GroovyTokenTypes.mEQUAL == tokenType;
  }

  public static boolean isInequality(@NotNull GrBinaryExpression binaryCondition) {
    final IElementType tokenType = binaryCondition.getOperationTokenType();
    return GroovyTokenTypes.mNOT_EQUAL == tokenType;
  }
}
