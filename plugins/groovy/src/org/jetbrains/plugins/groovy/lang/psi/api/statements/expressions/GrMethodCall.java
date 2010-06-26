package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;

/**
 * @author peter
 */
public interface GrMethodCall extends GrCall {

  GrExpression getInvokedExpression();

  @Nullable
  GrArgumentList getArgumentList();

  @Nullable
  PsiMethod resolveMethod();
}
