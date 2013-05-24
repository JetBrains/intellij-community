package org.hanuna.gitalk.data.impl;

import com.intellij.openapi.project.Project;
import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.data.DataLoader;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.data.rebase.InteractiveRebaseBuilder;
import org.hanuna.gitalk.git.reader.CommitParentsReader;
import org.hanuna.gitalk.git.reader.FullLogCommitParentsReader;
import org.hanuna.gitalk.git.reader.RefReader;
import org.hanuna.gitalk.git.reader.util.GitException;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.log.commit.CommitParents;
import org.hanuna.gitalk.log.commit.parents.FakeCommitParents;
import org.hanuna.gitalk.log.commit.parents.RebaseCommand;
import org.hanuna.gitalk.refs.Ref;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
public class DataLoaderImpl implements DataLoader {
  private final Project myProject;
  private final MyInteractiveRebaseBuilder myInteractiveRebaseBuilder;
  private State state = State.UNINITIALIZED;
  private volatile DataPackImpl dataPack;
  private CommitParentsReader partReader;

  public DataLoaderImpl(Project project) {
    myProject = project;
    partReader = new CommitParentsReader(project);
    myInteractiveRebaseBuilder = new MyInteractiveRebaseBuilder();
  }

  private DataLoaderImpl(Project project, MyInteractiveRebaseBuilder builder) {
    myProject = project;
    partReader = new CommitParentsReader(project);
    myInteractiveRebaseBuilder = builder;
  }

  public DataLoaderImpl copyInteractive() {
    return new DataLoaderImpl(myProject, myInteractiveRebaseBuilder);
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
        System.out.println("=== readNextPart() called with " + myInteractiveRebaseBuilder.fakeCommits.size() + " fake commits");
        List<CommitParents> commitParentsList = new ArrayList<CommitParents>();
        List<CommitParents> commits = partReader.readNextBlock(statusUpdater);
        for (CommitParents commit : commits) {
          if (myInteractiveRebaseBuilder.base != null && commit.getCommitHash().equals(myInteractiveRebaseBuilder.base.getCommitHash())) {
            commitParentsList.addAll(myInteractiveRebaseBuilder.fakeCommits);
          }
          commitParentsList.add(commit);
        }
        state = State.PART_LOG_READER;

        List<Ref> allRefs = new ArrayList<Ref>();
        allRefs.addAll(new RefReader(myProject).readAllRefs());
        if (myInteractiveRebaseBuilder.resultRef != null) {
          allRefs.add(myInteractiveRebaseBuilder.resultRef);
        }
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
    return myInteractiveRebaseBuilder;
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

  private class MyInteractiveRebaseBuilder extends InteractiveRebaseBuilder {

    private Node base = null;
    private List<FakeCommitParents> fakeCommits = new ArrayList<FakeCommitParents>();
    private Ref subjectRef = null;
    private Ref resultRef = null;

    @Override
    public void startRebase(Ref subjectRef, Node onto) {
      // todo find base
      startRebaseOnto(subjectRef, onto, Collections.<Node>emptyList());
    }

    @Override
    public void startRebaseOnto(Ref subjectRef, Node base, List<Node> nodesToRebase) {
      this.subjectRef = subjectRef;
      this.base = base;
      fakeCommits.clear();

      // Copy commits
      List<Node> reversed = new ArrayList<Node>(nodesToRebase);
      Collections.reverse(reversed);
      Hash parent = base.getCommitHash();
      for (Node node : reversed) {
        FakeCommitParents fakeCommit =
          new FakeCommitParents(parent, new RebaseCommand(RebaseCommand.RebaseCommandKind.PICK, node.getCommitHash()));
        parent = fakeCommit.getCommitHash();
        fakeCommits.add(fakeCommit);
      }

      Collections.reverse(fakeCommits);
      resultRef = new Ref(parent, subjectRef.getName(), Ref.RefType.BRANCH_UNDER_INTERACTIVE_REBASE);

      // todo move label

    }

    @Override
    public void moveCommits(Ref subjectRef, Node base, InsertPosition position, List<Node> nodesToInsert) {
      super.moveCommits(subjectRef, base, position, nodesToInsert); // TODO
    }

    @Override
    public void fixUp(Ref subjectRef, Node target, List<Node> nodesToFixUp) {
      super.fixUp(subjectRef, target, nodesToFixUp); // TODO
    }

    @Override
    public List<RebaseCommand> getRebaseCommands() {
      return super.getRebaseCommands(); // TODO
    }
  }
}
