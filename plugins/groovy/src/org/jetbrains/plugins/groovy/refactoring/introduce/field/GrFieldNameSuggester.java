// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.NameValidator;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * @author Max Medvedev
 */
public class GrFieldNameSuggester {

  private final GrIntroduceContext myContext;
  private final NameValidator myValidator;
  private final boolean myForStatic;

  public GrFieldNameSuggester(GrIntroduceContext context, NameValidator validator, boolean forStatic) {
    myContext = context;
    myValidator = validator;
    myForStatic = forStatic;
  }

  public @NotNull LinkedHashSet<String> suggestNames() {
    final GrExpression expression = myContext.getExpression();
    final GrVariable var = myContext.getVar();
    final StringPartInfo stringPart = myContext.getStringPart();

    if (expression != null) {
      return new LinkedHashSet<>(Arrays.asList(GroovyNameSuggestionUtil.suggestVariableNames(expression, myValidator, myForStatic)));
    }
    else if (stringPart != null) {
      return new LinkedHashSet<>(
        Arrays.asList(GroovyNameSuggestionUtil.suggestVariableNames(stringPart.getLiteral(), myValidator, myForStatic)));
    }
    else {
      assert var != null;
      return new LinkedHashSet<>(Arrays.asList(GroovyNameSuggestionUtil.suggestVariableNameByType(var.getType(), myValidator)));
    }
  }}
