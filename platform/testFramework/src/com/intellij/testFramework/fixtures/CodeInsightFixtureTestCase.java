// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * This is a "heavy" JUnit 3-compatible {@link UsefulTestCase} which is based around a {@link CodeInsightTestFixture}.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/testing-plugins.html">Testing Plugins (IntelliJ Platform Docs)</a>
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html">Light and Heavy Tests (IntelliJ Platform Docs)</a>
 * @see BasePlatformTestCase for "light" tests
 */
public abstract class CodeInsightFixtureTestCase<T extends ModuleFixtureBuilder<?>> extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;
  protected Module myModule;

  @Override
  protected final void runBare(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    // don't create application in EDT
    TestApplicationManager.getInstance();
    super.runBare(testRunnable);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String name = getClass().getName() + "." + getName();
    TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name);
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    T moduleFixtureBuilder = projectBuilder.addModule(getModuleBuilderClass());
    moduleFixtureBuilder.addSourceContentRoot(myFixture.getTempDirPath());
    tuneFixture(moduleFixtureBuilder);

    myFixture.setTestDataPath(getTestDataPath());
    myFixture.setUp();
    myModule = moduleFixtureBuilder.getFixture().getModule();
  }

  protected Class<T> getModuleBuilderClass() {
    //noinspection unchecked,rawtypes
    return (Class)EmptyModuleFixtureBuilder.class;
  }

  @Override
  protected void tearDown() throws Exception {
    myModule = null;
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

  protected void tuneFixture(T moduleBuilder) { }

  /**
   * Return relative path to the test data. Path is relative to the
   * {@link com.intellij.openapi.application.PathManager#getHomePath()}
   *
   * @return relative path to the test data.
   */
  protected @NonNls String getBasePath() {
    return "";
  }

  /**
   * Return absolute path to the test data. Not intended to be overridden.
   *
   * @return absolute path to the test data.
   */
  protected final @NonNls String getTestDataPath() {
    String path = isCommunity() ? PlatformTestUtil.getCommunityPath() : IdeaTestExecutionPolicy.getHomePathWithPolicy();
    return path.replace(File.separatorChar, '/') + getBasePath();
  }

  protected boolean isCommunity() {
    return false;
  }

  protected Project getProject() {
    return myFixture.getProject();
  }

  protected Editor getEditor() {
    return myFixture.getEditor();
  }

  protected PsiFile getFile() {
    return myFixture.getFile();
  }
}
