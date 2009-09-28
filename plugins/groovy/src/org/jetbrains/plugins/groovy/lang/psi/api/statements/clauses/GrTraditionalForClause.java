package org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ven
 */
public interface GrTraditionalForClause extends GrForClause {
  GrCondition[] getInitialization();
  
  @Nullable
  GrExpression getCondition();

  GrExpression[] getUpdate();
}
