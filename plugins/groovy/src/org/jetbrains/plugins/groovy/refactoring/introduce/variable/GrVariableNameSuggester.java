// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduce.variable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.NameValidator;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * @author Max Medvedev
 */
public class GrVariableNameSuggester {
  private final GrIntroduceContext myContext;
  private final NameValidator myValidator;

  public GrVariableNameSuggester(GrIntroduceContext context, NameValidator validator) {
    myContext = context;
    myValidator = validator;
  }

  public @NotNull LinkedHashSet<String> suggestNames() {
    GrExpression expression = myContext.getExpression() != null ? myContext.getExpression() : myContext.getStringPart().getLiteral();
    return new LinkedHashSet<>(Arrays.asList(GroovyNameSuggestionUtil.suggestVariableNames(expression, myValidator)));
  }
}
