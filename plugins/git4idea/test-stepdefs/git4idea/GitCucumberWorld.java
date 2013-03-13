package git4idea;

import com.intellij.dvcs.test.MockVcsHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import cucumber.annotation.After;
import cucumber.annotation.Before;
import git4idea.commands.Git;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.test.GitTestInitUtil;
import git4idea.test.TestNotificator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.picocontainer.MutablePicoContainer;

import java.io.File;

import static com.intellij.dvcs.test.Executor.cd;
import static junit.framework.Assert.assertNotNull;

/**
 * <p>The container of test environment variables which should be visible from any step definition script.</p>
 * <p>Most of the fields are populated in the Before hook of the {@link GeneralStepdefs}.</p>
 *
 * @author Kirill Likhodedov
 */
public class GitCucumberWorld {

  public static String myTestRoot;
  public static String myProjectRoot;
  public static Project myProject;

  public static GitPlatformFacade myPlatformFacade;
  public static Git myGit;
  public static GitRepository myRepository;
  public static GitVcsSettings mySettings;
  public static ChangeListManager myChangeListManager;

  public static MockVcsHelper myVcsHelper;
  public static TestNotificator myNotificator;

  public static GitTestVirtualCommitsHolder virtualCommits;

  private static IdeaProjectTestFixture myProjectFixture;

  @Before
  public void setUp() throws Throwable {
    myProjectFixture = new GitCucumberLightProjectFixture();
    myProjectFixture.setUp();
    myProject = myProjectFixture.getProject();

    ((ProjectComponent)ChangeListManager.getInstance(myProject)).projectOpened();
    ((ProjectComponent)VcsDirtyScopeManager.getInstance(myProject)).projectOpened();

    myProjectRoot = myProject.getBasePath();
    myTestRoot = myProjectRoot;

    myPlatformFacade = ServiceManager.getService(myProject, GitPlatformFacade.class);
    myGit = ServiceManager.getService(myProject, Git.class);
    mySettings = myPlatformFacade.getSettings(myProject);
    myVcsHelper = overrideService(myProject, AbstractVcsHelper.class, MockVcsHelper.class);
    myChangeListManager = myPlatformFacade.getChangeListManager(myProject);
    myNotificator = overrideService(myProject, Notificator.class, TestNotificator.class);

    cd(myProjectRoot);
    myRepository = createRepo(myProjectRoot);

    ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
    AbstractVcs vcs = vcsManager.findVcsByName("Git");
    Assert.assertEquals(1, vcsManager.getRootsUnderVcs(vcs).length);

    virtualCommits = new GitTestVirtualCommitsHolder();
  }

  @NotNull
  private static GitRepository createRepo(String root) {
    GitTestInitUtil.initRepo(root);
    ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
    vcsManager.setDirectoryMapping(root, GitVcs.NAME);
    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(new File(root));
    GitRepository repository = myPlatformFacade.getRepositoryManager(myProject).getRepositoryForRoot(file);
    assertNotNull("Couldn't find repository for root " + root, repository);
    return repository;
  }

  @After
  public void tearDown() throws Throwable {
    virtualCommits = null;
    myProjectFixture.tearDown();
  }

  @SuppressWarnings("unchecked")
  private static <T> T overrideService(@Nullable Project project, Class<? super T> serviceInterface, Class<T> serviceImplementation) {
    final String key = serviceInterface.getName();
    final MutablePicoContainer picoContainer;
    if (project != null) {
      picoContainer = (MutablePicoContainer) project.getPicoContainer();
    }
    else {
      picoContainer = (MutablePicoContainer) ApplicationManager.getApplication().getPicoContainer();

    }
    picoContainer.unregisterComponent(key);
    picoContainer.registerComponentImplementation(key, serviceImplementation);
    if (project != null) {
      return (T) ServiceManager.getService(project, serviceInterface);
    }
    else {
      return (T) ServiceManager.getService(serviceInterface);
    }
  }

  private static <T> T overrideAppService(Class<? super T> serviceInterface, Class<T> serviceImplementation) {
    return overrideService(null, serviceInterface, serviceImplementation);
  }


}
