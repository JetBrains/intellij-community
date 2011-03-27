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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.AbstractVcsTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.ui.GuiUtils;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.fail;

/**
 * Representation of a Git repository for tests purposes.
 * @author Kirill Likhodedov
 */
public class GitTestRepository {
  @NotNull private final GitTest myTest;
  @NotNull private final TempDirTestFixture myDirFixture;
  private VirtualFile myDir;

  public GitTestRepository(@NotNull GitTest test, @NotNull TempDirTestFixture fixture) {
    myTest = test;
    myDirFixture = fixture;
  }

  /**
   * Creates a new Mercurial repository in a new temporary test directory.
   * @param test   reference to the test case instance.
   * @param parameters optional array of parameters passed to 'git init'
   * @return created repository.
   */
  public static GitTestRepository create(GitTest test, String... parameters) throws Exception {
    final TempDirTestFixture dirFixture = createFixtureDir();
    final File repo = new File(dirFixture.getTempDirPath());
    final ProcessOutput processOutput = test.executeCommand(repo, join("init", parameters));
    AbstractVcsTestCase.verify(processOutput);
    return new GitTestRepository(test, dirFixture);
  }

  public static GitTestRepository cloneFrom(GitTestRepository parent) throws Exception {
    final TempDirTestFixture dirFixture = createFixtureDir();
    final File repo = new File(dirFixture.getTempDirPath());
    final ProcessOutput processOutput = parent.getTest().executeCommand(repo, "clone", parent.getDirFixture().getTempDirPath(), repo.getPath());
    AbstractVcsTestCase.verify(processOutput);
    return new GitTestRepository(parent.getTest(), dirFixture);
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

  @Nullable
  public VirtualFile getDir() {
    if (myDir == null) {
      myDir = VcsUtil.getVirtualFile(myDirFixture.getTempDirPath());
    }
    return myDir;
  }

  /**
   * Configures name and email for this git repository.
   */
  public void setName(String name, String email) throws IOException {
    config("user.name", name);
    config("user.email", email);
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
   * Natively executes the given mercurial command inside write or read action.
   * @param writeAction           If true, the command will be executed in a write action, otherwise - inside a read action.
   * @param commandWithParameters Mercurial command with parameters. E.g. ["status", "-a"]
   */
  public ProcessOutput execute(final boolean writeAction, final String... commandWithParameters) throws IOException {
    final AtomicReference<ProcessOutput> result = new AtomicReference<ProcessOutput>();
    final Runnable action = new Runnable() {
      @Override public void run() {
        try {
          result.set(myTest.executeCommand(new File(myDirFixture.getTempDirPath()), commandWithParameters));
        } catch (IOException e) {
          result.set(null);
        }
      }
    };
    if (ApplicationManager.getApplication() == null) { // application may be not initialized yet. OK to just run the action then.
      action.run();
      return result.get();
    }
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        if (writeAction) {
          ApplicationManager.getApplication().runWriteAction(action);
        } else {
          ApplicationManager.getApplication().runReadAction(action);
        }
      }
    }, ModalityState.defaultModalityState());
    return result.get();
  }

  public void add(String... filenames) throws IOException {
    if (filenames.length == 0) {
      execute(true, "add", ".");
    } else {
      execute(true, join("add", filenames));
    }
  }

  public ProcessOutput commit(@Nullable String commitMessage) throws IOException {
    if (commitMessage == null) {
      commitMessage = "Sample commit message";
    }
    return execute(true, "commit", "-m", commitMessage);
  }

  /**
   * Commit with a sample commit message. Use this when commit message doesn't matter to your test.
   */
  public void commit() throws IOException {
    commit(null);
  }

  public void config(String... parameters) throws IOException {
    execute(true, join("config", parameters));
  }

  /**
   * Create new branch and switch to it.
   */
  public void createBranch(String branchName) throws IOException {
    execute(true, "checkout", "-b", branchName);
  }

  /**
   * Checkout the given branch.
   */
  public void checkout(String branchName) throws IOException {
    execute(true, "checkout", branchName);
    // need to refresh the root directory, because checkouting a branch changes files on disk, but VFS is unaware of it.
    refresh();
  }

  public ProcessOutput log(String... parameters) throws IOException {
    return execute(false, join("log", parameters));
  }

  public void merge(String... parameters) throws IOException {
    execute(true, join("merge", parameters));
  }

  public void mv(VirtualFile file, String newPath) throws IOException {
    execute(true, "mv", file.getPath(), newPath);
  }

  public void mv(String oldPath, String newPath) throws Exception {
    execute(true, "mv", oldPath, newPath);
    refreshFile(getDir(), true);
  }

  private static void refreshFile(final VirtualFile file, final boolean recursive) throws Exception {
    GuiUtils.runOrInvokeAndWait(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            file.refresh(false, recursive);
          }
        });
      }
    });
  }

  public void pull() throws IOException {
    execute(true, "pull");
  }

  public void push(String... params) throws IOException {
    execute(true, join("push", params));
  }

  public void rm(String... filenames) throws Exception {
    execute(true, join("rm", filenames));
    refreshFile(getDir(), true);
  }

  public void update() throws IOException {
    execute(true, "update");
  }

  /**
   * Calls add() and then commit(). A shorthand for usual test situations when a file is added and then immediately committed.
   */
  public ProcessOutput addCommit() throws IOException {
    add();
    return commit(null);
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

  private static String[] join(String parameter, String[] parameters) {
    String[] pars = new String[parameters.length+1];
    pars[0] = parameter;
    System.arraycopy(parameters, 0, pars, 1, parameters.length);
    return pars;
  }

  public void refresh() {
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        try {
          refreshFile(getDir(), true);
        } catch (Exception e) {
          e.printStackTrace();
          fail("Exception while refreshing repository", e);
        }
      }
    }, ModalityState.defaultModalityState());
  }

  public void addRemotes(String... parameters) throws IOException {
    for (String s : parameters) {
      execute(true, "remote", "add", s);
    }
  }

  public ProcessOutput branch(String... parameters) throws IOException {
    return execute(false, join("branch", parameters));
  }

  @NotNull
  public GitTest getTest() {
    return myTest;
  }

  public String lastCommit() throws IOException {
    return execute(false, "rev-parse", "HEAD").getStdout().trim();
  }

}
