/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.junit;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.util.IncorrectOperationException;

import java.util.Collections;

class TestTags extends TestObject {
  public TestTags(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }


  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(getConfiguration());
    ProgramParametersUtil.checkWorkingDirectoryExist(
      getConfiguration(), getConfiguration().getProject(), getConfiguration().getConfigurationModule().getModule());
    final String tags = getConfiguration().getPersistentData().getTags();
    if (StringUtil.isEmptyOrSpaces(tags)) {
      throw new RuntimeConfigurationError("Tags are not specified");
    }
    final JavaRunConfigurationModule configurationModule = getConfiguration().getConfigurationModule();
    if (getSourceScope() == null) {
      configurationModule.checkForWarning();
    }
    parseAsJavaExpression(tags);
  }

  /**
   * Parse tag as java polyadic expression with boolean operations as top priority
   * 
   * 1+2 is accepted as tag
   * !1+2 is parsed as !tag
   * IncorrectOperationException is thrown e.g. on unbalanced parenthesis 
   */
  private void parseAsJavaExpression(String tags) throws RuntimeConfigurationWarning {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getConfiguration().getProject());
    try {
      PsiExpression expression = elementFactory.createExpressionFromText(tags.replaceAll("[^)(&|!]", "x"), null);
      if (expression instanceof PsiPolyadicExpression) {
        IElementType tokenType = ((PsiPolyadicExpression)expression).getOperationTokenType();
        if (tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR) {
          invalidTagException(tags);
        }
      }
    }
    catch (IncorrectOperationException e) {
      invalidTagException(tags);
    }
  }

  private static void invalidTagException(String tag) throws RuntimeConfigurationWarning {
    throw new RuntimeConfigurationWarning("Tag name [" + tag + "] must be syntactically valid");
  }

  @Override
  public String suggestActionName() {
    return "Tests of " + getConfiguration().getPersistentData().getCategory();
  }

  @Override
  public boolean isConfiguredByElement(JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {
    return false;
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = super.createJavaParameters();
    //tags written automatically inside
    addClassesListToJavaParameters(Collections.emptyList(), s -> "", "", true, javaParameters);
    return javaParameters;
  }

  @Override
  public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration) {
    return null;
  }
}
