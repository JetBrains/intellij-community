// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.TestCaseLoader;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestModeFlagListener;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;

/**
 * Allows to customize the test execution environment for the entire test execution without modifying the source code
 * of tests. To specify a test execution policy, set the system property {@link #SYSTEM_PROPERTY_NAME} to the FQ name
 * of a class implementing this interface.
 */
public abstract class IdeaTestExecutionPolicy implements TestModeFlagListener {
  public static final String SYSTEM_PROPERTY_NAME = "idea.test.execution.policy";

  protected IdeaTestExecutionPolicy() {
    TestModeFlags.addListener(this);
  }

  /**
   * Performs the setup required in this test execution mode.
   */
  public void setUp(Project project, Disposable testRootDisposable, String testDataPath) {
  }

  /**
   * Creates the fixture for working with temporary files.
   */
  public TempDirTestFixture createTempDirTestFixture() {
    return new LightTempDirTestFixtureImpl(true);
  }

  /**
   * If true, the test method is invoked in the EDT. Otherwise, it runs on the test runner thread.
   */
  public boolean runInDispatchThread() {
    return true;
  }

  public void testFileConfigured(@NotNull PsiFile file) {
  }

  public void testDirectoryConfigured(@NotNull PsiDirectory directory) {
  }

  public void beforeCheckResult(@NotNull PsiFile file) {
  }

  public String getHomePath() {
    return null;
  }

  public @Nullable String getPerTestTempDirName() {
    return null;
  }

  public void waitForHighlighting(@NotNull Project project, @NotNull Editor editor) {
  }

  public void inspectionToolEnabled(@NotNull Project project, @NotNull InspectionToolWrapper<?, ?> toolWrapper, @NotNull Disposable disposable) {
  }

  @Override
  public <T> void testModeFlagChanged(@NotNull Key<T> key, @Nullable T value) {
  }

  private static IdeaTestExecutionPolicy ourCurrent;

  public static @Nullable IdeaTestExecutionPolicy current() {
    if (ourCurrent != null) return ourCurrent;
    String policyClassName = System.getProperty(SYSTEM_PROPERTY_NAME);
    if (policyClassName == null) return null;
    try {
      Class<?> policyClass = Class.forName(policyClassName);
      ourCurrent = (IdeaTestExecutionPolicy)policyClass.getDeclaredConstructor().newInstance();
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
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

    SkipWithExecutionPolicy annotation = TestCaseLoader.getAnnotationInHierarchy(testCaseClass, SkipWithExecutionPolicy.class);
    return annotation == null || !annotation.value().equals(current.getName());
  }

  protected abstract String getName();
}
