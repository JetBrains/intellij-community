// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.psi.PsiType;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import it.unimi.dsi.fastutil.ints.IntList;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureHelperImpl;

/**
 * @author Max Medvedev
 */
public class GrIntroduceExpressionSettingsImpl extends ExtractClosureHelperImpl implements GrIntroduceParameterSettings {
  private final GrExpression myExpr;
  private final GrVariable myVar;
  private final PsiType mySelectedType;
  private final boolean myRemoveLocalVar;

  public GrIntroduceExpressionSettingsImpl(IntroduceParameterInfo info,
                                           String name,
                                           boolean declareFinal,
                                           IntList toRemove,
                                           boolean generateDelegate,
                                           @MagicConstant(valuesFromClass = IntroduceParameterRefactoring.class)
                                           int replaceFieldsWithGetters,
                                           GrExpression expr,
                                           GrVariable var,
                                           PsiType selectedType,
                                           boolean replaceAllOccurrences,
                                           boolean removeLocalVar,
                                           boolean forceReturn) {
    super(info, name, declareFinal, toRemove, generateDelegate, replaceFieldsWithGetters, forceReturn, replaceAllOccurrences, false);
    myExpr = expr;
    myVar = var;
    mySelectedType = selectedType;
    myRemoveLocalVar = removeLocalVar;
  }

  @Override
  public GrVariable getVar() {
    return myVar;
  }

  @Override
  public GrExpression getExpression() {
    return myExpr;
  }

  @Override
  public PsiType getSelectedType() {
    return mySelectedType;
  }


  @Override
  public boolean removeLocalVariable() {
    return myRemoveLocalVar;
  }
}
