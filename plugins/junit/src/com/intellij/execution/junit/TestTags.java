// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.JUnitBundle;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.rt.junit.JUnitStarter;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;

class TestTags extends TestObject {
  TestTags(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }


  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(getConfiguration());
    ProgramParametersUtil.checkWorkingDirectoryExist(
      getConfiguration(), getConfiguration().getProject(), getConfiguration().getConfigurationModule().getModule());
    final String tags = getConfiguration().getPersistentData().getTags();
    if (StringUtil.isEmptyOrSpaces(tags)) {
      throw new RuntimeConfigurationError(JUnitBundle.message("tags.are.not.specified.error.message"));
    }
    final JavaRunConfigurationModule configurationModule = getConfiguration().getConfigurationModule();
    if (getSourceScope() == null) {
      configurationModule.checkForWarning();
    }
    parseAsJavaExpression(tags);
  }

  @Nullable
  @Override
  public SourceScope getSourceScope() {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    return data.getScope().getSourceScope(getConfiguration());
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
      PsiExpression expression = elementFactory.createExpressionFromText(tags.replaceAll("[^)(&|!\\s]", "x"), null);
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
    throw new RuntimeConfigurationWarning(JUnitBundle.message("tag.name.0.must.be.syntactically.valid.warning", tag));
  }

  @Override
  public String suggestActionName() {
    return JUnitBundle.message("action.text.test.tags", getConfiguration().getPersistentData().getTags());
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
    JUnitConfiguration configuration = getConfiguration();
    Module module = configuration.getConfigurationModule().getModule();
    createTempFiles(javaParameters);
    if (module != null && configuration.getTestSearchScope() == TestSearchScope.SINGLE_MODULE) {
      try {
        JUnitStarter.printClassesList(composeDirectoryFilter(module), "", configuration.getPersistentData().getTags().replaceAll(" ", ""), "", myTempFile);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    else {
      //tags written automatically inside
      addClassesListToJavaParameters(Collections.emptyList(), s -> "", "", false, javaParameters);
    }
    return javaParameters;
  }

  @Override
  public RefactoringElementListener getListener(final PsiElement element) {
    return null;
  }
}
