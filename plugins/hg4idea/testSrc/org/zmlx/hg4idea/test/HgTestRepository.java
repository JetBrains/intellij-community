/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.zmlx.hg4idea.test;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.vcs.AbstractVcsTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Representation of a Mercurial repository for tests purposes.
 */
public class HgTestRepository {
  @NotNull private final HgTest myTest;
  @NotNull private final TempDirTestFixture myDirFixture;
  private VirtualFile myDir;


  /**
   * @param test test instance
   * @param dir  repository root
   */
  public HgTestRepository(@NotNull HgTest test, @NotNull TempDirTestFixture dir) {
    myTest = test;
    myDirFixture = dir;
  }

  /**
   * Creates a new Mercurial repository in a new temporary test directory.
   * @param test reference to the test case instance.
   * @return created repository.
   */
  public static HgTestRepository create(HgTest test) throws Exception {
    final TempDirTestFixture dirFixture = createFixtureDir();
    final File repo = new File(dirFixture.getTempDirPath());
    final ProcessOutput processOutput = test.runHg(repo, "init");
    AbstractVcsTestCase.verify(processOutput);
    return new HgTestRepository(test, dirFixture);
  }

  private static TempDirTestFixture createFixtureDir() throws Exception {
    final TempDirTestFixture fixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    fixture.setUp();
    return fixture;
  }

  /**
   * Clones a repository from this one. New repository is located in a new temporary test directory.
   * @return New repository cloned from this one.
   */
  public HgTestRepository cloneRepository() throws Exception {
    final TempDirTestFixture dirFixture = createFixtureDir();
    final ProcessOutput processOutput = myTest.runHg(null, "clone", getDirFixture().getTempDirPath(), dirFixture.getTempDirPath());
    AbstractVcsTestCase.verify(processOutput);
    return new HgTestRepository(myTest, dirFixture);
  }

  @NotNull
  public TempDirTestFixture getDirFixture() {
    return myDirFixture;
  }

  @Nullable
  public VirtualFile getDir() {
    if (myDir == null) {
      myDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myDirFixture.getTempDirPath()));
    }
    return myDir;
  }
}
