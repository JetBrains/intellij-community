package git4idea.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsOutgoingChangesProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import git4idea.GitBranchesSearcher;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitSimpleHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class GitOutgoingChangesProvider implements VcsOutgoingChangesProvider<CommittedChangeList> {
  private final static Logger LOG = Logger.getInstance("#git4idea.changes.GitOutgoingChangesProvider");
  private final Project myProject;

  public GitOutgoingChangesProvider(Project project) {
    myProject = project;
  }

  public Pair<VcsRevisionNumber, List<CommittedChangeList>> getOutgoingChanges(final VirtualFile vcsRoot, final boolean findRemote)
    throws VcsException {
    LOG.debug("getOutgoingChanges root: " + vcsRoot.getPath());
    final GitBranchesSearcher searcher = new GitBranchesSearcher(myProject, vcsRoot, findRemote);
    if (searcher.getLocal() == null || searcher.getRemote() == null) {
      return new Pair<VcsRevisionNumber, List<CommittedChangeList>>(null, Collections.<CommittedChangeList>emptyList());
    }
    final GitRevisionNumber base = searcher.getLocal().getMergeBase(myProject, vcsRoot, searcher.getRemote());
    if (base == null) {
      return new Pair<VcsRevisionNumber, List<CommittedChangeList>>(null, Collections.<CommittedChangeList>emptyList());
    }
    final List<CommittedChangeList> lists = GitUtil.getLocalCommittedChanges(myProject, vcsRoot, new Consumer<GitSimpleHandler>() {
      public void consume(final GitSimpleHandler handler) {
        handler.addParameters(base.asString() + "..HEAD");
      }
    });
    return new Pair<VcsRevisionNumber, List<CommittedChangeList>>(base, lists);
  }

  @Nullable
  public VcsRevisionNumber getMergeBaseNumber(final VirtualFile anyFileUnderRoot) throws VcsException {
    LOG.debug("getMergeBaseNumber parameter: " + anyFileUnderRoot.getPath());
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    final VirtualFile root = vcsManager.getVcsRootFor(anyFileUnderRoot);
    if (root == null) {
      LOG.info("VCS root not found");
      return null;
    }

    final GitBranchesSearcher searcher = new GitBranchesSearcher(myProject, root, true);
    if (searcher.getLocal() == null || searcher.getRemote() == null) {
      LOG.info("local or remote not found");
      return null;
    }
    final GitRevisionNumber base = searcher.getLocal().getMergeBase(myProject, root, searcher.getRemote());
    LOG.debug("found base: " + ((base == null) ? null : base.asString()));
    return base;
  }
}
