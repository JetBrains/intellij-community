// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GrExpressionList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public interface GrTraditionalForClause extends GrForClause {

  @Nullable
  GrCondition getInitialization();

  @Nullable
  GrExpression getCondition();

  @Nullable
  GrExpressionList getUpdate();
}
