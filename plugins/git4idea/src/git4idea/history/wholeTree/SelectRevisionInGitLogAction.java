package git4idea.history.wholeTree;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.vcs.log.impl.VcsLogContentProvider;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.ui.VcsLogUI;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author rachinskiy
 */
public class SelectRevisionInGitLogAction extends DumbAwareAction {

  public SelectRevisionInGitLogAction() {
    super(GitBundle.getString("vcs.history.action.gitlog"), GitBundle.getString("vcs.history.action.gitlog"), null);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final VirtualFile virtualFile = event.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    final Project project = event.getData(CommonDataKeys.PROJECT);
    final VcsRevisionNumber revision = getRevisionNumber(event);
    if (revision == null || virtualFile == null || project == null) {
      return;
    }

    final VcsLogManager log = ServiceManager.getService(project, VcsLogManager.class);
    if (log == null) {
      return;
    }

    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
    ContentManager cm = window.getContentManager();
    Content[] contents = cm.getContents();
    for (Content content : contents) {
      if (VcsLogContentProvider.TAB_NAME.equals(content.getDisplayName())) {
        cm.setSelectedContent(content);
      }
    }

    Runnable selectCommit = new Runnable() {
      @Override
      public void run() {
        VcsLogUI logUi = log.getLogUi();
        if (logUi == null) {
          return;
        }
        logUi.getVcsLog().jumpToReference(revision.asString());
      }
    };

    if (!window.isVisible()) {
      window.activate(selectCommit, true);
    }
    else {
      selectCommit.run();
    }
  }

  @Nullable
  private static VcsRevisionNumber getRevisionNumber(@NotNull AnActionEvent event) {
    VcsRevisionNumber revision = event.getData(VcsDataKeys.VCS_REVISION_NUMBER);
    if (revision == null) {
      VcsFileRevision fileRevision = event.getData(VcsDataKeys.VCS_FILE_REVISION);
      if (fileRevision != null) {
        revision = fileRevision.getRevisionNumber();
      }
    }
    return revision;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled((e.getData(VcsDataKeys.VCS_FILE_REVISION) != null ||
                                    e.getData(VcsDataKeys.VCS_REVISION_NUMBER) != null));
  }

}
