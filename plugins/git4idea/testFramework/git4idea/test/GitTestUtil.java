/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.notification.Notification;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.Notificator;
import git4idea.repo.GitRepository;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManagerImpl;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.touch;
import static com.intellij.openapi.vcs.VcsTestUtil.createDir;
import static com.intellij.openapi.vcs.VcsTestUtil.createFile;
import static git4idea.test.GitExecutor.git;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

/**
 * @author Kirill Likhodedov
 */
public class GitTestUtil {

  private static final String USER_NAME = "John Doe";
  private static final String USER_EMAIL = "John.Doe@example.com";

  /**
   * <p>Creates file structure for given paths. Path element should be a relative (from project root)
   * path to a file or a directory. All intermediate paths will be created if needed.
   * To create a dir without creating a file pass "dir/" as a parameter.</p>
   * <p>Usage example:
   * <code>createFileStructure("a.txt", "b.txt", "dir/c.txt", "dir/subdir/d.txt", "anotherdir/");</code></p>
   * <p>This will create files a.txt and b.txt in the project dir, create directories dir, dir/subdir and anotherdir,
   * and create file c.txt in dir and d.txt in dir/subdir.</p>
   * <p>Note: use forward slash to denote directories, even if it is backslash that separates dirs in your system.</p>
   * <p>All files are populated with "initial content" string.</p>
   */
  public static Map<String, VirtualFile> createFileStructure(Project project, GitTestRepository repo, String... paths) {
    Map<String, VirtualFile> result = new HashMap<String, VirtualFile>();

    for (String path : paths) {
      String[] pathElements = path.split("/");
      boolean lastIsDir = path.endsWith("/");
      VirtualFile currentParent = repo.getVFRootDir();
      for (int i = 0; i < pathElements.length-1; i++) {
        currentParent = createDir(project, currentParent, pathElements[i]);
      }

      String lastElement = pathElements[pathElements.length-1];
      currentParent = lastIsDir ? createDir(project, currentParent, lastElement) : createFile(project, currentParent, lastElement, "content" + Math.random());
      result.put(path, currentParent);
    }
    return result;
  }

  /**
   * Init, set up username and make initial commit.
   *
   * @param repoRoot
   */
  public static void initRepo(@NotNull String repoRoot) {
    cd(repoRoot);
    git("init");
    setupUsername();
    touch("initial.txt");
    git("add initial.txt");
    git("commit -m initial");
  }

  public static void setupUsername() {
    git("config user.name " + USER_NAME);
    git("config user.email " + USER_EMAIL);
  }

  /**
   * Creates a Git repository in the given root directory;
   * registers it in the Settings;
   * return the {@link GitRepository} object for this newly created repository.
   */
  @NotNull
  public static GitRepository createRepository(@NotNull Project project, @NotNull String root) {
    initRepo(root);
    ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(project);
    vcsManager.setDirectoryMapping(root, GitVcs.NAME);
    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(new File(root));
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(file);
    assertNotNull("Couldn't find repository for root " + root, repository);
    return repository;
  }

  public static void assertNotification(@NotNull Project project, @Nullable Notification expected) {
    if (expected == null) {
      assertNull("Notification is unexpected here", expected);
      return;
    }

    Notification actualNotification = ((TestNotificator)ServiceManager.getService(project, Notificator.class)).getLastNotification();
    Assert.assertNotNull("No notification was shown", actualNotification);
    Assert.assertEquals("Notification has wrong title", expected.getTitle(), actualNotification.getTitle());
    Assert.assertEquals("Notification has wrong type", expected.getType(), actualNotification.getType());
    Assert.assertEquals("Notification has wrong content", expected.getContent(), actualNotification.getContent());
  }

  /**
   * Default port will be occupied by main idea instance => define the custom default to avoid searching of free port
   */
  public static void setDefaultBuiltInServerPort() {
    System.setProperty(BuiltInServerManagerImpl.PROPERTY_RPC_PORT, "64463");
  }
}
