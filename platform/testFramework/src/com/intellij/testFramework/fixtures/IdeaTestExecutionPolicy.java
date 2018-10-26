// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class IdeaTestExecutionPolicy {
  public abstract void setUp(Project project, Disposable testRootDisposable, String testDataPath);
  public abstract TempDirTestFixture createTempDirTestFixture();
  public abstract boolean runInDispatchThread();
  public void testFileConfigured(@NotNull PsiFile file) {
  }

  public void beforeCheckResult(@NotNull PsiFile file) {
  }

  public String getHomePath() {
    return null;
  }

  public String getPerTestTempDirName() {
    return null;
  }

  public void waitForHighlighting(@NotNull Project project, @NotNull Editor editor) {
  }

  public void inspectionToolEnabled(@NotNull Project project, @NotNull InspectionToolWrapper<?, ?> toolWrapper, @NotNull Disposable disposable) {
  }

  private static IdeaTestExecutionPolicy ourCurrent = null;

  @Nullable
  public static IdeaTestExecutionPolicy current() {
    if (ourCurrent != null) return ourCurrent;
    String policyClassName = System.getProperty("idea.test.execution.policy");
    if (policyClassName == null) return null;
    try {
      Class<?> policyClass = Class.forName(policyClassName);
      ourCurrent = (IdeaTestExecutionPolicy)  policyClass.newInstance();
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    return ourCurrent;
  }

  public static String getHomePathWithPolicy() {
    IdeaTestExecutionPolicy policy = current();
    if (policy != null) {
      String policyHomePath = policy.getHomePath();
      if (policyHomePath != null) {
        return policyHomePath;
      }
    }
    return PathManager.getHomePath();
  }

  public boolean canRun(Class<? extends UsefulTestCase> testCaseClass) {
    IdeaTestExecutionPolicy current = current();
    if (current == null) return true;

    for (Class<?> clazz = testCaseClass; clazz != null; clazz = clazz.getSuperclass()) {
      SkipWithExecutionPolicy annotation = clazz.getAnnotation(SkipWithExecutionPolicy.class);
      if (annotation != null) {
        return !annotation.value().equals(current.getName());
      }
    }
    return true;
  }

  protected abstract String getName();
}
