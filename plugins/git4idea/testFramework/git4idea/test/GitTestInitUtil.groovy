package git4idea.test

import com.intellij.dvcs.test.MockVirtualFile
import com.intellij.openapi.project.Project
import git4idea.GitPlatformFacade
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryImpl

import static com.intellij.dvcs.test.Executor.*
import static git4idea.test.GitExecutor.*

/**
 *
 * @author Kirill Likhodedov
 */
class GitTestInitUtil {

  private static final String USER_NAME = "John Doe";
  private static final String USER_EMAIL = "John.Doe@example.com";

  /**
   * Init, set up username and make initial commit.
   * @param repoRoot
   */
  public static void initRepo(String repoRoot) {
    cd repoRoot
    git("init")
    setupUsername();
    touch("initial.txt")
    git("add initial.txt")
    git("commit -m initial")
  }

  public static void setupUsername() {
    git("config user.name $USER_NAME")
    git("config user.email $USER_EMAIL")
  }

  public static GitRepository createRepository(String rootDir, GitPlatformFacade platformFacade, Project project) {
    GitTestInitUtil.initRepo(rootDir)

    // TODO this smells hacky
    // the constructor and notifyListeners() should probably be private
    // getPresentableUrl should probably be final, and we should have a better VirtualFile implementation for tests.
    GitRepository repository = new GitRepositoryImpl(new MockVirtualFile(rootDir), platformFacade, project, project, true) {
      @Override
      protected void notifyListeners() {
      }

      @Override
      String getPresentableUrl() {
        return rootDir;
      }
    }

    registerRepository(repository, platformFacade, project)

    return repository
  }

  private static void registerRepository(GitRepository repository, GitPlatformFacade platformFacade, Project project) {
    ((GitTestRepositoryManager)platformFacade.getRepositoryManager(project)).add(repository)
  }
}
