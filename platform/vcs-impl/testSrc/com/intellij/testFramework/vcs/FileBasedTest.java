// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl;
import org.junit.After;
import org.junit.Before;

public abstract class FileBasedTest {
  protected LocalFileSystem myLocalFileSystem;
  protected IdeaProjectTestFixture myProjectFixture;
  protected Project myProject;

  @Before
  public void before() throws Exception {
    myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getClass().getSimpleName()).getFixture();
    myProjectFixture.setUp();
    myProject = myProjectFixture.getProject();

    myLocalFileSystem = LocalFileSystem.getInstance();
  }

  @After
  public void after() throws Exception {
    new RunAll(
      () -> myProject = null,
      () -> AsyncVfsEventsPostProcessorImpl.waitEventsProcessed(),
      () -> EdtTestUtil.runInEdtAndWait(() -> myProjectFixture.tearDown())
    ).run();
  }
}
