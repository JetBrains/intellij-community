package org.hanuna.gitalk.git.reader;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import gitlog.GitLogComponent;
import org.hanuna.gitalk.log.commit.CommitData;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitDataReader {
  private Project myProject;

  public CommitDataReader(Project project) {
    myProject = project;
  }

  @NotNull
  public CommitData readCommitData(@NotNull String commitHash) {
    return readCommitsData(Collections.singletonList(commitHash)).get(0);
  }


  public List<CommitData> readCommitsData(@NotNull List<String> hashes) {
    GitRepository repository = ServiceManager.getService(myProject, GitLogComponent.class).getRepository();
    List<GitCommit> gitCommits;
    try {
      gitCommits = GitHistoryUtils.commitsDetails(myProject, new FilePathImpl(repository.getRoot()), null, hashes);
    }
    catch (VcsException e) {
      throw new IllegalStateException(e);
    }

    return ContainerUtil.map(gitCommits, new Function<GitCommit, CommitData>() {
      @Override
      public CommitData fun(GitCommit gitCommit) {
        return new CommitData(gitCommit);
      }
    });
  }

}
