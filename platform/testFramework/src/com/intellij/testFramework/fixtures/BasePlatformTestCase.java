// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

/**
 * This is a "light" JUnit 3-compatible {@link UsefulTestCase} which is based around a {@link CodeInsightTestFixture}.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/testing-plugins.html">Testing Plugins (IntelliJ Platform Docs)</a>
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html">Light and Heavy Tests (IntelliJ Platform Docs)</a>
 * @see CodeInsightFixtureTestCase for "heavy" tests that require access to the real FS or changes project roots.
 */
public abstract class BasePlatformTestCase extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;

  @Override
  public @NotNull Disposable getTestRootDisposable() {
    return myFixture == null ? super.getTestRootDisposable() : myFixture.getTestRootDisposable();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture = createMyFixture();

    myFixture.setTestDataPath(getTestDataPath());
    myFixture.setUp();
  }

  protected CodeInsightTestFixture createMyFixture() {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder =
      factory.createLightFixtureBuilder(getProjectDescriptor(), getTestName(false));
    IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();

    return IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, createTempDirTestFixture());
  }

  protected TempDirTestFixture createTempDirTestFixture() {
    IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
    return policy != null
           ? policy.createTempDirTestFixture()
           : new LightTempDirTestFixtureImpl(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFixture = null;
      super.tearDown();
    }
  }

  /**
   * Returns relative path to the test data.
   *
   * @see #getTestDataPath()
   */
  protected String getBasePath() {
    return "";
  }

  protected LightProjectDescriptor getProjectDescriptor() {
    return null;
  }

  /**
   * Returns the absolute path to the test data.
   * Not intended to be overridden in tests written as part of the IntelliJ IDEA codebase;
   * it must be overridden in plugins which use the test framework.
   *
   * @see #getBasePath()
   * @see <a href="https://plugins.jetbrains.com/docs/intellij/test-project-and-testdata-directories.html">Test Project and Testdata Directories</a>
   */
  protected String getTestDataPath() {
    String path = isCommunity() ? PlatformTestUtil.getCommunityPath() : IdeaTestExecutionPolicy.getHomePathWithPolicy();
    return StringUtil.trimEnd(FileUtil.toSystemIndependentName(path), "/") + '/' +
           StringUtil.trimStart(FileUtil.toSystemIndependentName(getBasePath()), "/");
  }

  /**
   * For IntelliJ IDEA codebase only.
   */
  protected boolean isCommunity() {
    return false;
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    if (isWriteActionRequired()) {
      WriteCommandAction.writeCommandAction(getProject()).run(() -> super.runTestRunnable(testRunnable));
    }
    else {
      super.runTestRunnable(testRunnable);
    }
  }

  protected boolean isWriteActionRequired() {
    return false;
  }

  protected Project getProject() {
    return myFixture.getProject();
  }

  protected PsiManager getPsiManager() {
    return PsiManager.getInstance(getProject());
  }

  protected PsiFile createLightFile(FileType fileType, String text) {
    return PsiFileFactory.getInstance(getProject()).createFileFromText("a." + fileType.getDefaultExtension(), fileType, text);
  }

  public PsiFile createLightFile(String fileName, Language language, String text) {
    return PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, language, text, false, true);
  }

  protected @NotNull Module getModule() {
    return myFixture.getModule();
  }
}
