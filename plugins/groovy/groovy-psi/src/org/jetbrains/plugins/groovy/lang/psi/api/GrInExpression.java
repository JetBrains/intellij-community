// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;

public interface GrInExpression extends GrBinaryExpression {

  static boolean isNegated(@NotNull GrInExpression expression) {
    return expression.getOperationTokenType() == GroovyElementTypes.T_NOT_IN;
  }
}
