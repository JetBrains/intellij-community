/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
  public void setUp() throws Exception {
    myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getClass().getSimpleName()).getFixture();
    myProjectFixture.setUp();
    myProject = myProjectFixture.getProject();

    myLocalFileSystem = LocalFileSystem.getInstance();
  }

  @After
  public void tearDown() throws Exception {
    new RunAll()
      .append(() -> myProject = null)
      .append(() -> AsyncVfsEventsPostProcessorImpl.waitEventsProcessed())
      .append(() -> EdtTestUtil.runInEdtAndWait(() -> myProjectFixture.tearDown()))
      .run();
  }
}
