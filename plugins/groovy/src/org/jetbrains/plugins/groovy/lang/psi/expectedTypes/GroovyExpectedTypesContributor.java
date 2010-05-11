package org.jetbrains.plugins.groovy.lang.psi.expectedTypes;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.List;

/**
 * @author peter
 */
public abstract class GroovyExpectedTypesContributor {
  public static final ExtensionPointName<GroovyExpectedTypesContributor> EP_NAME = ExtensionPointName.create("org.intellij.groovy.expectedTypesContributor");

  public abstract List<TypeConstraint> calculateTypeConstraints(@NotNull GrExpression expression);
}
