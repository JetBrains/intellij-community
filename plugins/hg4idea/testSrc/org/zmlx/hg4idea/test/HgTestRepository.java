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
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.vcs.AbstractVcsTestCase;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Representation of a Mercurial repository for tests purposes.
 * @author Kirill Likhodedov
 */
public class HgTestRepository {
  @NotNull private final HgTest myTest;
  @NotNull private final TempDirTestFixture myDirFixture;
  private VirtualFile myDir;
  @Nullable private final HgTestRepository myParent; // cloned from

  public HgTestRepository(@NotNull HgTest test, @NotNull TempDirTestFixture dir) {
    this(test, dir, null);
  }

  /**
   * @param test   test instance
   * @param dir    repository root
   * @param parent parent repository where this repository is cloned from, if one exists.
   */
  public HgTestRepository(@NotNull HgTest test, @NotNull TempDirTestFixture dir, @Nullable HgTestRepository parent) {
    myTest = test;
    myDirFixture = dir;
    myParent = parent;
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
      myDir = VcsUtil.getVirtualFile(myDirFixture.getTempDirPath());
    }
    return myDir;
  }

  /**
   * Creates a file in this repository.
   * @param filename relative path to the file.
   * @return The created file.
   */
  public VirtualFile createFile(String filename) {
    return createFile(filename, "initial content");
  }

  /**
   * Creates a file in this repository and fills it with the given content.
   * @param filename relative path to the file.
   * @param content  initial content for the file.
   * @return The created file.
   */
  public VirtualFile createFile(String filename, String content) {
    return myTest.createFileInCommand(filename, content);
  }

  /**
   * Natively executes the given mercurial command.
   * @param commandWithParameters Mercurial command with parameters. E.g. ["status", "-a"]
   */
  public void execute(String... commandWithParameters) throws IOException {
    myTest.runHg(new File(myDirFixture.getTempDirPath()), commandWithParameters);
  }

  public void add() throws IOException {
    execute("add");
  }

  public void commit() throws IOException {
    execute("commit", "-m", "Sample commit message");
  }

  public void merge() throws IOException {
    execute("merge");
  }

  public void pull() throws IOException {
    execute("pull");
  }

  public void push() throws IOException {
    execute("push");
  }

  public void update() throws IOException {
    execute("update");
  }

  /**
   * Calls add() and then commit(). A shorthand for usual test situations when a file is added and then immediately committed.
   */
  public void addCommit() throws IOException {
    add();
    commit();
  }

  /**
   * Calls pull, update and merge on this repository. Common for merge testing.
   */
  public void pullUpdateMerge() throws IOException {
    pull();
    update();
    merge();
  }

}
