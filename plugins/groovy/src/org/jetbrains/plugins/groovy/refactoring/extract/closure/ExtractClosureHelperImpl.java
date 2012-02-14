/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.extract.closure;


import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractInfoHelperBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceParameterSettings;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.IntroduceParameterInfo;

/**
 * @author Max Medvedev
 */
public class ExtractClosureHelperImpl extends ExtractInfoHelperBase implements GrIntroduceParameterSettings {
  private final GrParametersOwner myOwner;
  private final PsiElement myToSearchFor;

  private final String myName;
  private final boolean myFinal;
  private final TIntArrayList myToRemove;
  private final boolean myGenerateDelegate;
  private final int myReplaceFieldsWithGetters;
  private final boolean myForceReturn;

  public ExtractClosureHelperImpl(IntroduceParameterInfo info,
                                  String name,
                                  boolean declareFinal,
                                  TIntArrayList toRemove,
                                  boolean generateDelegate,
                                  int replaceFieldsWithGetters,
                                  boolean forceReturn) {
    super(info);
    myForceReturn = forceReturn;
    myOwner = info.getToReplaceIn();
    myToSearchFor = info.getToSearchFor();
    myName = name;
    myFinal = declareFinal;
    myToRemove = toRemove;
    myGenerateDelegate = generateDelegate;
    myReplaceFieldsWithGetters = replaceFieldsWithGetters;
  }

  @NotNull
  public GrParametersOwner getToReplaceIn() {
    return myOwner;
  }

  public PsiElement getToSearchFor() {
    return myToSearchFor;
  }

  public String getName() {
    return myName;
  }

  public boolean declareFinal() {
    return myFinal;
  }

  @Override
  public TIntArrayList parametersToRemove() {
    return myToRemove;
  }

  @Override
  public int replaceFieldsWithGetters() {
    return myReplaceFieldsWithGetters;
  }

  @Override
  public boolean removeLocalVariable() {
    return false;
  }

  @Override
  public boolean replaceAllOccurrences() {
    return false;
  }

  @Override
  public PsiType getSelectedType() {
    return JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, myOwner);
  }

  public boolean generateDelegate() {
    return myGenerateDelegate;
  }

  public boolean isForceReturn() {
    return myForceReturn;
  }

  @Override
  public GrVariable getVar() {
    return null;
  }

  @Override
  public GrExpression getExpression() {
    return null;
  }
}
