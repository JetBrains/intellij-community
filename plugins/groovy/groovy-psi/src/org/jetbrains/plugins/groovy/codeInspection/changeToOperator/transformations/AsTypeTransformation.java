// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.ChangeToOperatorInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import static java.lang.String.format;
import static org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil.replaceExpression;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kAS;

final class AsTypeTransformation extends SimpleBinaryTransformation {
   AsTypeTransformation() {
    super(kAS);
  }

  @Override
  public void apply(@NotNull GrMethodCall methodCall, @NotNull ChangeToOperatorInspection.Options options) {
    GrReferenceExpression rhs = (GrReferenceExpression) getRhs(methodCall);

    GrExpression rhsQualifierExpression = rhs.isQualified() ? rhs.getQualifierExpression() : rhs;

    if (rhsQualifierExpression == null) return;

    replaceExpression(methodCall, format("%s %s %s", getLhs(methodCall).getText(), kAS, rhsQualifierExpression.getText()));
  }

  @Override
  public boolean couldApplyInternal(@NotNull GrMethodCall methodCall, @NotNull ChangeToOperatorInspection.Options options) {
    if (!super.couldApplyInternal(methodCall, options)) return false;
    GrExpression rhs = getRhs(methodCall);
    return ResolveUtil.resolvesToClass(rhs);
  }
}
