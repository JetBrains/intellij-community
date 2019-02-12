// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * Represents a Groovy lambda expression body in a single expression form.
 */
public interface GrExpressionLambdaBody extends GrLambdaBody {
  @NotNull
  GrExpression getExpression();
}
