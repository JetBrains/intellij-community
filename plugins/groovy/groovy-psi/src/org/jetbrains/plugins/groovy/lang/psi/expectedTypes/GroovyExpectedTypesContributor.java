// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.expectedTypes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.List;

/**
 * @author peter
 */
public abstract class GroovyExpectedTypesContributor {
  public abstract List<TypeConstraint> calculateTypeConstraints(@NotNull GrExpression expression);
}
