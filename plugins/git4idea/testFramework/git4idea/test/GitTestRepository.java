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
package git4idea.test;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Representation of a Git repository for tests purposes.
 * Works with {@link java.io.File} - be sure to {@link #refresh()} the repository if a {@link VirtualFile} is needed.
 * @author Kirill Likhodedov
 * @deprecated Use {@link GitLightTest}
 */
@Deprecated
public class GitTestRepository {

  private File myRootDir;
  private GitTestRunEnv myRunEnv;

  @NotNull
  public static GitTestRepository init(@NotNull File dir) throws IOException {
    GitTestRepository repo = new GitTestRepository(dir);
    repo.init();
    return repo;
  }

  @NotNull
  public static GitTestRepository clone(@NotNull GitTestRepository sourceRepo, @NotNull File targetDir) throws IOException {
    sourceRepo.run("clone", sourceRepo.getRootDir().getPath(), targetDir.getPath());
    return new GitTestRepository(targetDir);
  }

  /**
   * Create new GitTestRepository related to the given directory - the Git repository root.
   * @param rootDir
   */
  public GitTestRepository(@NotNull File rootDir) {
    myRootDir = rootDir;
    myRunEnv = new GitTestRunEnv(myRootDir);
  }

  public void init() throws IOException {
    myRunEnv.run("init");
  }

  @NotNull
  public File getRootDir() {
    return myRootDir;
  }

  @NotNull
  public VirtualFile getVFRootDir() {
    final VirtualFile vf = VcsUtil.getVirtualFile(myRootDir);
    assert vf != null;
    return vf;
  }

  public void refresh() {
    final VirtualFile virtualFile = VcsUtil.getVirtualFile(myRootDir);
    assert virtualFile != null;
    virtualFile.refresh(false, true);
  }

  /**
   * Creates a file in this repository and fills it with the given content.
   * @param filename relative path to the file.
   * @param content  initial content for the file.
   * @return The created file.
   */
  @NotNull
  public File createFile(String filename, String content) throws IOException {
    return createFile(myRootDir, filename, content);
  }
  
  @NotNull
  public static File createFile(File parentDir, String filename, String content) throws IOException {
    File f = new File(parentDir, filename);
    assert f.createNewFile();
    FileUtil.writeToFile(f, content);
    return f;
  }

  @NotNull
  public VirtualFile createVFile(String filename, String content) throws IOException {
    File f = createFile(filename, content);
    refresh();
    final VirtualFile virtualFile = VcsUtil.getVirtualFile(f);
    assert virtualFile != null;
    return virtualFile;
  }

  @NotNull
  public File createDir(String dirname) {
    return createDir(myRootDir.getPath(), dirname);
  }

  @NotNull
  public VirtualFile createVDir(String dirname) {
    File d = createDir(dirname);
    refresh();
    final VirtualFile virtualFile = VcsUtil.getVirtualFile(d);
    assert virtualFile != null;
    return virtualFile;
  }

  public File createDir(String parent, String dirname) {
    File dir = new File(parent, dirname);
    assert dir.mkdir();
    return dir;
  }

  public String run(String command, String... params) throws IOException {
    return myRunEnv.run(command, params);
  }

  /**
   * Configures name and email for this git repository.
   */
  public void setName(String name, String email) throws IOException {
    config("user.name", name);
    config("user.email", email);
  }

  public void add(String... filenames) throws IOException {
    if (filenames.length == 0) {
      myRunEnv.run("add", ".");
    } else {
      myRunEnv.run("add", filenames);
    }
  }

  public String commit(@Nullable String commitMessage) throws IOException {
    if (commitMessage == null) {
      commitMessage = "Sample commit message";
    }
    myRunEnv.run("commit", "-m", commitMessage);
    return lastCommit();
  }

  /**
   * Commit with a sample commit message. Use this when commit message doesn't matter to your test.
   */
  public String commit() throws IOException {
    return commit(null);
  }

  public void config(String... parameters) throws IOException {
    myRunEnv.run("config", parameters);
  }

  /**
   * Create new branch and switch to it.
   */
  public void createBranch(String branchName) throws IOException {
    myRunEnv.run("checkout", "-b", branchName);
  }

  /**
   * Checkout the given branch.
   */
  public void checkout(String branchName) throws IOException {
    myRunEnv.run("checkout", branchName);
    // need to refresh the root directory, because checkouting a branch changes files on disk, but VFS is unaware of it.
    refresh();
  }

  public void fetch(String... parameters) throws IOException {
    myRunEnv.run("fetch", parameters);
  }

  public String log(String... parameters) throws IOException {
    return myRunEnv.run("log", parameters);
  }

  public void merge(String... parameters) throws IOException {
    myRunEnv.run("merge", parameters);
  }

  public void rebase(String... parameters) throws IOException {
    myRunEnv.run("rebase", parameters);
  }

  public void mv(VirtualFile file, String newPath) throws IOException {
    myRunEnv.run("mv", file.getPath(), newPath);
  }

  public void mv(String oldPath, String newPath) throws Exception {
    myRunEnv.run("mv", oldPath, newPath);
  }

  public void pull() throws IOException {
    myRunEnv.run("pull");
  }

  public void push(String... params) throws IOException {
    myRunEnv.run("push", params);
  }

  public void rm(String... filenames) throws Exception {
    myRunEnv.run("rm", filenames);
  }

  public void stash(String... params) throws IOException {
    myRunEnv.run("stash", params);
  }

  public void unstash() throws IOException {
    myRunEnv.run("stash", "pop");
  }

  /**
   * Calls add() and then commit(). A shorthand for usual test situations when a file is added and then immediately committed.
   */
  public String addCommit() throws IOException {
    return addCommit(null);
  }

  public String addCommit(@Nullable String commitMessage) throws IOException {
    add();
    return commit(commitMessage);
  }

  public String branch(String... parameters) throws IOException {
    return myRunEnv.run("branch", parameters);
  }

  public String lastCommit() throws IOException {
    return myRunEnv.run(true, "rev-parse", "HEAD").trim();
  }

  public String createAddCommit() throws IOException {
    createFile("file" + Math.random() + ".txt", "Some content " + Math.random());
    return addCommit();
  }
}
