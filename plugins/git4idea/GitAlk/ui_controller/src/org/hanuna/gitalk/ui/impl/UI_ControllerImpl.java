package org.hanuna.gitalk.ui.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import git4idea.repo.GitRepository;
import gitlog.GitActionHandlerImpl;
import gitlog.GitLogComponent;
import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.common.CacheGet;
import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.Function;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
import org.hanuna.gitalk.data.CommitDataGetter;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.data.DataPackUtils;
import org.hanuna.gitalk.data.impl.CacheCommitDataGetter;
import org.hanuna.gitalk.data.impl.DataLoaderImpl;
import org.hanuna.gitalk.data.impl.FakeCommitsInfo;
import org.hanuna.gitalk.data.rebase.GitActionHandler;
import org.hanuna.gitalk.data.rebase.InteractiveRebaseBuilder;
import org.hanuna.gitalk.git.reader.CommitDataReader;
import org.hanuna.gitalk.git.reader.util.GitException;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graphmodel.FragmentManager;
import org.hanuna.gitalk.graphmodel.GraphFragment;
import org.hanuna.gitalk.log.commit.CommitData;
import org.hanuna.gitalk.log.commit.parents.FakeCommitParents;
import org.hanuna.gitalk.log.commit.parents.RebaseCommand;
import org.hanuna.gitalk.printmodel.SelectController;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.ui.ControllerListener;
import org.hanuna.gitalk.ui.DragDropListener;
import org.hanuna.gitalk.ui.UI_Controller;
import org.hanuna.gitalk.ui.tables.GraphTableModel;
import org.hanuna.gitalk.ui.tables.refs.refs.RefTreeModel;
import org.hanuna.gitalk.ui.tables.refs.refs.RefTreeModelImpl;
import org.hanuna.gitalk.ui.tables.refs.refs.RefTreeTableModel;
import org.jdesktop.swingx.treetable.TreeTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;
import java.io.IOException;
import java.util.*;

/**
 * @author erokhins
 */
public class UI_ControllerImpl implements UI_Controller {

  private volatile DataLoaderImpl dataLoader;
  private final EventsController events = new EventsController();
  private final Project myProject;
  private final BackgroundTaskQueue myDataLoaderQueue;

  private DataPack dataPack;
  private DataPackUtils dataPackUtils;
  private RefTreeTableModel refTableModel;
  private RefTreeModel refTreeModel;

  private GraphTableModel graphTableModel;

  private GraphElement prevGraphElement = null;
  private Set<Hash> prevSelectionBranches;
  private List<Ref> myRefs;

  private DragDropListener dragDropListener = DragDropListener.EMPTY;
  private GitActionHandler myGitActionHandler;
  private final GitActionHandler.Callback myCallback = new Callback();

  private final MyInteractiveRebaseBuilder rebaseDelegate = new MyInteractiveRebaseBuilder();
  private InteractiveRebaseBuilder myInteractiveRebaseBuilder = new InteractiveRebaseBuilder() {

    @Override
    public void startRebase(Ref subjectRef, Node onto) {
      rebaseDelegate.startRebase(subjectRef, onto);
      refresh(true);
    }

    @Override
    public void startRebaseOnto(Ref subjectRef, Node base, List<Node> nodesToRebase) {
      rebaseDelegate.startRebaseOnto(subjectRef, base, nodesToRebase);
      refresh(true);
    }

    @Override
    public void moveCommits(Ref subjectRef, Node base, InsertPosition position, List<Node> nodesToInsert) {
      rebaseDelegate.moveCommits(subjectRef, base, position, nodesToInsert);
      refresh(true);
    }

    @Override
    public void fixUp(Ref subjectRef, Node target, List<Node> nodesToFixUp) {
      rebaseDelegate.fixUp(subjectRef, target, nodesToFixUp);
      refresh(true);
    }

    @Override
    public List<RebaseCommand> getRebaseCommands() {
      return super.getRebaseCommands();
    }
  };
  private boolean myCommitDetailsPreloaded;

