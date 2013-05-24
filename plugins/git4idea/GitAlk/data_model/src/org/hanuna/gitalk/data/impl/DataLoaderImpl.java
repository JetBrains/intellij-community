package org.hanuna.gitalk.data.impl;

import com.intellij.openapi.project.Project;
import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.data.DataLoader;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.git.reader.CommitParentsReader;
import org.hanuna.gitalk.git.reader.FullLogCommitParentsReader;
import org.hanuna.gitalk.git.reader.RefReader;
import org.hanuna.gitalk.git.reader.util.GitException;
import org.hanuna.gitalk.log.commit.CommitParents;
import org.hanuna.gitalk.refs.Ref;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

/**
 * @author erokhins
 */
public class DataLoaderImpl implements DataLoader {
  private final Project myProject;
  private State state = State.UNINITIALIZED;
  private volatile DataPackImpl dataPack;
  private CommitParentsReader partReader;

  public DataLoaderImpl(Project project) {
    myProject = project;
    partReader = new CommitParentsReader(project);
  }

  @Override
  public void readAllLog(@NotNull Executor<String> statusUpdater) throws IOException, GitException {
    if (state != State.UNINITIALIZED) {
      throw new IllegalStateException("data was read");
    }
    state = State.ALL_LOG_READER;
    FullLogCommitParentsReader reader = new FullLogCommitParentsReader(myProject, statusUpdater);
    List<CommitParents> commitParentsList = reader.readAllCommitParents();

    List<Ref> allRefs = new RefReader(myProject).readAllRefs();
    dataPack = DataPackImpl.buildDataPack(commitParentsList, allRefs, statusUpdater, myProject);
  }

  @Override
  public void readNextPart(@NotNull Executor<String> statusUpdater, @NotNull FakeCommitsInfo fakeCommits) throws IOException, GitException {
    switch (state) {
      case ALL_LOG_READER:
        throw new IllegalStateException("data was read");
      case UNINITIALIZED:
        List<Ref> allRefs = new ArrayList<Ref>();
        allRefs.addAll(new RefReader(myProject).readAllRefs());
        if (fakeCommits.resultRef != null) {
          allRefs.add(0, fakeCommits.resultRef);
          allRefs.remove(fakeCommits.subjectRef);
        }

        Set<Hash> visible = new HashSet<Hash>();
        for (Ref ref : allRefs) {
          if (ref.getType() != Ref.RefType.HEAD) {
            visible.add(ref.getCommitHash());
          }
        }

        System.out.println("=== readNextPart() called with " + fakeCommits.commits.size() + " fake commits");
        List<CommitParents> commitParentsList = new ArrayList<CommitParents>();
        List<CommitParents> commits = partReader.readNextBlock(statusUpdater);
        for (int i = 0; i < commits.size(); i++) {
          CommitParents commit = commits.get(i);
          if (fakeCommits.base != null && commitParentsList.size() + fakeCommits.commits.size() == fakeCommits.insertAbove) {
            commitParentsList.addAll(fakeCommits.commits);
            for (CommitParents fakeCommit : fakeCommits.commits) {
              //System.out.println("Visible from fake_" + fakeCommit.getCommitHash() + ": " + fakeCommit.getParentHashes());
              visible.addAll(fakeCommit.getParentHashes());
            }
          }
          if (visible.contains(commit.getCommitHash())) {
            commitParentsList.add(commit);
            visible.addAll(commit.getParentHashes());

          }
          else {
            System.out.println("Hidden: " + commit.getCommitHash());
          }
        }
        state = State.PART_LOG_READER;

        dataPack = DataPackImpl.buildDataPack(commitParentsList, allRefs, statusUpdater, myProject);
        break;
      case PART_LOG_READER:
        List<CommitParents> nextPart = partReader.readNextBlock(statusUpdater);
        dataPack.appendCommits(nextPart, statusUpdater);
        break;
      default:
        throw new IllegalStateException();
    }
  }

  @NotNull
  @Override
  public DataPack getDataPack() {
    return dataPack;
  }

  private static enum State {
    UNINITIALIZED,
    ALL_LOG_READER,
    PART_LOG_READER
  }
}
