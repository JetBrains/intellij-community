package gitlog;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.repo.GitRepositoryManager;
import git4idea.repo.GitRepositoryManagerImpl;
import org.hanuna.gitalk.swing_ui.Swing_UI;
import org.hanuna.gitalk.ui.impl.UI_ControllerImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Kirill Likhodedov
 */
public class GitLogComponent extends AbstractProjectComponent {

  private final Project myProject;
  private GitRepositoryManager myRepositoryManager;
  private GitRepository myRepository;
  private Swing_UI mySwingUi;
  private UI_ControllerImpl myUiController;

  @SuppressWarnings("UnusedParameters")
  protected GitLogComponent(Project project, GitRepositoryManager makeSureRepositoryManagerIsInitialized,
                            DirectoryIndex makeSureIndexIsInitialized) {
    super(project);
    myProject = project;
  }

  @Override
  public void initComponent() {
    super.initComponent();

    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      @Override
      public void run() {
        myRepositoryManager = GitUtil.getRepositoryManager(myProject);
        ((GitRepositoryManagerImpl)myRepositoryManager).updateRepositoriesCollection();
        myRepository = myRepositoryManager.getRepositories().get(0);

        myUiController = new UI_ControllerImpl(myProject);
        mySwingUi = new Swing_UI(myUiController);
        myUiController.addControllerListener(mySwingUi.getControllerListener());
        myUiController.setDragDropListener(mySwingUi.getDragDropListener());
        myUiController.init(false);

        myProject.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
          @Override
          public void repositoryChanged(@NotNull GitRepository repository) {
            myUiController.refresh();
          }
        });

        final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow("Git Log");
        if (toolWindow != null && toolWindow.isActive()) {
          toolWindow.activate(null);
        }
      }
    });
  }

  public JPanel getMainGitAlkComponent() {
    return mySwingUi.getMainFrame().getMainComponent();
  }

  public GitRepository getRepository() {
    return myRepository;
  }

  public UI_ControllerImpl getUiController() {
    return myUiController;
  }
}