  public UI_ControllerImpl(Project project) {
    myProject = project;
    myGitActionHandler = new GitActionHandlerImpl(myProject, this);
    myDataLoaderQueue = new BackgroundTaskQueue(myProject, "Loading...");
  }

  private void dataInit() {
    dataPack = dataLoader.getDataPack();
    refTreeModel = new RefTreeModelImpl(dataPack.getRefsModel());
    refTableModel = new RefTreeTableModel(refTreeModel);
    myRefs = dataPack.getRefsModel().getAllRefs();
    graphTableModel = new GraphTableModel(dataPack);
    dataPackUtils = new DataPackUtils(dataPack);

    prevSelectionBranches = new HashSet<Hash>(refTreeModel.getCheckedCommits());

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        preloadCommitDetails();
      }
    });

  }

  private final CacheGet<Hash, CommitData> commitDataCache = new CacheGet<Hash, CommitData>(new Function<Hash, CommitData>() {
    @NotNull
    @Override
    public CommitData get(@NotNull Hash key) {
      return CommitDataReader.readCommitData(myProject, key.toStrHash());
      }
  }, 5000);


  public void init(final boolean readAllLog, boolean inBackground, final boolean reusePreviousGitOutput) {
    final Consumer<ProgressIndicator> doInit = new Consumer<ProgressIndicator>() {
      @Override
      public void consume(final ProgressIndicator indicator) {
        events.setState(ControllerListener.State.PROGRESS);
        dataLoader = new DataLoaderImpl(myProject, reusePreviousGitOutput, commitDataCache);
        Executor<String> statusUpdater = new Executor<String>() {
          @Override
          public void execute(String key) {
            events.setUpdateProgressMessage(key);
            indicator.setText(key);
          }
        };

        try {
          if (readAllLog) {
            dataLoader.readAllLog(statusUpdater);
          }
          else {
            dataLoader.readNextPart(statusUpdater, rebaseDelegate.getFakeCommitsInfo());
          }
          dataInit();
          events.setState(ControllerListener.State.USUAL);
        }
        catch (IOException e) {
          events.setState(ControllerListener.State.ERROR);
          events.setErrorMessage(e.getMessage());
        }
        catch (GitException e) {
          events.setState(ControllerListener.State.ERROR);
          events.setErrorMessage(e.getMessage());
        }

        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            updateUI();
          }
        });
      }
    };

    if (inBackground) {
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          myDataLoaderQueue.run(new Task.Backgroundable(myProject, "Loading...", false) {
            public void run(@NotNull final ProgressIndicator indicator) {
              doInit.consume(indicator);
            }
          });
        }
      });

    }
    else {
      doInit.consume(new EmptyProgressIndicator());
    }

  }

  private void preloadCommitDetails() {
    if (myCommitDetailsPreloaded) {
      return;
    }
    CommitDataGetter commitDataGetter = dataPack.getCommitDataGetter();
    if (commitDataGetter instanceof CacheCommitDataGetter) {
      ((CacheCommitDataGetter)commitDataGetter).initiallyPreloadCommitDetails();
    }
    myCommitDetailsPreloaded = true;
  }


  @Override
  @NotNull
  public TableModel getGraphTableModel() {
    return graphTableModel;
  }

  @Override
  @NotNull
  public TreeTableModel getRefsTreeTableModel() {
    return refTableModel;
  }

  @Override
  public RefTreeModel getRefTreeModel() {
    return refTreeModel;
  }

  @Override
  public void addControllerListener(@NotNull ControllerListener listener) {
    events.addListener(listener);
  }

  @Override
  public void removeAllListeners() {
    events.removeAllListeners();
  }

  @Override
  public void over(@Nullable GraphElement graphElement) {
    SelectController selectController = dataPack.getPrintCellModel().getSelectController();
    FragmentManager fragmentManager = dataPack.getGraphModel().getFragmentManager();
    if (graphElement == prevGraphElement) {
      return;
    }
    else {
      prevGraphElement = graphElement;
    }
    selectController.deselectAll();
    if (graphElement == null) {
      events.runUpdateUI();
    }
    else {
      GraphFragment graphFragment = fragmentManager.relateFragment(graphElement);
      selectController.select(graphFragment);
      events.runUpdateUI();
    }
  }

  public void click(@Nullable GraphElement graphElement) {
    SelectController selectController = dataPack.getPrintCellModel().getSelectController();
    FragmentManager fragmentController = dataPack.getGraphModel().getFragmentManager();
    selectController.deselectAll();
    if (graphElement == null) {
      return;
    }
    GraphFragment fragment = fragmentController.relateFragment(graphElement);
    if (fragment == null) {
      return;
    }
    UpdateRequest updateRequest = fragmentController.changeVisibility(fragment);
    events.runUpdateUI();
    //TODO:
    events.runJumpToRow(updateRequest.from());
  }

  public void click(int rowIndex) {
    dataPack.getPrintCellModel().getCommitSelectController().deselectAll();
    Node node = dataPackUtils.getNode(rowIndex);
    if (node != null) {
      FragmentManager fragmentController = dataPack.getGraphModel().getFragmentManager();
      dataPack.getPrintCellModel().getCommitSelectController().select(fragmentController.allCommitsCurrentBranch(node));
    }
    events.runUpdateUI();
  }

  @Override
  public void doubleClick(int rowIndex) {
    if (rowIndex == graphTableModel.getRowCount() - 1) {
      readNextPart();
    }
  }

  @Override
  public void updateVisibleBranches() {
    final Set<Hash> checkedCommitHashes = refTreeModel.getCheckedCommits();
    if (!prevSelectionBranches.equals(checkedCommitHashes)) {
      MyTimer timer = new MyTimer("update branch shows");

      prevSelectionBranches = new HashSet<Hash>(checkedCommitHashes);
      dataPack.getGraphModel().setVisibleBranchesNodes(new Function<Node, Boolean>() {
        @NotNull
        @Override
        public Boolean get(@NotNull Node key) {
          return key.getType() == Node.NodeType.COMMIT_NODE && checkedCommitHashes.contains(key.getCommitHash());
        }
      });

      events.runUpdateUI();
      //TODO:
      events.runJumpToRow(0);

      timer.print();
    }
  }


  @Override
  public void readNextPart() {
    myDataLoaderQueue.run(new Task.Backgroundable(myProject, "Loading...", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          events.setState(ControllerListener.State.PROGRESS);

          dataLoader.readNextPart(new Executor<String>() {
            @Override
            public void execute(String key) {
              events.setUpdateProgressMessage(key);
            }
          }, rebaseDelegate.getFakeCommitsInfo());


          events.setState(ControllerListener.State.USUAL);
          events.runUpdateUI();

        }
        catch (IOException e) {
          events.setState(ControllerListener.State.ERROR);
          events.setErrorMessage(e.getMessage());
        }
        catch (GitException e) {
          events.setState(ControllerListener.State.ERROR);
          events.setErrorMessage(e.getMessage());
        }
      }
    });
  }

  @Override
  public void showAll() {
    dataPack.getGraphModel().getFragmentManager().showAll();
    events.runUpdateUI();
    events.runJumpToRow(0);
  }

  @Override
  public void hideAll() {
    new Task.Backgroundable(myProject, "Hiding long branches...", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            events.setState(ControllerListener.State.PROGRESS);
            events.setUpdateProgressMessage("Hide long branches");
            MyTimer timer = new MyTimer("hide All");
            dataPack.getGraphModel().getFragmentManager().hideAll();

            events.runUpdateUI();
            //TODO:
            events.runJumpToRow(0);
            timer.print();

            events.setState(ControllerListener.State.USUAL);
          }
        });
      }
    }.queue();
  }

  @Override
  public void setLongEdgeVisibility(boolean visibility) {
    dataPack.getPrintCellModel().setLongEdgeVisibility(visibility);
    events.runUpdateUI();
  }

  @Override
  public void jumpToCommit(Hash commitHash) {
    int row = dataPackUtils.getRowByHash(commitHash);
    if (row != -1) {
      events.runJumpToRow(row);
    }
  }

  @Override
  public List<Ref> getRefs() {
    return myRefs;
  }

  @NotNull
  @Override
  public DragDropListener getDragDropListener() {
    return dragDropListener;
  }

  public void setDragDropListener(@NotNull DragDropListener dragDropListener) {
    this.dragDropListener = dragDropListener;
  }

  @NotNull
  @Override
  public InteractiveRebaseBuilder getInteractiveRebaseBuilder() {
    return myInteractiveRebaseBuilder;
  }

  @NotNull
  @Override
  public GitActionHandler getGitActionHandler() {
    return myGitActionHandler;
  }

  public void setGitActionHandler(@NotNull GitActionHandler gitActionHandler) {
    myGitActionHandler = gitActionHandler;
  }

  @Override
  public DataPack getDataPack() {
    return dataPack;
  }

  @Override
  public DataPackUtils getDataPackUtils() {
    return dataPackUtils;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public void refresh(boolean reusePreviousGitOutput) {
    init(false, true, reusePreviousGitOutput);
  }

  public void updateUI() {
    events.runUpdateUI();
  }

  @Override
  public void applyInteractiveRebase() {
    if (rebaseDelegate.resultRef == null) {
      return;
    }
    getGitActionHandler().interactiveRebase(rebaseDelegate.subjectRef, rebaseDelegate.branchBase, getCallback(),
                                            rebaseDelegate.getRebaseCommands());
    cancelInteractiveRebase();
  }

  @Override
  public void cancelInteractiveRebase() {
    rebaseDelegate.reset();
    refresh(true);
  }

  @Override
  public GitActionHandler.Callback getCallback() {
    return myCallback;
  }

  @Override
  public GitRepository getRepository() {
    return ServiceManager.getService(myProject, GitLogComponent.class).getRepository();
  }

  private class MyInteractiveRebaseBuilder extends InteractiveRebaseBuilder {

    private Node branchBase = null;
    private int insertAfter = -1;
    private List<FakeCommitParents> fakeBranch = new ArrayList<FakeCommitParents>();
    private Ref subjectRef = null;
    private Ref resultRef = null;

    private FakeCommitParents createFake(Hash oldHash, Hash newParent) {
      return new FakeCommitParents(newParent, new RebaseCommand(RebaseCommand.RebaseCommandKind.PICK, FakeCommitParents.getOriginal(oldHash)));
    }

    public void reset() {
      branchBase = null;
      insertAfter = -1;
      fakeBranch.clear();
      subjectRef = null;
      resultRef = null;
    }

    @Override
    public void startRebase(Ref subjectRef, Node onto) {
      List<Node> commitsToRebase =
        getDataPackUtils().getCommitsDownToCommon(onto, getDataPackUtils().getNodeByHash(subjectRef.getCommitHash()));
      startRebaseOnto(subjectRef, onto, commitsToRebase.subList(0, commitsToRebase.size() - 1));
    }

    private void setResultRef(Ref subjectRef) {
      if (resultRef == null) {
        this.subjectRef = subjectRef;
      }
      this.resultRef = new Ref(fakeBranch.get(0).getCommitHash(), subjectRef.getName(), Ref.RefType.BRANCH_UNDER_INTERACTIVE_REBASE);
    }

    @Override
    public void startRebaseOnto(Ref subjectRef, Node base, List<Node> nodesToRebase) {
      reset();
      this.branchBase = base;
      this.insertAfter = base.getRowIndex();

      this.fakeBranch = createFakeCommits(base, nodesToRebase);

      setResultRef(subjectRef);
    }

    @Override
    public void moveCommits(Ref subjectRef, Node base, InsertPosition position, List<Node> nodesToInsert) {
      if (resultRef != null && resultRef != subjectRef) {
        reset();
      }
      DataPackUtils du = getDataPackUtils();
      if (position == InsertPosition.BELOW) {
        //insertAfter = base.getRowIndex() + 1;
        // TODO: what if many edges?
        base = getParent(base);
      }
      else {
        //insertAfter = base.getRowIndex();
      }
      Node lowestInserted = nodesToInsert.get(nodesToInsert.size() - 1);
      if (du.isAncestorOf(base, lowestInserted)) {
        this.branchBase = base;
      }
      else {
        // TODO: many parents?
        this.branchBase = getParent(lowestInserted);
      }

      if (!fakeBranch.isEmpty()) {
        FakeCommitParents lowestFakeCommit = fakeBranch.get(fakeBranch.size() - 1);
        Node lowestFakeNode = du.getNodeByHash(lowestFakeCommit.getCommitHash());

        if (lowestFakeNode == branchBase || du.isAncestorOf(lowestFakeNode, branchBase)) {
          branchBase = getParent(lowestFakeNode);
        }
      }

      Set<Node> nodesToRemove = new HashSet<Node>(nodesToInsert);
      List<Node> branch = du.getCommitsInBranchAboveBase(this.branchBase, du.getNodeByHash(subjectRef.getCommitHash()));
      List<Node> result = new ArrayList<Node>();
      boolean baseFound = false;
      for (Node node : branch) {
        if (node == base) {
          result.addAll(nodesToInsert);
          baseFound = true;
        }
        if (!nodesToRemove.contains(node)) {
          result.add(node);
        }
      }
      if (!baseFound) {
        result.addAll(nodesToInsert);
      }

      this.fakeBranch = createFakeCommits(this.branchBase, result);

      int maxIndex = -1;
      for (Node node : result) {
        if (maxIndex < node.getRowIndex()) {
          maxIndex = node.getRowIndex();
        }
      }
      insertAfter = maxIndex + 1;

      setResultRef(subjectRef);
    }

    private List<FakeCommitParents> createFakeCommits(Node base, List<Node> nodesToRebase) {
      List<FakeCommitParents> result = new ArrayList<FakeCommitParents>();
      List<Node> reversed = reverse(nodesToRebase);
      Hash parent = base.getCommitHash();
      for (Node node : reversed) {
        FakeCommitParents fakeCommit = createFake(node.getCommitHash(), parent);
        parent = fakeCommit.getCommitHash();
        result.add(fakeCommit);
      }
      Collections.reverse(result);
      return result;
    }

    private List<Node> reverse(List<Node> nodesToRebase) {
      List<Node> reversed = new ArrayList<Node>(nodesToRebase);
      Collections.reverse(reversed);
      return reversed;
    }

    private Node getParent(Node base) {
      return base.getDownEdges().get(0).getDownNode();
    }

    @Override
    public void fixUp(Ref subjectRef, Node target, List<Node> nodesToFixUp) {
      // TODO
    }

    @Override
    public List<RebaseCommand> getRebaseCommands() {
      return ContainerUtil.map(fakeBranch, new com.intellij.util.Function<FakeCommitParents, RebaseCommand>() {
        @Override
        public RebaseCommand fun(FakeCommitParents fakeCommitParents) {
          return fakeCommitParents.getCommand();
        }
      });
    }

    public FakeCommitsInfo getFakeCommitsInfo() {
      return new FakeCommitsInfo(fakeBranch, branchBase, insertAfter, resultRef, subjectRef);
    }
  }

  private static class Callback implements GitActionHandler.Callback {
    @Override
    public void disableModifications() {

    }

    @Override
    public void enableModifications() {

    }

    @Override
    public void interactiveCommandApplied(RebaseCommand command) {

    }
  }

}
