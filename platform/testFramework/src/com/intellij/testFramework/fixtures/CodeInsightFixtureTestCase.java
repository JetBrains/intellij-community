// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author yole
 */
public abstract class CodeInsightFixtureTestCase<T extends ModuleFixtureBuilder> extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;
  protected Module myModule;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String name = getClass().getName() + "." + getName();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name);
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    final T moduleFixtureBuilder = projectBuilder.addModule(getModuleBuilderClass());
    moduleFixtureBuilder.addSourceContentRoot(myFixture.getTempDirPath());
    tuneFixture(moduleFixtureBuilder);

    myFixture.setTestDataPath(getTestDataPath());
    myFixture.setUp();
    myModule = moduleFixtureBuilder.getFixture().getModule();
  }

  protected Class<T> getModuleBuilderClass() {
    return (Class<T>)EmptyModuleFixtureBuilder.class;
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

  protected void tuneFixture(final T moduleBuilder) {}

  /**
   * Return relative path to the test data. Path is relative to the
   * {@link com.intellij.openapi.application.PathManager#getHomePath()}
   *
   * @return relative path to the test data.
   */
  @NonNls
  protected String getBasePath() {
    return "";
  }

  /**
   * Return absolute path to the test data. Not intended to be overridden.
   *
   * @return absolute path to the test data.
   */
  @NonNls
  protected final String getTestDataPath() {
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
