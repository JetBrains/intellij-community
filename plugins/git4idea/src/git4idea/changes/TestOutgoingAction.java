package git4idea.changes;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsOutgoingChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Pair;
import git4idea.GitVcs;

import java.util.List;

public class TestOutgoingAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) return;
    final GitVcs vcs = GitVcs.getInstance(project);
    final VcsOutgoingChangesProvider<CommittedChangeList> provider = vcs.getOutgoingChangesProvider();
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    final VirtualFile[] roots = vcsManager.getRootsUnderVcs(vcs);
    if (roots == null) return;
    for (VirtualFile root : roots) {
      try {
        final Pair<VcsRevisionNumber, List<CommittedChangeList>> pair = provider.getOutgoingChanges(root, true);
        System.out.println("list.size() = " + pair.getSecond().size());
      }
      catch (VcsException e1) {
        e1.printStackTrace();
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setText("Test Outgoing Changes");
  }
}
