// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.extract.closure;


import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import it.unimi.dsi.fastutil.ints.IntList;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
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
  private final GrParameterListOwner myOwner;
  private final PsiElement myToSearchFor;

  private final String myName;
  private final boolean myFinal;
  private final IntList myToRemove;
  private final boolean myGenerateDelegate;
  @MagicConstant(valuesFromClass = IntroduceParameterRefactoring.class)
  private final int myReplaceFieldsWithGetters;
  private final boolean myForceReturn;
  private final boolean myReplaceAllOccurrences;

  private PsiType myType = null;
  private final boolean myForceDef;

  public ExtractClosureHelperImpl(IntroduceParameterInfo info,
                                  String name,
                                  boolean declareFinal,
                                  IntList toRemove,
                                  boolean generateDelegate,
                                  @MagicConstant(valuesFromClass = IntroduceParameterRefactoring.class)
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
  public GrParameterListOwner getToReplaceIn() {
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
  public IntList parametersToRemove() {
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
          if (parameters[0].equalsToText(CommonClassNames.JAVA_LANG_VOID)) {
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
