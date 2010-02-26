/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework.fixtures;

import com.intellij.lang.Language;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author peter
 */
public abstract class LightPlatformCodeInsightFixtureTestCase extends UsefulTestCase{
  protected CodeInsightTestFixture myFixture;
  protected Module myModule;

  protected void setUp() throws Exception {
    super.setUp();

    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(getProjectDescriptor());
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));

    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());

    myModule = myFixture.getModule();
  }

  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    myModule = null;
    super.tearDown();
  }

  /**
   * Return relative path to the test data.
   *
   * @return relative path to the test data.
   */
  @NonNls
  protected String getBasePath() {
    return "";
  }


  protected LightProjectDescriptor getProjectDescriptor() {
    return null;
  }

  /**
   * Return absolute path to the test data. Not intended to be overrided.
   *
   * @return absolute path to the test data.
   */
  @NonNls
  protected final String getTestDataPath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/') + getBasePath();
  }

  protected void runTest() throws Throwable {
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        LightPlatformCodeInsightFixtureTestCase.super.runTest();
      }
    }.execute();
  }

  protected Project getProject() {
    return myFixture.getProject();
  }

  protected PsiManager getPsiManager() {
    return PsiManager.getInstance(getProject());
  }

  protected PsiFile createLightFile(final FileType fileType, final String text) {
    return PsiFileFactory.getInstance(getProject()).createFileFromText("a." + fileType.getDefaultExtension(), fileType, text);
  }

  public PsiFile createLightFile(final String fileName, final Language language, final String text) {
    return PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, language, text, false, true);
  }

}