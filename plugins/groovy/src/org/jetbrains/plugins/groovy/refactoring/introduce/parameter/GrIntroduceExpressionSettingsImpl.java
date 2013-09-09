/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.psi.PsiType;
import gnu.trove.TIntArrayList;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.extract.closure.ExtractClosureHelperImpl;

import static com.intellij.refactoring.IntroduceParameterRefactoring.*;

/**
 * @author Max Medvedev
 */
public class GrIntroduceExpressionSettingsImpl extends ExtractClosureHelperImpl implements GrIntroduceParameterSettings {
  private final GrExpression myExpr;
  private final GrVariable myVar;
  private final PsiType mySelectedType;

  public GrIntroduceExpressionSettingsImpl(IntroduceParameterInfo info,
                                           String name,
                                           boolean declareFinal,
                                           TIntArrayList toRemove,
                                           boolean generateDelegate,
                                           @MagicConstant(intValues = {REPLACE_FIELDS_WITH_GETTERS_ALL, REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, REPLACE_FIELDS_WITH_GETTERS_NONE}) int replaceFieldsWithGetters,
                                           GrExpression expr,
                                           GrVariable var,
                                           PsiType selectedType,
                                           boolean forceReturn) {
    super(info, name, declareFinal, toRemove, generateDelegate, replaceFieldsWithGetters, forceReturn, false);
    myExpr = expr;
    myVar = var;
    mySelectedType = selectedType;
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


}
