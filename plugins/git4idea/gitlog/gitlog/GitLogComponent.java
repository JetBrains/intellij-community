package gitlog;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.repo.GitRepositoryManagerImpl;
import org.hanuna.gitalk.swing_ui.Swing_UI;
import org.hanuna.gitalk.ui.impl.UI_ControllerImpl;

import javax.swing.*;

/**
 * @author Kirill Likhodedov
 */
public class GitLogComponent extends AbstractProjectComponent {

  private final Project myProject;
  private GitRepositoryManager myRepositoryManager;
  private GitRepository myRepository;
  private Swing_UI mySwingUi;

  protected GitLogComponent(Project project, GitRepositoryManager repositoryManager) {
    super(project);
    myProject = project;
  }

  @Override
  public void initComponent() {
    super.initComponent();

    myRepositoryManager = GitUtil.getRepositoryManager(myProject);
    ((GitRepositoryManagerImpl)myRepositoryManager).updateRepositoriesCollection();
    myRepository = myRepositoryManager.getRepositories().get(0);

    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      @Override
      public void run() {
        UI_ControllerImpl ui_controller = new UI_ControllerImpl(myProject);
        mySwingUi = new Swing_UI(ui_controller);
        ui_controller.addControllerListener(mySwingUi.getControllerListener());
        ui_controller.init(false);
      }
    });
  }

  public JPanel getMainGitAlkComponent() {
    return mySwingUi.getMainFrame().getMainComponent();
  }

  public GitRepository getRepository() {
    return myRepository;
  }
}
