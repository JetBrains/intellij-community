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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.AbstractVcsTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Representation of a Mercurial repository for tests purposes.
 * @author Kirill Likhodedov
 */
class HgTestRepository {
  @NotNull private final HgAbstractTestCase myTest;
  @NotNull private final TempDirTestFixture myDirFixture;
  @Nullable private final HgTestRepository myParent; // cloned from

  HgTestRepository(@NotNull HgAbstractTestCase test, @NotNull TempDirTestFixture dir) {
    this(test, dir, null);
  }

  /**
   * @param test   test instance
   * @param dir    repository root
   * @param parent parent repository where this repository is cloned from, if one exists.
   */
  HgTestRepository(@NotNull HgAbstractTestCase test, @NotNull TempDirTestFixture dir, @Nullable HgTestRepository parent) {
    myTest = test;
    myDirFixture = dir;
    myParent = parent;
  }

  /**
   * Creates a new Mercurial repository in a new temporary test directory.
   * @param testCase reference to the test case instance.
   * @return created repository.
   */
  public static HgTestRepository create(HgAbstractTestCase testCase) throws Exception {
    final TempDirTestFixture dirFixture = createFixtureDir();
    final File repo = new File(dirFixture.getTempDirPath());
    final ProcessOutput processOutput = testCase.runHg(repo, "init");
    AbstractVcsTestCase.verify(processOutput);
    return new HgTestRepository(testCase, dirFixture);
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
  HgTestRepository cloneRepository() throws Exception {
    final TempDirTestFixture dirFixture = createFixtureDir();
    final ProcessOutput processOutput = myTest.runHg(null, "clone", getDirFixture().getTempDirPath(), dirFixture.getTempDirPath());
    AbstractVcsTestCase.verify(processOutput);
    return new HgTestRepository(myTest, dirFixture);
  }

  @NotNull
  TempDirTestFixture getDirFixture() {
    return myDirFixture;
  }

  public VirtualFile getDir() {
    return myDirFixture.getFile(".");
  }

  public VirtualFile createFile(String filename) {
    return myDirFixture.createFile(filename);
  }

  /**
   * Natively executes the given mercurial command.
   * @param commandWithParameters Mercurial command with parameters. E.g. ["status", "-a"]
   */
  void execute(String... commandWithParameters) throws IOException {
    myTest.runHg(new File(myDirFixture.getTempDirPath()), commandWithParameters);
  }

  void add() throws IOException {
    execute("add");
  }

  void commit() throws IOException {
    execute("commit", "-m", "Sample commit message");
  }

  void merge() throws IOException {
    execute("merge");
  }

  void pull() throws IOException {
    execute("pull");
  }

  void push() throws IOException {
    execute("push");
  }

  void update() throws IOException {
    execute("update");
  }

}
