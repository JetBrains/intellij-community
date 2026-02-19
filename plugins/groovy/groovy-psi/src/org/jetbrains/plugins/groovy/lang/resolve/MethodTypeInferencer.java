// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.List;

public class MethodTypeInferencer implements Computable<PsiType> {
  private final GrStatementOwner myBlock;

  public MethodTypeInferencer(GrStatementOwner block) {
    myBlock = block;
  }

  @Override
  public @Nullable PsiType compute() {
    List<GrStatement> returns = ControlFlowUtils.collectReturns(myBlock);
    if (returns.isEmpty()) return PsiTypes.voidType();

    PsiType result = null;
    PsiManager manager = myBlock.getManager();
    for (GrStatement returnStatement : returns) {
      GrExpression value = null;
      if (returnStatement instanceof GrReturnStatement) {
        value = ((GrReturnStatement)returnStatement).getReturnValue();
      }
      else if (returnStatement instanceof GrExpression) {
        value = (GrExpression)returnStatement;
      }

      if (value != null) {
        result = TypesUtil.getLeastUpperBoundNullable(result, value.getType(), manager);
      }
    }

    return result;
  }
}
