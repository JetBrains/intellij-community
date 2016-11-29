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
package com.siyeh.ig.methodmetrics;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class OverlyLongLambdaInspection extends MethodMetricInspection {

  private static final int DEFAULT_LIMIT = 3;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("overly.long.lambda.display.name");
  }

  @Override
  protected int getDefaultLimit() {
    return DEFAULT_LIMIT;
  }

  @Override
  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message("non.comment.source.statements.limit.option");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Integer statementCount = (Integer)infos[0];
    return InspectionGadgetsBundle.message("overly.long.lambda.problem.descriptor", statementCount);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OverlyLongLambdaVisitor();
  }

  private class OverlyLongLambdaVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {
      final PsiElement body = expression.getBody();
      if (!(body instanceof PsiCodeBlock)) {
        return;
      }
      final PsiCodeBlock block = (PsiCodeBlock)body;
      final PsiJavaToken brace = block.getLBrace();
      if (brace == null) {
        return;
      }
      final NCSSVisitor visitor = new NCSSVisitor();
      block.accept(visitor);
      final int count = visitor.getStatementCount();
      if (count <= getLimit()) {
        return;
      }
      registerErrorAtOffset(expression, 0, body.getStartOffsetInParent() + 1, Integer.valueOf(count));
    }
  }
}