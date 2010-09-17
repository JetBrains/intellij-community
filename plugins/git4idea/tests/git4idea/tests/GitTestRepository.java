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
package git4idea.tests;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.AbstractVcsTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * Representation of a Git repository for tests purposes.
 * @author Kirill Likhodedov
 */
public class GitTestRepository {
  @NotNull private final GitTestCase myTest;
  @NotNull private final TempDirTestFixture myDirFixture;

  public GitTestRepository(@NotNull GitTestCase test, @NotNull TempDirTestFixture dir) {
    myTest = test;
    myDirFixture = dir;
  }


  /**
   * Creates a new Mercurial repository in a new temporary test directory.
   * @param testCase reference to the test case instance.
   * @return created repository.
   */
  public static GitTestRepository create(GitTestCase testCase) throws Exception {
    final TempDirTestFixture dirFixture = createFixtureDir();
    final File repo = new File(dirFixture.getTempDirPath());
    final ProcessOutput processOutput = testCase.executeCommand(repo, "init");
    AbstractVcsTestCase.verify(processOutput);
    return new GitTestRepository(testCase, dirFixture);
  }

  private static TempDirTestFixture createFixtureDir() throws Exception {
    final TempDirTestFixture fixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    fixture.setUp();
    return fixture;
  }

  @NotNull
  public TempDirTestFixture getDirFixture() {
    return myDirFixture;
  }

  public VirtualFile getDir() {
    return myDirFixture.getFile(".");
  }

  /**
   * Creates a file in this repository.
   * @param filename relative path to the file.
   * @return The created file.
   */
  public VirtualFile createFile(String filename) throws IOException {
    return createFile(filename, null);
  }

  /**
   * Creates a file in this repository and fills it with the given content.
   * @param filename relative path to the file.
   * @param content  initial content for the file.
   * @return The created file.
   */
  public VirtualFile createFile(String filename, String content) throws FileNotFoundException {
    return myTest.createFileInCommand(filename, content);
  }

  /**
   * Natively executes the given mercurial command.
   * @param commandWithParameters Mercurial command with parameters. E.g. ["status", "-a"]
   */
  public ProcessOutput execute(String... commandWithParameters) throws IOException {
    return myTest.executeCommand(new File(myDirFixture.getTempDirPath()), commandWithParameters);
  }

  public void add() throws IOException {
    execute("add", ".");
  }

  public void commit(String commitMessage) throws IOException {
    if (commitMessage == null) {
      commitMessage = "Sample commit message";
    }
    execute("commit", "-m", commitMessage);
  }

  public void config(String... parameters) throws IOException {
    String[] pars = new String[parameters.length+1];
    pars[0] = "config";
    System.arraycopy(parameters, 0, pars, 1, parameters.length);
    execute(pars);
  }

  public ProcessOutput log(String... parameters) throws IOException {
    String[] pars = new String[parameters.length+1];
    pars[0] = "log";
    System.arraycopy(parameters, 0, pars, 1, parameters.length);
    return execute(pars);
  }

  public void merge() throws IOException {
    execute("merge");
  }

  public void mv(VirtualFile file, String newPath) throws IOException {
    execute("mv", file.getPath(), newPath);
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
    commit(null);
  }

  public void addCommit(String commitMessage) throws IOException {
    add();
    commit(commitMessage);
  }

  /**
   * Calls pull, update and merge on this repository. Common for merge testing.
   */
  public void pullUpdateMerge() throws IOException {
    pull();
    update();
    merge();
  }

  /**
   * Writes the given content to the file.
   * @param file    file which content will be substituted by the given one.
   * @param content new file content
   */
  public static void printToFile(VirtualFile file, String content) throws FileNotFoundException {
    PrintStream centralPrinter = null;
    try {
      centralPrinter = new PrintStream(new FileOutputStream(new File(file.getPath())));
      centralPrinter.print(content);
      centralPrinter.close();
    } finally {
      if (centralPrinter != null) {
        centralPrinter.close();
      }
    }
  }

}
