// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.forloop;

import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiType;

public class ReplaceForEachLoopWithOptimizedIndexedForLoopIntention extends ReplaceForEachLoopWithIndexedForLoopIntention {
  @Override
  protected void createForLoopDeclaration(PsiForeachStatement statement,
                                          boolean isArray,
                                          String iteratedValueText,
                                          final String indexText,
                                          StringBuilder newStatement) {
    final String lengthText = isArray
                              ? createVariableName(iteratedValueText + "Length", PsiType.INT, statement)
                              : createVariableName(iteratedValueText + "Size", PsiType.INT, statement);

    newStatement.append("for(int ");
    newStatement.append(indexText);
    newStatement.append("=0,");
    newStatement.append(lengthText);
    newStatement.append('=');
    newStatement.append(iteratedValueText);
    newStatement.append(isArray ? ".length;" : ".size();");
    newStatement.append(indexText);
    newStatement.append('<');
    newStatement.append(lengthText);
    newStatement.append(';');
    newStatement.append(indexText);
    newStatement.append("++){");
  }
}
