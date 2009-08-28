package git4idea.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsOutgoingChangesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.commands.GitSimpleHandler;

import java.util.Collections;
import java.util.List;

public class GitOutgoingChangesProvider implements VcsOutgoingChangesProvider {
  private final Project myProject;

  public GitOutgoingChangesProvider(Project project) {
    myProject = project;
  }

  public List getOutgoingChanges(VirtualFile vcsRoot) throws VcsException {
    final GitBranch currentBranch = GitBranch.current(myProject, vcsRoot);
    String trackedRemoteName = currentBranch.getTrackedRemoteName(myProject, vcsRoot);

    if ((trackedRemoteName == null) && (! "master".equals(currentBranch.getName()))) return Collections.emptyList();
    if (trackedRemoteName == null) trackedRemoteName = "master";

    final String finalTrackedRemoteName = trackedRemoteName;
    return GitUtil.getLocalCommittedChanges(myProject, vcsRoot, new Consumer<GitSimpleHandler>() {
      public void consume(final GitSimpleHandler handler) {
        handler.addParameters("origin/" + finalTrackedRemoteName + "..HEAD");
      }
    });
  }
}
