/*
 * Copyright 2000-2014 JetBrains s.r.o.
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


import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
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
  private final boolean myReplaceAllOccurrences;

  private PsiType myType = null;
  private final boolean myForceDef;

  public ExtractClosureHelperImpl(IntroduceParameterInfo info,
                                  String name,
                                  boolean declareFinal,
                                  TIntArrayList toRemove,
                                  boolean generateDelegate,
                                  int replaceFieldsWithGetters,
                                  boolean forceReturn,
                                  boolean replaceAllOccurrences,
                                  boolean forceDef) {
    super(info);
    myForceReturn = forceReturn;
    myReplaceAllOccurrences = replaceAllOccurrences;
    myForceDef = forceDef;
    myOwner = info.getToReplaceIn();
    myToSearchFor = info.getToSearchFor();
    myName = name;
    myFinal = declareFinal;
    myToRemove = toRemove;
    myGenerateDelegate = generateDelegate;
    myReplaceFieldsWithGetters = replaceFieldsWithGetters;
  }

  @Override
  @NotNull
  public GrParametersOwner getToReplaceIn() {
    return myOwner;
  }

  @Override
  public PsiElement getToSearchFor() {
    return myToSearchFor;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
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
    return myReplaceAllOccurrences;
  }

  @Override
  public PsiType getSelectedType() {
    if (myForceDef) return null;

    if (myType == null) {
      final GrClosableBlock closure = ExtractClosureProcessorBase.generateClosure(this);
      PsiType type = closure.getType();
      if (type instanceof PsiClassType) {
        final PsiType[] parameters = ((PsiClassType)type).getParameters();
        if (parameters.length == 1 && parameters[0] != null) {
          if (parameters[0].equalsToText(PsiType.VOID.getBoxedTypeName())) {
            type = ((PsiClassType)type).rawType();
          }
        }
      }

      myType = type;
    }
    return myType;
  }

  @Override
  public boolean generateDelegate() {
    return myGenerateDelegate;
  }

  @Override
  public boolean isForceReturn() {
    return myForceReturn;
  }

  @Nullable
  @Override
  public GrVariable getVar() {
    return null;
  }

  @Override
  public GrExpression getExpression() {
    return null;
  }
}
