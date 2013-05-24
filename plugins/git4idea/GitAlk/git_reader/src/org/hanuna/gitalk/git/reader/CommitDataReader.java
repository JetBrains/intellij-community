package org.hanuna.gitalk.git.reader;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.SmartList;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import gitlog.GitLogComponent;
import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.log.commit.CommitData;
import org.hanuna.gitalk.log.commit.parents.FakeCommitParents;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
    // true -> fake
    Map<String, String> fakeHashes = new HashMap<String, String>();
    Set<String> trueHashes = new HashSet<String>();
    for (String hash : hashes) {
      if (FakeCommitParents.isFake(hash)) {
        String trueHash = FakeCommitParents.getOriginal(hash);
        fakeHashes.put(trueHash, hash);

        trueHashes.add(trueHash);
      }
      else {
        trueHashes.add(hash);
      }
    }

    GitRepository repository = ServiceManager.getService(myProject, GitLogComponent.class).getRepository();
    List<GitCommit> gitCommits;
    try {
      MyTimer timer = new MyTimer();
      timer.clear();
      gitCommits = GitHistoryUtils.commitsDetails(myProject, new FilePathImpl(repository.getRoot()), null, trueHashes);
      System.out.println("Details loading took " + timer.get() + "ms for " + trueHashes.size() + " hashes");
    }
    catch (VcsException e) {
      throw new IllegalStateException(e);
    }

    SmartList<CommitData> result = new SmartList<CommitData>();
    for (GitCommit gitCommit : gitCommits) {
      String longHash = gitCommit.getHash().getValue();


      // TODO
      // HACK HACK HACK: hashes have different lengths
      String fakeHash = null;
      for (Map.Entry<String, String> entry : fakeHashes.entrySet()) {
        if (longHash.startsWith(entry.getKey())) {
          fakeHash = entry.getValue();
          break;
        }
      }
      if (fakeHash != null) {
        result.add(new CommitData(gitCommit, Hash.build(fakeHash)));
      }

      result.add(new CommitData(gitCommit));
    }

    return result;
  }

}
