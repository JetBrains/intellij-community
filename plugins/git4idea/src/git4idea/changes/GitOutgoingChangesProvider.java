package git4idea.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsOutgoingChangesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import git4idea.GitBranchesSearcher;
import git4idea.GitUtil;
import git4idea.commands.GitSimpleHandler;

import java.util.Collections;
import java.util.List;

public class GitOutgoingChangesProvider implements VcsOutgoingChangesProvider {
  private final Project myProject;

  public GitOutgoingChangesProvider(Project project) {
    myProject = project;
  }

  public List getOutgoingChanges(final VirtualFile vcsRoot, final boolean findRemote) throws VcsException {
    final GitBranchesSearcher searcher = new GitBranchesSearcher(myProject, vcsRoot, findRemote);
    if (searcher.getLocal() == null || searcher.getRemote() == null) return Collections.emptyList();

    return GitUtil.getLocalCommittedChanges(myProject, vcsRoot, new Consumer<GitSimpleHandler>() {
      public void consume(final GitSimpleHandler handler) {
        handler.addParameters(searcher.getRemote().getFullName() + "..HEAD");
      }
    });
  }
}
