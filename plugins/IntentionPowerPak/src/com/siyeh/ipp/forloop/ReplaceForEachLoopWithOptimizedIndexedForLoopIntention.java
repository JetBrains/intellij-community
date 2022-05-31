// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.forloop;

import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiType;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;

public class ReplaceForEachLoopWithOptimizedIndexedForLoopIntention extends ReplaceForEachLoopWithIndexedForLoopIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.for.each.loop.with.optimized.indexed.for.loop.intention.family.name");
  }

  @Override
  public @NotNull String getText() {
    return IntentionPowerPackBundle.message("replace.for.each.loop.with.optimized.indexed.for.loop.intention.name");
  }

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
