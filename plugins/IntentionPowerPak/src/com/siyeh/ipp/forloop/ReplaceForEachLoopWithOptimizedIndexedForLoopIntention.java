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
package com.siyeh.ipp.forloop;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;

/**
 * User: anna
 * Date: 8/1/12
 */
public class ReplaceForEachLoopWithOptimizedIndexedForLoopIntention extends ReplaceForEachLoopWithIndexedForLoopIntention {
  @Override
  protected void createForLoopDeclaration(PsiForeachStatement statement,
                                          PsiExpression iteratedValue,
                                          boolean isArray,
                                          String iteratedValueText,
                                          StringBuilder newStatement, final String indexText) {
    
    final String lengthText;
    if (isArray) {
      lengthText = createVariableName(iteratedValueText + "Length", PsiType.INT, statement);
    }
    else {
      lengthText = createVariableName(iteratedValueText + "Size", PsiType.INT, statement);
    }

    newStatement.append("for(int ");
    newStatement.append(indexText);
    newStatement.append(" = 0, ");
    newStatement.append(lengthText);
    newStatement.append(" = ");
    if (iteratedValue instanceof PsiTypeCastExpression) {
      newStatement.append('(');
      newStatement.append(iteratedValueText);
      newStatement.append(')');
    }
    else {
      newStatement.append(iteratedValueText);
    }
    if (isArray) {
      newStatement.append(".length;");
    }
    else {
      newStatement.append(".size();");
    }
    newStatement.append(indexText);
    newStatement.append('<');
    newStatement.append(lengthText);
    newStatement.append(';');
    newStatement.append(indexText);
    newStatement.append("++)");
    newStatement.append("{ ");
  }
}
