// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.testDiscovery;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.TestObject;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.testDiscovery.TestDiscoverySearchHelper;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.util.FunctionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

abstract class JUnitTestDiscoveryRunnableState extends TestObject {
  JUnitTestDiscoveryRunnableState(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  protected abstract String getChangeList();
  protected abstract Pair<String, String> getPosition();


  @Override
  protected TestSearchScope getScope() {
    return getConfiguration().getConfigurationModule().getModule() != null ? TestSearchScope.MODULE_WITH_DEPENDENCIES : TestSearchScope.WHOLE_PROJECT;
  }

  @Override
  protected boolean forkPerModule() {
    return getConfiguration().getConfigurationModule().getModule() == null;
  }

  @Override
  protected PsiElement retrievePsiElement(Object pattern) {
    if (pattern instanceof String) {
      final String className = StringUtil.getPackageName((String)pattern, ',');
      if (!pattern.equals(className)) {
        final Project project = getConfiguration().getProject();
        PsiManager manager = PsiManager.getInstance(project);
        final SourceScope sourceScope = getSourceScope();
        final GlobalSearchScope globalSearchScope = sourceScope != null ? sourceScope.getGlobalSearchScope()
                                                                        : GlobalSearchScope.projectScope(project);
        return ClassUtil.findPsiClass(manager, className, null, true, globalSearchScope);
      }
    }
    return null;
  }

  @SuppressWarnings("deprecation")
  @Override
  public @Nullable SearchForTestsTask createSearchingForTestsTask() {
    return createSearchingForTestsTask(new LocalTargetEnvironment(new LocalTargetEnvironmentRequest()));
  }

  @Override
  public @Nullable SearchForTestsTask createSearchingForTestsTask(@NotNull TargetEnvironment targetEnvironment) {
    return new SearchForTestsTask(getConfiguration().getProject(), getServerSocket()) {

      private Set<String> myPatterns;

      @Override
      protected void search() {
        myPatterns = TestDiscoverySearchHelper.search(getProject(), getPosition(), getChangeList(), getConfiguration().getTestFrameworkId());
      }

      @Override
      protected void onFound() {
        if (myPatterns != null) {
          try {
            addClassesListToJavaParameters(myPatterns, FunctionUtil.id(), "", false, getJavaParameters());
          }
          catch (ExecutionException ignored) {
          }
        }
      }
    };
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = super.createJavaParameters();
    createTempFiles(javaParameters);

    createServerSocket(javaParameters);
    return javaParameters;
  }

  @Override
  public String suggestActionName() {
    return "";
  }

  @Override
  public RefactoringElementListener getListener(PsiElement element) {
    return null;
  }

  @Override
  public boolean isConfiguredByElement(JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {
    return false;
  }
}
