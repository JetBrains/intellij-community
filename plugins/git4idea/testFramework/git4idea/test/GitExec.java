/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.AbstractVcsTestCase;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import git4idea.PlatformFacade;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Kirill Likhodedov
 */
public class GitExec {

  public static void refresh(@NotNull GitRepository repository) {
    repository.getRoot().refresh(false, true);
  }

  public static GitRepository init(@NotNull Project project, @NotNull VirtualFile root) throws IOException {
    new GitTestRunEnv(new File(root.getPath())).run("init");
    root.refresh(false, true);
    return GitRepositoryImpl.getLightInstance(root, project, ServiceManager.getService(project, PlatformFacade.class), project);
  }

  /**
   * Returns null in case of bare repository, because GitRepository instance for a bare repository can't be created.
   */
  @Nullable
  public static GitRepository clone(@NotNull Project project, @NotNull String sourcePath, @NotNull String destinationPath, boolean bare)
    throws IOException
  {

    String[] args = bare ? new String[]{"--bare", sourcePath, destinationPath} : new String[]{sourcePath, destinationPath};
    new GitTestRunEnv(new File(sourcePath)).run("clone", args);
    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(destinationPath));
    assert root != null;
    root.refresh(false, true);
    return bare ? null : GitRepositoryImpl.getLightInstance(root, project,
                                                            ServiceManager.getService(project, PlatformFacade.class), project);
  }

  public static String push(@NotNull GitRepository repository, String... args) throws IOException {
    return run(repository, "push", args);
  }

  public static String remoteAdd(@NotNull GitRepository repository, String... args) throws IOException {
    return run(repository, "remote", ArrayUtil.mergeArrays(new String[]{"add"}, args));
  }

  public static void create(@NotNull GitRepository repository, @NotNull String filePath) {
    create(repository, filePath, "content");
  }

  public static void create(final GitRepository repository, final String filePath, @NotNull final String content) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override public void run() {
        VfsTestUtil.createFile(repository.getRoot(), filePath, content);
      }
    });
  }

  public static void edit(final GitRepository repository, String filePath, final String newContent) {
    final VirtualFile file = repository.getRoot().findFileByRelativePath(filePath);
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override public void run() {
        AbstractVcsTestCase.editFileInCommand(repository.getProject(), file, newContent);
      }
    });
  }
  
  @NotNull
  public static String read(final @NotNull GitRepository repository, @NotNull String filePath) throws IOException {
    final VirtualFile file = repository.getRoot().findFileByRelativePath(filePath);
    assert file != null;
    return new String(file.contentsToByteArray());
  }

  public static void add(@NotNull GitRepository repository, @NotNull String filePath) throws IOException {
    run(repository, "add", filePath);
  }

  public static void add(@NotNull GitRepository repository) throws IOException {
    run(repository, "add", ".");
  }

  public static void commit(@NotNull GitRepository repository) throws IOException {
    run(repository, "commit", "-m", "message");
  }
  
  public static void addCommit(@NotNull GitRepository repository, @NotNull String filePath) throws IOException {
    add(repository, filePath);
    commit(repository);
  }
  
  public static void addCommit(@NotNull GitRepository repository) throws IOException {
    add(repository);
    commit(repository);
  }

  public static void createAddCommit(@NotNull GitRepository repository, @NotNull String filePath) throws IOException {
    create(repository, filePath);
    addCommit(repository, filePath);
  }
  
  @NotNull
  public static String branch(@NotNull GitRepository repository, String... params) throws IOException {
    return run(repository, "branch", params);
  }

  @Nullable
  public static String currentBranch(@NotNull GitRepository repository) throws IOException {
    String[] branches = branch(repository).split("\n");
    for (String branch : branches) {
      if (branch.trim().startsWith("*")) {
        return branch.trim().substring(1).trim();
      }
    }
    return null;
  }

  public static void checkout(@NotNull GitRepository repository, String... params) throws IOException {
    run(repository, "checkout", params);
  }

  public static void merge(@NotNull GitRepository repository, @NotNull String branch) throws IOException {
    run(repository, "merge", branch);
  }

  public static String tip(@NotNull GitRepository repository) throws IOException {
    return run(repository, "rev-list", "-1", "HEAD");
  }

  @NotNull
  public static String run(@NotNull GitRepository repository, @NotNull String command, String... params) throws IOException {
    return new GitTestRunEnv(new File(repository.getRoot().getPath())).run(command, params);
  }

}
