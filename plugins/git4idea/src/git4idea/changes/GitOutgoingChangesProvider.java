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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GitOutgoingChangesProvider implements VcsOutgoingChangesProvider {
  private final Project myProject;

  public GitOutgoingChangesProvider(Project project) {
    myProject = project;
  }

  public List getOutgoingChanges(final VirtualFile vcsRoot, final boolean findRemote) throws VcsException {
    final Set<GitBranch> usedBranches = new HashSet<GitBranch>();
    final GitBranch currentBranch = GitBranch.current(myProject, vcsRoot);
    if (currentBranch == null) return Collections.emptyList();
    usedBranches.add(currentBranch);

    GitBranch remoteBranch = currentBranch;
    while (true) {
      remoteBranch = remoteBranch.tracked(myProject, vcsRoot);
      if (remoteBranch == null) return Collections.emptyList();
      
      if ((! findRemote) || remoteBranch.isRemote()) break;

      if (usedBranches.contains(remoteBranch)) return Collections.emptyList();
      usedBranches.add(remoteBranch);
    }

    final GitBranch finalRemoteBranch = remoteBranch;
    return GitUtil.getLocalCommittedChanges(myProject, vcsRoot, new Consumer<GitSimpleHandler>() {
      public void consume(final GitSimpleHandler handler) {
        handler.addParameters(finalRemoteBranch.getFullName() + "..HEAD");
      }
    });
  }
}
