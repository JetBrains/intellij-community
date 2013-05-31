package org.hanuna.gitalk.git.reader;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.CommitParents;
import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.git.reader.util.GitProcessFactory;
import org.hanuna.gitalk.git.reader.util.ProcessOutputReader;
import org.hanuna.gitalk.log.commit.parents.TimestampCommitParents;
import org.hanuna.gitalk.log.parser.CommitParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitParentsReader {
  public static final int COMMIT_BLOCK_SIZE = 1000;

  private long lastTimeStamp = 0;
  private Executor<Integer> progressUpdater;

  private Project myProject;
  @NotNull private final VirtualFile myRoot;
  private final boolean myReusePreviousGitOutput;
  private static List<CommitParents> ourPreviousOutput;

  public CommitParentsReader(@NotNull Project project, @NotNull VirtualFile root, boolean reusePreviousGitOutput) {
    myProject = project;
    myRoot = root;
    myReusePreviousGitOutput = reusePreviousGitOutput;
  }

  private List<TimestampCommitParents> nextBlock() throws IOException {
    final List<TimestampCommitParents> commitParentsList = new ArrayList<TimestampCommitParents>();
    final MyTimer gitThink = new MyTimer("gitThink");
    final MyTimer readTimer = new MyTimer("read commit parents");
    ProcessOutputReader outputReader = new ProcessOutputReader(progressUpdater, new Executor<String>() {
      private boolean wasReadFirstLine = false;

      @Override
      public void execute(String key) {
        if (!wasReadFirstLine) {
          wasReadFirstLine = true;
          gitThink.print();
          readTimer.clear();
        }
        TimestampCommitParents commitParents = CommitParser.parseTimestampParentHashes(key);
        commitParentsList.add(commitParents);
      }
    });
    if (lastTimeStamp == 0) {
      outputReader.startRead(GitProcessFactory.getInstance(myProject).firstPart(myRoot, COMMIT_BLOCK_SIZE));
    }
    else {
      outputReader.startRead(GitProcessFactory.getInstance(myProject).logPart(myRoot, lastTimeStamp, COMMIT_BLOCK_SIZE));
    }
    return commitParentsList;
  }

  private void removeElementsWithLastTimeStamp(List<TimestampCommitParents> commitParentsList) {
    int lastOkIndex = -5;
    for (int i = commitParentsList.size() - 1; i >= 0; i--) {
      if (commitParentsList.get(i).getTimestamp() != lastTimeStamp) {
        lastOkIndex = i;
        break;
      }
    }
    commitParentsList.subList(lastOkIndex + 1, commitParentsList.size()).clear();
  }

  /**
   * @return empty list, if all commits was readied
   */
  @NotNull
  public List<CommitParents> readNextBlock(final Consumer<String> statusUpdater) throws IOException{
    if (myReusePreviousGitOutput && ourPreviousOutput != null) {
      return ourPreviousOutput;
    }

    statusUpdater.consume("Loading Git history...");
    progressUpdater = new Executor<Integer>() {
      @Override
      public void execute(Integer key) {
        statusUpdater.consume("Reading " + key + " commits...");
      }
    };
    List<TimestampCommitParents> commitParentsList = nextBlock();
    TimestampCommitParents lastCommit = commitParentsList.get(commitParentsList.size() - 1);
    lastTimeStamp = lastCommit.getTimestamp();

    if (commitParentsList.size() == COMMIT_BLOCK_SIZE) {
      removeElementsWithLastTimeStamp(commitParentsList);
    }
    else {
      lastTimeStamp++;
    }
    List<CommitParents> commitParentses = Collections.<CommitParents>unmodifiableList(commitParentsList);
    ourPreviousOutput = commitParentses;
    return commitParentses;
  }

}
