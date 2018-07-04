// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.lang.Language;
import com.intellij.openapi.application.PathManager;
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

/**
 * @author peter
 */
public abstract class LightPlatformCodeInsightFixtureTestCase extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;
  protected Module myModule;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(getProjectDescriptor());
    IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));

    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());

    myModule = myFixture.getModule();
  }

  @Override
  @SuppressWarnings("Duplicates")
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    finally {
      myFixture = null;
      myModule = null;
      super.tearDown();
    }
  }

  /**
   * Returns relative path to the test data.
   */
  protected String getBasePath() {
    return "";
  }

  protected LightProjectDescriptor getProjectDescriptor() {
    return null;
  }

   /**
    * Return absolute path to the test data. Not intended to be overridden in tests written as part of the IntelliJ IDEA codebase;
    * must be overridden in plugins which use the test framework.
    *
    * @see #getBasePath()
    */
   protected String getTestDataPath() {
     String path = isCommunity() ? PlatformTestUtil.getCommunityPath() : PathManager.getHomePath();
     return StringUtil.trimEnd(FileUtil.toSystemIndependentName(path), "/") + '/' +
            StringUtil.trimStart(FileUtil.toSystemIndependentName(getBasePath()), "/");
   }

   protected boolean isCommunity() {
     return false;
   }

  @Override
  protected void runTest() throws Throwable {
    if (isWriteActionRequired()) {
      WriteCommandAction.writeCommandAction(getProject()).run(() -> doRunTests());
    }
    else {
      doRunTests();
    }
  }

  protected boolean isWriteActionRequired() {
    return false;
  }

  protected void doRunTests() throws Throwable {
    super.runTest();
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
}