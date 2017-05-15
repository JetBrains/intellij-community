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

public class AsTypeTransformation extends SimpleBinaryTransformation {
  public AsTypeTransformation() {
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
