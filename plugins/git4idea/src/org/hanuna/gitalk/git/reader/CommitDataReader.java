package org.hanuna.gitalk.git.reader;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.vcs.log.CommitData;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsCommit;
import git4idea.GitVcsCommit;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.log.commit.parents.FakeCommitParents;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author erokhins
 */
public class CommitDataReader {

  public static List<CommitData> readCommitsData(Project project, @NotNull List<String> hashes, VirtualFile root) {
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

    List<GitCommit> gitCommits;
    try {
      MyTimer timer = new MyTimer();
      timer.clear();
      gitCommits = GitHistoryUtils.commitsDetails(project, new FilePathImpl(root), null, trueHashes);
      System.out.println("Details loading took " + timer.get() + "ms for " + trueHashes.size() + " hashes");
    }
    catch (VcsException e) {
      throw new IllegalStateException(e);
    }

    List<CommitData> result = new SmartList<CommitData>();
    for (GitCommit gitCommit : gitCommits) {
      VcsCommit commit = new GitVcsCommit(gitCommit);
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
        result.add(new CommitData(commit, Hash.build(fakeHash)));
      }

      result.add(new CommitData(commit));
    }

    return result;
  }

}
