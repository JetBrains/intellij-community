package git4idea;

import com.intellij.dvcs.test.MockProject;
import git4idea.commands.Git;
import git4idea.repo.GitRepository;

/**
 * <p>The container of test environment variables which should be visible from any step definition script.</p>
 * <p>Most of the fields are populated in the Before hook of the {@link GeneralStepdefs}.</p>
 *
 * @author Kirill Likhodedov
 */
public class GitCucumberWorld {

  public static String myTestRoot;
  public static String myProjectRoot;
  public static MockProject myProject;
  public static GitPlatformFacade myPlatformFacade;
  public static Git myGit;
  public static GitRepository myRepository;

}
