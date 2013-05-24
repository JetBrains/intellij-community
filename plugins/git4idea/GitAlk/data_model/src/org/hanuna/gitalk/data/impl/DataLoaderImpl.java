package org.hanuna.gitalk.data.impl;

import com.intellij.openapi.project.Project;
import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.data.DataLoader;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.data.rebase.InteractiveRebaseBuilder;
import org.hanuna.gitalk.git.reader.CommitParentsReader;
import org.hanuna.gitalk.git.reader.FullLogCommitParentsReader;
import org.hanuna.gitalk.git.reader.RefReader;
import org.hanuna.gitalk.git.reader.util.GitException;
import org.hanuna.gitalk.log.commit.CommitParents;
import org.hanuna.gitalk.refs.Ref;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

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
  public void readNextPart(@NotNull Executor<String> statusUpdater) throws IOException, GitException {
    switch (state) {
      case ALL_LOG_READER:
        throw new IllegalStateException("data was read");
      case UNINITIALIZED:
        List<CommitParents> commitParentsList = partReader.readNextBlock(statusUpdater);
        state = State.PART_LOG_READER;

        List<Ref> allRefs = new RefReader(myProject).readAllRefs();
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
  public InteractiveRebaseBuilder getInteractiveRebaseBuilder() {
    return new InteractiveRebaseBuilder();
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
