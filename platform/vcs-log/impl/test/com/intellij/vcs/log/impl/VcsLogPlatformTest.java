/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public abstract class VcsLogPlatformTest extends UsefulTestCase {

  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  private static final Logger LOG = Logger.getInstance(VcsLogPlatformTest.class);

  protected Project myProject;
  protected VirtualFile myProjectRoot;
  protected String myProjectPath;

  private IdeaProjectTestFixture myProjectFixture;
  private String myTestStartedIndicator;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableDebugLogging();

    try {
      myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getTestName(true)).getFixture();
      myProjectFixture.setUp();
    }
    catch (Exception e) {
      super.tearDown();
      throw e;
    }

    myProject = myProjectFixture.getProject();
    myProjectRoot = myProject.getBaseDir();
    myProjectPath = myProjectRoot.getPath();
  }

  private void enableDebugLogging() {
    TestLoggerFactory.enableDebugLogging(myTestRootDisposable, ArrayUtil.toStringArray(getDebugLogCategories()));
    myTestStartedIndicator = createTestStartedIndicator();
    LOG.info(myTestStartedIndicator);
  }

  @NotNull
  private String createTestStartedIndicator() {
    return "Starting " + getClass().getName() + "." + getTestName(false) + Math.random();
  }

  @NotNull
  protected Collection<String> getDebugLogCategories() {
    return Collections.emptyList();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myProjectFixture.tearDown();
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected void defaultRunBare() throws Throwable {
    try {
      super.defaultRunBare();
    }
    catch (Throwable throwable) {
      if (myTestStartedIndicator != null) {
        TestLoggerFactory.dumpLogToStdout(myTestStartedIndicator);
      }
      throw throwable;
    }
  }
}