package org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ven
 */
public interface GrForInClause extends GrForClause {
  @Nullable
  GrExpression getIteratedExpression();
}
