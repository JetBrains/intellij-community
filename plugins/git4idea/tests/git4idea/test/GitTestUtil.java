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
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import com.intellij.vcs.log.VcsRef;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.config.GitVersion;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static com.intellij.openapi.vcs.Executor.append;
import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.mkdir;
import static com.intellij.openapi.vcs.Executor.touch;
import static git4idea.test.GitExecutor.*;
import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

public class GitTestUtil {

  public static final String USER_NAME = "John Doe";
  public static final String USER_EMAIL = "John.Doe@example.com";

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
  public static void createFileStructure(@NotNull VirtualFile rootDir, String... paths) {
    for (String path : paths) {
      cd(rootDir);
      boolean dir = path.endsWith("/");
      if (dir) {
        mkdir(path);
      }
      else {
        touch(path, "initial_content_" + Math.random());
      }
    }
  }

  public static void initRepo(@NotNull String repoRoot, boolean makeInitialCommit) {
    cd(repoRoot);
    git("init");
    setupUsername();
    if (makeInitialCommit) {
      touch("initial.txt");
      git("add initial.txt");
      git("commit -m initial");
    }
  }

  public static void setupUsername() {
    git("config user.name '" + USER_NAME + "'");
    git("config user.email '" + USER_EMAIL + "'");
  }

  /**
   * Creates a Git repository in the given root directory;
   * registers it in the Settings;
   * return the {@link GitRepository} object for this newly created repository.
   */
  @NotNull
  public static GitRepository createRepository(@NotNull Project project, @NotNull String root) {
    return createRepository(project, root, true);
  }

  public static GitRepository createRepository(@NotNull Project project, @NotNull String root, boolean makeInitialCommit) {
    initRepo(root, makeInitialCommit);
    VirtualFile gitDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(root, GitUtil.DOT_GIT));
    assertNotNull(gitDir);
    return registerRepo(project, root);
  }

  @NotNull
  public static GitRepository registerRepo(Project project, String root) {
    ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(project);
    vcsManager.setDirectoryMapping(root, GitVcs.NAME);
    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(new File(root));
    assertFalse(vcsManager.getAllVcsRoots().length == 0);
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(file);
    assertNotNull("Couldn't find repository for root " + root, repository);
    return repository;
  }

  public static void assumeSupportedGitVersion(@NotNull GitVcs vcs) {
    GitVersion version = vcs.getVersion();
    assumeTrue("Unsupported Git version: " + version, version.isSupported());
  }

  @SuppressWarnings("unchecked")
  @NotNull
  public static <T> T overrideService(@NotNull Project project, Class<? super T> serviceInterface, Class<T> serviceImplementation) {
    String key = serviceInterface.getName();
    MutablePicoContainer picoContainer = (MutablePicoContainer) project.getPicoContainer();
    picoContainer.unregisterComponent(key);
    picoContainer.registerComponentImplementation(key, serviceImplementation);
    return (T) ServiceManager.getService(project, serviceInterface);
  }

  @SuppressWarnings("unchecked")
  @NotNull
  public static <T> T overrideService(Class<? super T> serviceInterface, Class<T> serviceImplementation) {
    String key = serviceInterface.getName();
    MutablePicoContainer picoContainer = (MutablePicoContainer) ApplicationManager.getApplication().getPicoContainer();
    picoContainer.unregisterComponent(key);
    picoContainer.registerComponentImplementation(key, serviceImplementation);
    return (T) ServiceManager.getService(serviceInterface);
  }

  @NotNull
  public static Set<VcsRef> readAllRefs(@NotNull VirtualFile root, @NotNull VcsLogObjectsFactory objectsFactory) {
    String[] refs = StringUtil.splitByLines(git("log --branches --tags --no-walk --format=%H%d --decorate=full"));
    Set<VcsRef> result = ContainerUtil.newHashSet();
    for (String ref : refs) {
      result.addAll(new RefParser(objectsFactory).parseCommitRefs(ref, root));
    }
    return result;
  }

  @NotNull
  public static String makeCommit(String file) throws IOException {
    append(file, "some content");
    addCommit("some message");
    return last();
  }

  public static void assertNotification(@NotNull NotificationType type,
                                        @NotNull String title,
                                        @NotNull String content,
                                        @NotNull Notification actual) {
    assertEquals("Incorrect notification type: " + tos(actual), type, actual.getType());
    assertEquals("Incorrect notification title: " + tos(actual), title, actual.getTitle());
    assertEquals("Incorrect notification content: " + tos(actual), cleanupForAssertion(content), cleanupForAssertion(actual.getContent()));
  }

  @NotNull
  public static String cleanupForAssertion(@NotNull String content) {
    return content.replace("<br/>", "\n").replace("\n", " ").replaceAll("[ ]{2,}", " ").replaceAll(" href='[^']*'", "").trim();
  }

  @NotNull
  private static String tos(@NotNull Notification notification) {
    return notification.getTitle() + "|" + notification.getContent();
  }
}
