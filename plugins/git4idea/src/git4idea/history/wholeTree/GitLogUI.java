/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.actions.RefreshAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.CaptionIcon;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vcs.ui.SearchFieldAction;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.table.JBTable;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.AdjustComponentWhenShown;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.MoreAction;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitBrancher;
import git4idea.changes.GitChangeUtils;
import git4idea.history.browser.*;
import git4idea.repo.GitRepository;
import icons.Git4ideaIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author irengrig
 */
public class GitLogUI implements Disposable {
  private final static Logger LOG = Logger.getInstance("#git4idea.history.wholeTree.GitLogUI");
  public static final SimpleTextAttributes HIGHLIGHT_TEXT_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_SEARCH_MATCH, UIUtil.getTableForeground());
  public static final String GIT_LOG_TABLE_PLACE = "git log table";
  private final Project myProject;
  private final GitVcs myVcs;

  private BigTableTableModel myTableModel;
  private DetailsCache myDetailsCache;
  private final Mediator myMediator;
  private Splitter mySplitter;
  private GitTableScrollChangeListener myMyChangeListener;
  private List<VirtualFile> myRootsUnderVcs;
  private final Map<VirtualFile, CachedRefs> myRefs;
  private final SymbolicRefs myRecalculatedCommon;
  private UIRefresh myUIRefresh;
  private JBTable myJBTable;
  private GraphGutter myGraphGutter;
  private RepositoryChangesBrowser myRepositoryChangesBrowser;
  final List<CommitI> myCommitsInRepositoryChangesBrowser;
  private boolean myDataBeingAdded;
  private boolean myStarted;
  private String myPreviousFilter;
  private final CommentSearchContext myCommentSearchContext;
  private List<String> myUsersSearchContext;
  private String mySelectedBranch;
  private BranchSelectorAction myBranchSelectorAction;
  private final DescriptionRenderer myDescriptionRenderer;

  private GenericDetailsLoader<CommitI, GitHeavyCommit> myDetailsLoader;
  private GenericDetailsLoader<CommitI, List<String>> myBranchesLoader;
  private GitLogDetailsPanel myDetailsPanel;

  private StepType myState;
  private MoreAction myMoreAction;
  private UsersFilterAction myUsersFilterAction;
  private MyFilterUi myUserFilterI;
  private MyRefreshAction myRefreshAction;
  private MyStructureFilter myStructureFilter;
  private StructureFilterAction myStructureFilterAction;
  private DatesFilterAction myDatesFilterAction;
  private AnAction myCopyHashAction;
  // todo group somewhere??
  private Consumer<CommitI> myDetailsLoaderImpl;
  private Consumer<CommitI> myBranchesLoaderImpl;
  private final RequestsMerger mySelectionRequestsMerger;

  private final TableCellRenderer myAuthorRenderer;
  private MyRootsAction myRootsAction;
  private JPanel myEqualToHeadr;
  private boolean myThereIsDisordering;
  private final GitLogUI.MyShowTreeAction myMyShowTreeAction;
  private final GitLogShowOnlyStarredCommitsAction myFilterStarredAction;
  private boolean myIsFilterByStarOn;
  private JLabel myOrderLabel;
  private boolean myProjectScope;
  private ActionPopupMenu myContextMenu;
  private final Map<AbstractHash, Long> myMarked;
  private final Runnable myRefresh;
  private JViewport myTableViewPort;
  private GitLogUI.MyGotoCommitAction myMyGotoCommitAction;
  private final Set<VirtualFile> myClearedHighlightingRoots;
  private final Splitter myDetailsSplitter;
  private JScrollPane myTableScrollPane;
  private GitLogUI.MyTextFieldAction myTextFieldAction;
  private DatesFilterI myDatesFilter;
  private GitLogSettings mySettings;

  public GitLogUI(Project project, final Mediator mediator) {
    myProject = project;
    myVcs = GitVcs.getInstance(project);
    mySettings = GitLogSettings.getInstance(myProject);
    myMediator = mediator;
    myCommentSearchContext = new CommentSearchContext();
    myUsersSearchContext = new ArrayList<String>();
    myRefs = new HashMap<VirtualFile, CachedRefs>();
    myRecalculatedCommon = new SymbolicRefs();
    myPreviousFilter = "";
    myDescriptionRenderer = new DescriptionRenderer();
    myCommentSearchContext.addHighlighter(myDescriptionRenderer.myInner.myWorker);
    myCommitsInRepositoryChangesBrowser = new ArrayList<CommitI>();
    myMarked = new HashMap<AbstractHash, Long>();
    myClearedHighlightingRoots = new HashSet<VirtualFile>();

    mySelectionRequestsMerger = new RequestsMerger(new Runnable() {
      @Override
      public void run() {
        selectionChanged();
      }
    }, new Consumer<Runnable>() {
      @Override
      public void consume(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
      }
    });
    createTableModel();
    myState = StepType.CONTINUE;

    initUiRefresh();
    myAuthorRenderer = new HighLightingRenderer(HIGHLIGHT_TEXT_ATTRIBUTES, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    myMyShowTreeAction = new MyShowTreeAction();
    myIsFilterByStarOn = false;
    myFilterStarredAction = new GitLogShowOnlyStarredCommitsAction(createOnOffForFilterStarred());
    myRefresh = new Runnable() {
      @Override
      public void run() {
        reloadRequest();
      }
    };
    myDetailsSplitter = new Splitter(true, 0.6f);
    myDetailsSplitter.setShowDividerControls(true);
  }

  private OnOff createOnOffForFilterStarred() {
    return new OnOff() {
      @Override
      public boolean isOn() {
        return myIsFilterByStarOn;
      }

      @Override
      public void on() {
        myIsFilterByStarOn = true;
        toggleOtherFiltersPresentation();
        reloadRequest();
      }

      @Override
      public void off() {
        myIsFilterByStarOn = false;
        toggleOtherFiltersPresentation();
        reloadRequest();
      }

      private void toggleOtherFiltersPresentation() {
        myDatesFilterAction.greyPanelFg(myIsFilterByStarOn);
        myStructureFilterAction.greyPanelFg(myIsFilterByStarOn);
        myBranchSelectorAction.greyPanelFg(myIsFilterByStarOn);
        myUsersFilterAction.greyPanelFg(myIsFilterByStarOn);
        myTextFieldAction.setTextFieldFg(myIsFilterByStarOn);
      }
    };
  }

  public void initFromSettings() {
    mySelectedBranch = mySettings.getSelectedBranch();
    myUserFilterI.myFilter = mySettings.getSelectedUser();
    myUserFilterI.myMeSelected = mySettings.isSelectedUserMe();
    myUsersFilterAction.setSelectedPresets(mySettings.getSelectedUser(), mySettings.isSelectedUserMe());
    myBranchSelectorAction.setPreset(mySelectedBranch);

    final List<String> selectedPaths = mySettings.getSelectedPaths();
    if (selectedPaths != null && ! selectedPaths.isEmpty()){
      final ArrayList<VirtualFile> paths = new ArrayList<VirtualFile>();
      final LocalFileSystem lfs = LocalFileSystem.getInstance();
      for (String path : selectedPaths) {
        final VirtualFile vf = lfs.refreshAndFindFileByIoFile(new File(path));
        if (vf == null) return; // do not keep filter if any file can not be found
        paths.add(vf);
      }
      myStructureFilter.myAllSelected = false;
      myStructureFilter.getSelected().addAll(paths);
    }
    myStructureFilterAction.setPreset();

    if (mySettings.getDateState().mySelectedTime) {
      myDatesFilterAction.setPreset(mySettings.getDateState());
    }
    final HashSet<VirtualFile> activeRoots = new HashSet<VirtualFile>();
    final Set<String> saved = mySettings.getActiveRoots();
    if (! saved.isEmpty()) {
      for (VirtualFile vf : myRootsUnderVcs) {
        if (saved.contains(vf.getPath())) {
          activeRoots.add(vf);
        }
      }
      if (! activeRoots.isEmpty()) {
        myTableModel.setActiveRoots(activeRoots);
      }
    }
  }

  private void setOrderText(boolean topoOrder) {
    myOrderLabel.setText(topoOrder ? "Topo Order" : "Date Order");
  }

  private void initUiRefresh() {
    myUIRefresh = new UIRefresh() {
      @Override
      public void detailsLoaded() {
        tryRefreshDetails();
        fireTableRepaint();
      }

      @Override
      public void linesReloaded(boolean drawMore) {
        if ((! StepType.STOP.equals(myState)) && (! StepType.FINISHED.equals(myState))) {
          myState = drawMore ? StepType.PAUSE : StepType.CONTINUE;
        }
        fireTableRepaint();
        updateMoreVisibility();
        orderLabelVisibility();
      }

      @Override
      public void acceptException(Exception e) {
        LOG.info(e);
        if (myVcs.getExecutableValidator().isExecutableValid()) {
          VcsBalloonProblemNotifier.showOverChangesView(myProject, e.getMessage(), MessageType.ERROR);
        }
      }

      @Override
      public void finished() {
        myState = StepType.FINISHED;
        updateMoreVisibility();
      }

      @Override
      public void reportStash(VirtualFile root, @Nullable Pair<AbstractHash, AbstractHash> hash) {
        myTableModel.stashFor(root, hash);
      }

      @Override
      public void reportSymbolicRefs(VirtualFile root, CachedRefs symbolicRefs) {
        myRefs.put(root, symbolicRefs);
        myTableModel.setHeadIfEmpty(root, symbolicRefs.getHeadHash());

        myRecalculatedCommon.clear();
        if (myRefs.isEmpty()) return;

        final CheckSamePattern<String> currentUser = new CheckSamePattern<String>();
        final CheckSamePattern<String> currentBranch = new CheckSamePattern<String>();
        for (CachedRefs refs : myRefs.values()) {
          myRecalculatedCommon.addLocals(refs.getLocalBranches());
          myRecalculatedCommon.addRemotes(refs.getRemoteBranches());
          final String currentFromRefs = refs.getCurrent() == null ? null : refs.getCurrent().getFullName();
          currentBranch.iterate(currentFromRefs);
          currentUser.iterate(refs.getUsername());
        }
        if (currentBranch.isSame()) {
          myRecalculatedCommon.setCurrent(myRefs.values().iterator().next().getCurrent());
        }
        if (currentUser.isSame()) {
          final String username = currentUser.getSameValue();
          myRecalculatedCommon.setUsername(username);
          myUserFilterI.setMe(username);
        }

        myBranchSelectorAction.setSymbolicRefs(myRecalculatedCommon);
        myBranchSelectorAction.asTextAction();
      }
    };
  }

  private void orderLabelVisibility() {
    myOrderLabel.setVisible(myGraphGutter.getComponent().getWidth() > 60);
  }

  private void fireTableRepaint() {
    final TableSelectionKeeper keeper = new TableSelectionKeeper(myJBTable, myTableModel);
    keeper.put();
    myDataBeingAdded = true;
    myTableModel.fireTableDataChanged();
    keeper.restore();
    myDataBeingAdded = false;
    myJBTable.revalidate();
    myJBTable.repaint();
    ((JComponent) myEqualToHeadr.getParent()).revalidate();
    myEqualToHeadr.getParent().repaint();
  }

  private void start() {
    myStarted = true;
    myMyChangeListener.start();
    myGraphGutter.setRowHeight(myJBTable.getRowHeight());
    myGraphGutter.start();
    rootsChanged(myRootsUnderVcs);
  }

  private static class TableSelectionKeeper {
    private final List<Pair<Integer, AbstractHash>> myData;
    private final JBTable myTable;
    private final BigTableTableModel myModel;
    private int[] mySelectedRows;

    private TableSelectionKeeper(final JBTable table, final BigTableTableModel model) {
      myTable = table;
      myModel = model;
      myData = new ArrayList<Pair<Integer,AbstractHash>>();
    }

    public void put() {
      mySelectedRows = myTable.getSelectedRows();
      for (int row : mySelectedRows) {
        final CommitI commitI = myModel.getCommitAt(row);
        if (commitI != null) {
          myData.add(new Pair<Integer, AbstractHash>(commitI.selectRepository(SelectorList.getInstance()), commitI.getHash()));
        }
      }
    }

    public void restore() {
      final int rowCount = myModel.getRowCount();
      final ListSelectionModel selectionModel = myTable.getSelectionModel();
      for (int row : mySelectedRows) {
        final CommitI commitI = myModel.getCommitAt(row);
        if (commitI != null) {
          final Pair<Integer, AbstractHash> pair =
            new Pair<Integer, AbstractHash>(commitI.selectRepository(SelectorList.getInstance()), commitI.getHash());
          if (myData.remove(pair)) {
            selectionModel.addSelectionInterval(row, row);
            if (myData.isEmpty()) return;
          }
        }
      }
      if (myData.isEmpty()) return;
      for (int i = 0; i < rowCount; i++) {
        final CommitI commitI = myModel.getCommitAt(i);
        if (commitI == null) continue;
        final Pair<Integer, AbstractHash> pair =
          new Pair<Integer, AbstractHash>(commitI.selectRepository(SelectorList.getInstance()), commitI.getHash());
        if (myData.remove(pair)) {
          selectionModel.addSelectionInterval(i, i);
          if (myData.isEmpty()) break;
        }
      }
    }
  }

  @Override
  public void dispose() {
  }

  public UIRefresh getUIRefresh() {
    return myUIRefresh;
  }

  public void createMe() {
    mySplitter = new Splitter(false, 0.7f);

    final JPanel wrapper = createMainTable();
    mySplitter.setFirstComponent(wrapper);

    final JComponent component = createRepositoryBrowserDetails();
    mySplitter.setSecondComponent(component);

    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        if (myMyChangeListener != null) {
          myMyChangeListener.stop();
        }
      }
    });

    createDetailLoaders();
  }

  private void createDetailLoaders() {
    myDetailsLoaderImpl = new Consumer<CommitI>() {
      @Override
      public void consume(final CommitI commitI) {
        if (commitI == null || commitI.holdsDecoration()) return;
        final GitHeavyCommit gitCommit = fullCommitPresentation(commitI);

        if (gitCommit == null) {
          final MultiMap<VirtualFile, AbstractHash> question = new MultiMap<VirtualFile, AbstractHash>();
          question.putValue(commitI.selectRepository(myRootsUnderVcs), commitI.getHash());
          myDetailsCache.acceptQuestion(question);
        } else {
          try {
            myDetailsLoader.take(commitI, gitCommit);
          }
          catch (Details.AlreadyDisposedException e) {
            //
          }
        }
      }
    };
    myDetailsLoader = new GenericDetailsLoader<CommitI, GitHeavyCommit>(myDetailsLoaderImpl, new PairConsumer<CommitI, GitHeavyCommit>() {
      @Override
      public void consume(CommitI commitI, GitHeavyCommit commit) {
        myDetailsPanel.setData(commitI.selectRepository(myRootsUnderVcs), commit);
      }
    });

    myBranchesLoaderImpl = new Consumer<CommitI>() {
      private Processor<AbstractHash> myRecheck;

      {
        myRecheck = new Processor<AbstractHash>() {
          @Override
          public boolean process(AbstractHash abstractHash) {
            if (myBranchesLoader.getCurrentlySelected() == null) return false;
            return Comparing.equal(myBranchesLoader.getCurrentlySelected().getHash(), abstractHash);
          }
        };
      }

      @Override
      public void consume(final CommitI commitI) {
        if (commitI == null) return;
        final VirtualFile root = commitI.selectRepository(myRootsUnderVcs);
        final List<String> branches = myDetailsCache.getBranches(root, commitI.getHash());
        if (branches != null) {
          try {
            myBranchesLoader.take(commitI, branches);
          }
          catch (Details.AlreadyDisposedException e) {
            //
          }
          return;
        }

        myDetailsCache.loadAndPutBranches(root, commitI.getHash(), new Consumer<List<String>>() {
          @Override
          public void consume(List<String> strings) {
            if (myProject.isDisposed() || strings == null) return;
            try {
              myBranchesLoader.take(commitI, strings);
            }
            catch (Details.AlreadyDisposedException e) {
              //
            }
          }
        }, myRecheck);
      }
    };
    myBranchesLoader = new GenericDetailsLoader<CommitI, List<String>>(myBranchesLoaderImpl, new PairConsumer<CommitI, List<String>>() {
      @Override
      public void consume(CommitI commitI, List<String> strings) {
        myDetailsPanel.setBranches(strings);
      }
    });
  }

  private JComponent createRepositoryBrowserDetails() {
    myRepositoryChangesBrowser = new RepositoryChangesBrowser(myProject, Collections.<CommittedChangeList>emptyList(), Collections.<Change>emptyList(), null);
    myRepositoryChangesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), myJBTable);
    myRepositoryChangesBrowser.getViewer().setScrollPaneBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.TOP));
    myJBTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        myGraphGutter.getComponent().repaint();
        mySelectionRequestsMerger.request();
      }
    });
    return myRepositoryChangesBrowser;
  }

  private void selectionChanged() {
    if (myDataBeingAdded) {
      mySelectionRequestsMerger.request();
      return;
    }
    final int[] rows = myJBTable.getSelectedRows();
    selectionChangedForDetails(rows);

    if (rows.length == 0) {
      myRepositoryChangesBrowser.getViewer().setEmptyText("Nothing selected");
      return;
    } else if (rows.length >= 10) {
      myRepositoryChangesBrowser.getViewer().setEmptyText("Too many rows selected");
      return;
    }
    if (! myDataBeingAdded && ! gatherNotLoadedData()) {
      myRepositoryChangesBrowser.getViewer().setEmptyText("Loading...");
    }
  }

  private static class MeaningfulSelection {
    private CommitI myCommitI;
    private int myMeaningfulRows;

    private MeaningfulSelection(int[] rows, final BigTableTableModel tableModel) {
      myMeaningfulRows = 0;
      for (int row : rows) {
        final CommitI commitAt = tableModel.getCommitAt(row);
        if (! commitAt.holdsDecoration()) {
          myCommitI = commitAt;
          ++ myMeaningfulRows;
          if (myMeaningfulRows > 1) break;
        }
      }
    }

    @Nullable
    public CommitI getCommit() {
      return myCommitI;
    }

    public int getMeaningfulRows() {
      return myMeaningfulRows;
    }
  }

  private void tryRefreshDetails() {
    MeaningfulSelection meaningfulSelection = new MeaningfulSelection(myJBTable.getSelectedRows(), myTableModel);
    if (meaningfulSelection.getMeaningfulRows() == 1) {
      // still have one item selected which probably was not loaded
      final CommitI commit = meaningfulSelection.getCommit();
      myDetailsLoaderImpl.consume(commit);
      myBranchesLoaderImpl.consume(commit);
    }
  }

  private void selectionChangedForDetails(int[] rows) {
    MeaningfulSelection meaningfulSelection = new MeaningfulSelection(rows, myTableModel);
    int meaningfulRows = meaningfulSelection.getMeaningfulRows();
    CommitI commitAt = meaningfulSelection.getCommit();

    if (meaningfulRows == 0) {
      myDetailsPanel.nothingSelected();
      myDetailsLoader.updateSelection(null, false);
      myBranchesLoader.updateSelection(null, false);
    } else if (meaningfulRows == 1) {
      final GitHeavyCommit commit = fullCommitPresentation(commitAt);
      if (commit == null) {
        myDetailsPanel.loading(commitAt.selectRepository(myRootsUnderVcs));
      }
      myDetailsLoader.updateSelection(commitAt, false);
      myBranchesLoader.updateSelection(commitAt, false);
    } else {
      myDetailsPanel.severalSelected();
      myDetailsLoader.updateSelection(null, false);
      myBranchesLoader.updateSelection(null, false);
    }
  }

  private GitHeavyCommit fullCommitPresentation(CommitI commitAt) {
    return myDetailsCache.convert(commitAt.selectRepository(myRootsUnderVcs), commitAt.getHash());
  }

  public void updateByScroll() {
    gatherNotLoadedData();
  }

  private boolean gatherNotLoadedData() {
    if (myDataBeingAdded) return false;
    final int[] rows = myJBTable.getSelectedRows();
    final List<GitHeavyCommit> commits = new ArrayList<GitHeavyCommit>();
    final List<CommitI> forComparison = new ArrayList<CommitI>();

    final MultiMap<VirtualFile,AbstractHash> missingHashes = new MultiMap<VirtualFile, AbstractHash>();
    for (int i = rows.length - 1; i >= 0; --i) {
      final int row = rows[i];
      final CommitI commitI = myTableModel.getCommitAt(row);
      if (commitI == null || commitI.holdsDecoration()) continue;
      final GitHeavyCommit details = fullCommitPresentation(commitI);
      if (details == null) {
        missingHashes.putValue(commitI.selectRepository(myRootsUnderVcs), commitI.getHash());
      } else if (missingHashes.isEmpty()) {   // no sense in collecting commits when s
        forComparison.add(commitI);
        commits.add(details);
      }
    }
    if (! missingHashes.isEmpty()) {
      myDetailsCache.acceptQuestion(missingHashes);
      return false;
    }
    if (Comparing.equal(myCommitsInRepositoryChangesBrowser, forComparison)) return true;
    myCommitsInRepositoryChangesBrowser.clear();
    myCommitsInRepositoryChangesBrowser.addAll(forComparison);

    final List<Change> changes = new ArrayList<Change>();
    for (GitHeavyCommit commit : commits) {
      changes.addAll(commit.getChanges());
    }
    final List<Change> zipped = CommittedChangesTreeBrowser.zipChanges(changes);
    myRepositoryChangesBrowser.setChangesToDisplay(zipped);
    return true;
  }

  private JPanel createMainTable() {
    myJBTable = new JBTable(myTableModel) {
      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        final TableCellRenderer custom = myTableModel.getColumnInfo(column).getRenderer(myTableModel.getValueAt(row, column));
        return custom == null ? super.getCellRenderer(row, column) : custom;
      }
    };

    if (UIUtil.isUnderDarcula()) {
      myJBTable.setStriped(true);
      myJBTable.setShowGrid(false);
    }
    final TableLinkMouseListener tableLinkListener = new TableLinkMouseListener() {
      @Override
      protected Object tryGetTag(MouseEvent e, JTable table, int row, int column) {
        myDescriptionRenderer.getTableCellRendererComponent(table, table.getValueAt(row, column), false, false, row, column);
        final Rectangle rc = table.getCellRect(row, column, false);
        int index = myDescriptionRenderer.myInner.findFragmentAt(e.getPoint().x - rc.x - myDescriptionRenderer.getCurrentWidth());
        if (index >= 0) {
          return myDescriptionRenderer.myInner.getFragmentTag(index);
        }
        return null;
      }
    };
    final ActionToolbar actionToolbar = createToolbar();
    tableLinkListener.installOn(myJBTable);
    myJBTable.getExpandableItemsHandler().setEnabled(false);
    myJBTable.setShowGrid(false);
    myJBTable.setModel(myTableModel);
    myJBTable.setBorder(null);
    final PopupHandler popupHandler = new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        createContextMenu();
        myContextMenu.getComponent().show(comp, x, y);
      }
    };
    myJBTable.addMouseListener(popupHandler);

    myTableScrollPane = ScrollPaneFactory.createScrollPane(myJBTable);
    myTableScrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.RIGHT | SideBorder.BOTTOM));
    myGraphGutter = new GraphGutter(myTableModel);
    myGraphGutter.setJBTable(myJBTable);
    myTableViewPort = myTableScrollPane.getViewport();
    myGraphGutter.setTableViewPort(myTableViewPort);
    myGraphGutter.getComponent().addMouseListener(popupHandler);

    new AdjustComponentWhenShown() {
      @Override
      protected boolean init() {
        return adjustColumnSizes(myTableScrollPane);
      }

      @Override
      protected boolean canExecute() {
        return myStarted;
      }
    }.install(myJBTable);

    myMyChangeListener = new GitTableScrollChangeListener(myJBTable, myDetailsCache, myTableModel, new Runnable() {
      @Override
      public void run() {
        updateByScroll();
      }
    }, new Runnable() {
      @Override
      public void run() {
        if (myGraphGutter.getComponent().isVisible()) {
          myGraphGutter.getComponent().repaint();
        }
      }
    }
    );
    myTableScrollPane.getViewport().addChangeListener(myMyChangeListener);

    final JPanel wrapper = new DataProviderPanel(new BorderLayout());
    wrapper.add(actionToolbar.getComponent(), BorderLayout.NORTH);
    final JPanel mainBorderWrapper = new JPanel(new BorderLayout());
    final JPanel wrapperGutter = new JPanel(new BorderLayout());
    //myGraphGutter.getComponent().setVisible(false);
    createTreeUpperComponent();
    wrapperGutter.add(myEqualToHeadr, BorderLayout.NORTH);
    wrapperGutter.add(myGraphGutter.getComponent(), BorderLayout.CENTER);
    mainBorderWrapper.add(wrapperGutter, BorderLayout.WEST);
    mainBorderWrapper.add(myTableScrollPane, BorderLayout.CENTER);
    //mainBorderWrapper.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
    wrapper.add(mainBorderWrapper, BorderLayout.CENTER);
    
    myDetailsPanel = new GitLogDetailsPanel(myProject, myDetailsCache, new Convertor<VirtualFile, CachedRefs>() {
      @Override
      public CachedRefs convert(VirtualFile o) {
        return myRefs.get(o);
      }
    }, new Processor<AbstractHash>() {
      @Override
      public boolean process(AbstractHash hash) {
        return myMarked.containsKey(hash);
      }
    });

    myDetailsSplitter.setFirstComponent(wrapper);
    JPanel details = myDetailsPanel.getComponent();
    details.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.RIGHT));
    setupDetailsSplitter(mySettings.isShowDetails());
    return myDetailsSplitter;
  }

  private void createTreeUpperComponent() {
    final MyTreeSettings treeSettings = new MyTreeSettings();
    myEqualToHeadr = new JPanel(new BorderLayout()) {
      @Override
      public Dimension getPreferredSize() {
        return getMySize();
      }

      @Override
      public Dimension getMaximumSize() {
        return getMySize();
      }

      @Override
      public Dimension getMinimumSize() {
        return getMySize();
      }

      public Dimension getMySize() {
        final int height = myJBTable.getTableHeader().getHeight();
        final int width = myGraphGutter.getComponent().getPreferredSize().width;
        return new Dimension(width, height);
      }
    };
    myEqualToHeadr.setBorder(BorderFactory.createMatteBorder(1,0,1,0, UIUtil.getBorderColor()));
    final JPanel wr2 = new JPanel(new BorderLayout());
    myOrderLabel = new JLabel();
    myOrderLabel.setForeground(UIUtil.getInactiveTextColor());
    myOrderLabel.setBorder(new EmptyBorder(0, 1, 0, 0));
    wr2.add(myOrderLabel, BorderLayout.WEST);
    wr2.add(treeSettings.getLabel(), BorderLayout.EAST);
    myEqualToHeadr.add(wr2, BorderLayout.CENTER);
    treeSettings.getLabel().setBorder(BorderFactory.createLineBorder(UIUtil.getLabelBackground()));
    final MouseAdapter mouseAdapter = new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        treeSettings.execute(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        treeSettings.getLabel().setBackground(UIUtil.getLabelBackground());
        treeSettings.getLabel().setBorder(BorderFactory.createLineBorder(UIUtil.getLabelBackground()));
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        treeSettings.getLabel().setBackground(UIUtil.getLabelBackground().darker());
        treeSettings.getLabel().setBorder(BorderFactory.createLineBorder(Color.black));
      }
    };
    myEqualToHeadr.addMouseListener(mouseAdapter);
    treeSettings.getLabel().addMouseListener(mouseAdapter);
  }

  private void createContextMenu() {
    if (myContextMenu == null) {
      final DefaultActionGroup group = new DefaultActionGroup();
      final Point location = MouseInfo.getPointerInfo().getLocation();
      SwingUtilities.convertPointFromScreen(location, myJBTable);
      final int row = myJBTable.rowAtPoint(location);
      if (row >= 0) {
        final GitHeavyCommit commit = getCommitAtRow(row);
        if (commit != null) {
          myUsersFilterAction.setPreselectedUser(commit.getCommitter());
        }
      }
      group.add(getCherryPickAction());
      group.add(new CreatePatchFromChangesAction() {
        @Override
        public void update(AnActionEvent e) {
          Change[] changes;
          e.getPresentation().setEnabled((changes = e.getData(VcsDataKeys.CHANGES)) != null && changes.length > 0);
        }
      });
      group.add(new MyCheckoutRevisionAction());
      group.add(new MyCheckoutNewBranchAction());
      group.add(new MyCreateNewTagAction());

      group.add(new Separator());
      group.add(myCopyHashAction);
      group.add(myMyShowTreeAction);
      group.add(myMyGotoCommitAction);
      final CustomShortcutSet shortcutSet =
        new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_G, KeyEvent.ALT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK));
      myMyGotoCommitAction.registerCustomShortcutSet(shortcutSet, myJBTable);
      myMyGotoCommitAction.registerCustomShortcutSet(shortcutSet, myGraphGutter.getComponent());
      final MyHighlightCurrent myHighlightCurrent = new MyHighlightCurrent();
      final CustomShortcutSet customShortcutSet = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_W, SystemInfo.isMac
                                                                                                  ? KeyEvent.META_DOWN_MASK
                                                                                                  : KeyEvent.CTRL_DOWN_MASK));
      myHighlightCurrent.registerCustomShortcutSet(customShortcutSet, myJBTable);
      myHighlightCurrent.registerCustomShortcutSet(customShortcutSet, myGraphGutter.getComponent());

      group.add(myHighlightCurrent);
      group.add(new MyHighlightActionGroup());
      final MyToggleCommitMark toggleCommitMark = new MyToggleCommitMark();
      toggleCommitMark.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), myJBTable);
      toggleCommitMark.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)),
                                                 myGraphGutter.getComponent());
      group.add(toggleCommitMark);
      group.add(new Separator());

      group.add(myBranchSelectorAction.asTextAction());
      group.add(myUsersFilterAction.asTextAction());
      group.add(myStructureFilterAction.asTextAction());
      group.add(myDatesFilterAction.asTextAction());
      group.add(new Separator());
      group.add(myRefreshAction);

      group.addAll(getCustomActions());

      myRefreshAction.registerShortcutOn(myJBTable);
      myRefreshAction.registerShortcutOn(myGraphGutter.getComponent());

      myContextMenu = ActionManager.getInstance().createActionPopupMenu(GIT_LOG_TABLE_PLACE, group);
    }
  }

  // TODO move all actions in this group, so that they are independent from this class, and would be accessible from anywhere
  @NotNull
  private static ActionGroup getCustomActions() {
    return (ActionGroup)ActionManager.getInstance().getAction("Git.LogContextMenu");
  }

  private ActionToolbar createToolbar() {
    final DefaultActionGroup group = new DefaultActionGroup();
    myBranchSelectorAction = new BranchSelectorAction(myProject, new Consumer<String>() {
      @Override
      public void consume(String s) {
        mySelectedBranch = s;
        reloadRequest();
      }
    });
    myUserFilterI = new MyFilterUi(myRefresh);
    myUsersFilterAction = new UsersFilterAction(myProject, myUserFilterI);
    myTextFieldAction = new MyTextFieldAction();
    group.add(myTextFieldAction);
    group.add(myBranchSelectorAction);
    group.add(myUsersFilterAction);
    Getter<List<VirtualFile>> rootsGetter = new Getter<List<VirtualFile>>() {
      @Override
      public List<VirtualFile> get() {
        return myRootsUnderVcs;
      }
    };
    myStructureFilter = new MyStructureFilter(myRefresh, rootsGetter);
    myStructureFilterAction = new StructureFilterAction(myProject, myStructureFilter);
    group.add(myStructureFilterAction);
    myDatesFilter = new DatesFilterI() {
      @Override
      public long getBefore() {
        return mySettings.getDateState().mySelectedTime ? mySettings.getDateState().myTimeBefore : -1;
      }

      @Override
      public long getAfter() {
        return mySettings.getDateState().mySelectedTime ? mySettings.getDateState().myTimeAfter : -1;
      }

      @Override
      public boolean isAll() {
        return ! mySettings.getDateState().mySelectedTime;
      }

      @Override
      public long getCommitTimeIfOne() {
        final CommitI commitAt = getCommitIfOneRealSelected();
        return commitAt == null ? -1 : commitAt.getTime();
      }

      @Override
      public void selectAll() {
        mySettings.getDateState().mySelectedTime = false;
        myRefresh.run();
      }

      @Override
      public void filter(long before, long after, String presetFilterName) {
        final GitLogSettings.MyDateState state = mySettings.getDateState();
        state.myTimeBefore = before;
        state.myTimeAfter = after;
        state.mySelectedTime = true;
        state.myPresetFilter = presetFilterName;
        myRefresh.run();
      }
    };
    myDatesFilterAction = new DatesFilterAction(myProject, myDatesFilter);
    group.add(myDatesFilterAction);
    group.add(myFilterStarredAction);
    group.add(getCherryPickAction());
    group.add(ActionManager.getInstance().getAction("ChangesView.CreatePatchFromChanges"));
    myRefreshAction = new MyRefreshAction();
    myRootsAction = new MyRootsAction(rootsGetter, myJBTable);
    group.add(myRootsAction);
    group.add(myMyShowTreeAction);

    group.add(new ToggleAction("Show Details", "Display details panel", AllIcons.Actions.Preview) {
        @Override
        public boolean isSelected(AnActionEvent e) {
          return !myProject.isDisposed() && mySettings.isShowDetails();
        }

        @Override
        public void setSelected(AnActionEvent e, boolean state) {
          setupDetailsSplitter(state);
          if (!myProject.isDisposed()) {
            mySettings.setShowDetails(state);
          }
        }
      });

    myMyGotoCommitAction = new MyGotoCommitAction();
    group.add(myMyGotoCommitAction);
    group.add(myRefreshAction);
    group.add(new ContextHelpAction("reference.changesToolWindow.log"));

    //group.add(new TestIndexAction());
    myMoreAction = new MoreAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myMediator.continueLoading();
        myState = StepType.CONTINUE;
        updateMoreVisibility();
      }
    };
    group.add(myMoreAction);
    // just created here
    myCopyHashAction = new AnAction("Copy Hash") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final int[] selectedRows = myJBTable.getSelectedRows();
        final StringBuilder sb = new StringBuilder();
        for (int row : selectedRows) {
          final CommitI commitAt = myTableModel.getCommitAt(row);
          if (commitAt == null) continue;
          if (sb.length() > 0) {
            sb.append(' ');
          }
          sb.append(commitAt.getHash().getString());
        }
        CopyPasteManager.getInstance().setContents(new StringSelection(sb.toString()));
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myJBTable.getSelectedRowCount() > 0);
      }
    };
    return ActionManager.getInstance().createActionToolbar("Git log", group, true);
  }

  private static AnAction getCherryPickAction() {
    return ActionManager.getInstance().getAction("Git.CherryPick");
  }

  private void setupDetailsSplitter(boolean state) {
    myDetailsSplitter.setSecondComponent(state ? myDetailsPanel.getComponent() : null);
    if (state) {
      myTableScrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.RIGHT | SideBorder.BOTTOM));
    }
    else {
      myTableScrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.RIGHT));
    }
  }

  private class DataProviderPanel extends JPanel implements TypeSafeDataProvider {

    private GitCommitDetailsProvider myCommitDetailsProvider;

    private DataProviderPanel(LayoutManager layout) {
      super(layout);
      myCommitDetailsProvider = new GitCommitDetailsProvider() {
        @NotNull
        @Override
        public List<String> getContainingBranches(@NotNull VirtualFile root, @NotNull AbstractHash commitHash) {
          List<String> branches = myDetailsCache.getBranches(root, commitHash);
          return branches == null ? Collections.<String>emptyList() : branches;
        }
      };
    }

    @Override
    public void calcData(DataKey key, DataSink sink) {
      if (VcsDataKeys.CHANGES.equals(key) || GitVcs.GIT_COMMIT.equals(key)) {
        final int[] rows = myJBTable.getSelectedRows();
        if (rows.length != 1) return;
        int row = rows[0];
        final GitHeavyCommit gitCommit = getCommitAtRow(row);
        if (gitCommit == null) return;

        if (VcsDataKeys.CHANGES.equals(key)) {
          final List<Change> changes = new ArrayList<Change>(gitCommit.getChanges());
          sink.put(key, changes.toArray(new Change[changes.size()]));
        }
        else if (GitVcs.GIT_COMMIT.equals(key)) {
          sink.put(key, gitCommit);
        }
      }
      else if (GitVcs.SELECTED_COMMITS.equals(key)) {
        sink.put(key, getSelectedCommits());
      }
      else if (GitVcs.COMMIT_DETAILS_PROVIDER.equals(key)) {
        sink.put(key, myCommitDetailsProvider);
      }
      else if (VcsDataKeys.PRESET_COMMIT_MESSAGE.equals(key)) {
        final int[] rows = myJBTable.getSelectedRows();
        if (rows.length != 1) return;
        final CommitI commitAt = myTableModel.getCommitAt(rows[0]);
        if (commitAt == null) return;
        final GitHeavyCommit gitCommit = fullCommitPresentation(commitAt);
        if (gitCommit == null) return;
        sink.put(key, gitCommit.getDescription());
      }
    }
  }

    /**
     * Returns the list of selected commits. The list is sorted so that newest commits go first (because they are above in the log).
     */
    @NotNull
    private List<GitHeavyCommit> getSelectedCommits() {
      if (myJBTable == null) {
        return Collections.emptyList();
      }

      final List<GitHeavyCommit> commits = new ArrayList<GitHeavyCommit>();
      for (int row : myJBTable.getSelectedRows()) {
        final CommitI commitI = myTableModel.getCommitAt(row);
        if (commitI == null) {
          return Collections.emptyList();
        }
        if (commitI.holdsDecoration()) {
          continue;
        }
        final VirtualFile root = commitI.selectRepository(myRootsUnderVcs);
        final GitHeavyCommit gitCommit = myDetailsCache.convert(root, commitI.getHash());
        if (gitCommit == null) {
          return Collections.emptyList();
        }
        commits.add(gitCommit);
      }
      return commits;
    }

  @Nullable
  private GitHeavyCommit getCommitAtRow(int row) {
    final CommitI commitAt = myTableModel.getCommitAt(row);
    if (commitAt == null) return null;
    final GitHeavyCommit gitCommit = fullCommitPresentation(commitAt);
    if (gitCommit == null) return null;
    return gitCommit;
  }

  private boolean adjustColumnSizes(JScrollPane scrollPane) {
    if (myJBTable.getWidth() <= 0) return false;
    createContextMenu();
    //myJBTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    final TableColumnModel columnModel = myJBTable.getColumnModel();
    final FontMetrics metrics = myJBTable.getFontMetrics(myJBTable.getFont());
    final int height = metrics.getHeight();
    myJBTable.setRowHeight((int) (height * 1.3) + 1);
    myGraphGutter.setRowHeight(myJBTable.getRowHeight());
    myGraphGutter.setHeaderHeight(myJBTable.getTableHeader().getHeight());
    final int dateWidth = metrics.stringWidth("Yesterday 00:00:00  " + scrollPane.getVerticalScrollBar().getWidth()) + columnModel.getColumnMargin();
    final int nameWidth = metrics.stringWidth("Somelong W. UsernameToDisplay");
    int widthWas = 0;
    for (int i = 0; i < columnModel.getColumnCount(); i++) {
      widthWas += columnModel.getColumn(i).getWidth();
    }

    columnModel.getColumn(1).setWidth(nameWidth);
    columnModel.getColumn(1).setPreferredWidth(nameWidth);

    columnModel.getColumn(2).setWidth(dateWidth);
    columnModel.getColumn(2).setPreferredWidth(dateWidth);

    final int nullWidth = widthWas - dateWidth - nameWidth - columnModel.getColumnMargin() * 3;
    columnModel.getColumn(0).setWidth(nullWidth);
    columnModel.getColumn(0).setPreferredWidth(nullWidth);

    return true;
  }

  private static class CommentSearchContext {
    private final List<HighlightingRendererBase> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
    private final List<String> mySearchContext;

    private CommentSearchContext() {
      mySearchContext = new ArrayList<String>();
    }

    public void addHighlighter(final HighlightingRendererBase renderer) {
      myListeners.add(renderer);
    }

    public void clear() {
      mySearchContext.clear();
      for (HighlightingRendererBase listener : myListeners) {
        listener.setSearchContext(Collections.<String>emptyList());
      }
    }

    public String preparse(String previousFilter) {
      final String[] strings = previousFilter.split("[\\s]");
      StringBuilder sb = new StringBuilder();
      mySearchContext.clear();
      for (String string : strings) {
        if (string.trim().length() == 0) continue;
        mySearchContext.add(string.toLowerCase());
        final String word = StringUtil.escapeToRegexp(string);
        sb.append(word).append(".*");
      }
      new SubstringsFilter().doFilter(mySearchContext);
      for (HighlightingRendererBase listener : myListeners) {
        listener.setSearchContext(mySearchContext);
      }
      return sb.toString();
    }
  }

  public static class SubstringsFilter extends AbstractFilterChildren<String> {
    @Override
    protected boolean isAncestor(String parent, String child) {
      return parent.startsWith(child);
    }

    @Override
    protected void sortAscending(List<String> strings) {
      Collections.sort(strings, new ComparableComparator.Descending<String>());
    }
  }

  public JComponent getPanel() {
    return mySplitter;
  }

  public void rootsChanged(List<VirtualFile> rootsUnderVcs) {
    final RootsHolder wasRootsHolder = myTableModel.getRootsHolder();
    final HashSet<VirtualFile> activeRoots = new HashSet<VirtualFile>(rootsUnderVcs);

    if (wasRootsHolder != null) {
      final HashSet<VirtualFile> excludedRoots = new HashSet<VirtualFile>(wasRootsHolder.getRoots());
      excludedRoots.removeAll(myTableModel.getActiveRoots());
      activeRoots.removeAll(excludedRoots);
    }

    myRootsUnderVcs = rootsUnderVcs;
    final RootsHolder rootsHolder = new RootsHolder(rootsUnderVcs);
    myTableModel.setRootsHolder(rootsHolder);
    myTableModel.setActiveRoots(activeRoots);
    myDetailsCache.rootsChanged(rootsUnderVcs);
    
    if (myStarted) {
      reloadRequest();
    }
  }

  public UIRefresh getRefreshObject() {
    return myUIRefresh;
  }

  public BigTableTableModel getTableModel() {
    return myTableModel;
  }

  private void createTableModel() {
    myTableModel = new BigTableTableModel(columns(), new Runnable() {
      @Override
      public void run() {
        start();
      }
    });
  }

  List<ColumnInfo> columns() {
    initAuthor();
    return Arrays.asList((ColumnInfo)COMMENT, AUTHOR, DATE);
  }

  private final ColumnInfo<Object, Object> COMMENT = new ColumnInfo<Object, Object>("Comment") {
    private final TableCellRenderer mySimpleRenderer = new SimpleRenderer(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true);

    @Override
    public Object valueOf(Object o) {
      if (o instanceof GitHeavyCommit) {
        return o;
      }
      if (BigTableTableModel.LOADING == o) return o;
      return o == null ? "" : o.toString();
    }

    @Override
    public TableCellRenderer getRenderer(Object o) {
      return o instanceof GitHeavyCommit ? myDescriptionRenderer : mySimpleRenderer;
    }
  };

  private class HighLightingRenderer extends ColoredTableCellRenderer {
    private final SimpleTextAttributes myHighlightAttributes;
    private final SimpleTextAttributes myUsualAttributes;
    private SimpleTextAttributes myUsualAttributesForRun;
    protected final HighlightingRendererBase myWorker;

    public HighLightingRenderer(SimpleTextAttributes highlightAttributes, SimpleTextAttributes usualAttributes) {
      myHighlightAttributes = highlightAttributes;
      myUsualAttributes = usualAttributes == null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : usualAttributes;
      myUsualAttributesForRun = myUsualAttributes;
      myWorker = new HighlightingRendererBase() {
        @Override
        protected void usual(String s) {
          append(s, myUsualAttributesForRun);
        }

        @Override
        protected void highlight(String s) {
          append(s, SimpleTextAttributes.merge(myUsualAttributesForRun, myHighlightAttributes));
        }
      };
    }

    public HighlightingRendererBase getWorker() {
      return myWorker;
    }

    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      setBackground(getRowBg(row));
      //setBackground(getLogicBackground(selected, row));
      if (BigTableTableModel.LOADING == value) {
        return;
      }
      final String text = value.toString();
      myUsualAttributesForRun = isCurrentUser(row, text) ?
                                SimpleTextAttributes.merge(myUsualAttributes, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES) :
                                new SimpleTextAttributes(myUsualAttributes.getBgColor(), myUsualAttributes.getFgColor(),
                                                         myUsualAttributes.getWaveColor(), myUsualAttributes.getStyle());
      if (myWorker.isEmpty()) {
        append(text, myUsualAttributesForRun);
        return;
      }
      myWorker.tryHighlight(text);
    }

    private boolean isCurrentUser(final int row, final String text) {
      final CommitI commitAt = myTableModel.getCommitAt(row);
      if (commitAt == null) return false;
      final SymbolicRefsI symbolicRefs = myRefs.get(commitAt.selectRepository(myRootsUnderVcs));
      if (symbolicRefs == null) return false;
      return Comparing.equal(symbolicRefs.getUsername(), text);
    }
  }

  private class SimpleRenderer extends ColoredTableCellRenderer {
    private final SimpleTextAttributes myAtt;
    private final boolean myShowLoading;

    public SimpleRenderer(SimpleTextAttributes att, boolean showLoading) {
      myAtt = att;
      myShowLoading = showLoading;
    }

    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      setBackground(getRowBg(row));
      if (BigTableTableModel.LOADING == value) {
        if (myShowLoading) {
          append("Loading...");
        }
        return;
      }
      append(value.toString(), myAtt);
    }
  }

  private class DescriptionRenderer implements TableCellRenderer {
    private final Map<String, Icon> myTagMap;
    private final Map<String, Icon> myBranchMap;
    private final JPanel myPanel;
    private final Inner myInner;
    private int myCurrentWidth;

    private DescriptionRenderer() {
      myInner = new Inner();
      myTagMap = new HashMap<String, Icon>();
      myBranchMap = new HashMap<String, Icon>();
      myPanel = new JPanel();
      myPanel.setBackground(UIUtil.getTableBackground());
      final BoxLayout layout = new BoxLayout(myPanel, BoxLayout.X_AXIS);
      myPanel.setLayout(layout);
      myCurrentWidth = 0;
    }

    public void resetIcons() {
      myBranchMap.clear();
      myTagMap.clear();
    }

    public int getCurrentWidth() {
      return myCurrentWidth;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      myCurrentWidth = 0;
      final Color bg = isSelected ? UIUtil.getTableSelectionBackground() : getRowBg(row);
      if (value instanceof GitHeavyCommit) {
        myPanel.removeAll();
        myPanel.setBackground(bg);
        final GitHeavyCommit commit = (GitHeavyCommit)value;

        final boolean marked = myMarked.containsKey(commit.getShortHash());
        final int localSize = commit.getLocalBranches() == null ? 0 : commit.getLocalBranches().size();
        final int remoteSize = commit.getRemoteBranches() == null ? 0 : commit.getRemoteBranches().size();
        final int tagsSize = commit.getTags().size();

        if (marked) {
          myPanel.add(new JLabel(Git4ideaIcons.Star));
          myCurrentWidth += Git4ideaIcons.Star.getIconWidth();
        }
        if (localSize + remoteSize > 0) {
          final CommitI commitI = myTableModel.getCommitAt(row);
          final List<Trinity<String, Boolean, Color>> display = getBranchesToDisplay(commit, commitI);
          boolean containsHead = commit.getTags().contains("HEAD");

          final boolean plus = localSize + remoteSize + tagsSize > (display.size() + (containsHead ? 1 : 0));
          for (int i = 0; i < display.size(); i++) {
            final Trinity<String, Boolean, Color> trinity = display.get(i);
            boolean withContionuation = containsHead ? false : (plus && (i == display.size() - 1));
            String key = trinity.getFirst() + (withContionuation ? "@" : "");
            Icon icon = myBranchMap.get(key);
            if (icon == null) {
              icon = new CaptionIcon(trinity.getThird(), table.getFont().deriveFont((float) table.getFont().getSize() - 1),
                                     trinity.getFirst(), table, CaptionIcon.Form.SQUARE,
                                     withContionuation, trinity.getSecond());
              myBranchMap.put(key, icon);
            }
            addOneIcon(table, value, isSelected, hasFocus, row, column, icon);
          }
          if (tagsSize > 0 && containsHead) {
            addTagIcon(table, value, isSelected, hasFocus, row, column, "HEAD", plus);
          }

          myInner.setBackground(bg);
          return myPanel;
        }
        if ((localSize + remoteSize == 0) && (tagsSize > 0)) {
          final String tag = commit.getTags().get(0);
          addTagIcon(table, value, isSelected, hasFocus, row, column, tag, tagsSize > 1);
          myInner.setBackground(bg);
          return myPanel;
        }
        if (marked) {
          myInner.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          myPanel.add(myInner);
          myInner.setBackground(bg);
          return myPanel;
        }
      }
      myInner.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      myInner.setBackground(bg);
      return myInner;
    }

    private void addTagIcon(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column, String tag, final boolean plus) {
      String key = tag + (plus ? "@" : "");
      Icon icon = myTagMap.get(key);
      if (icon == null) {
        icon = new CaptionIcon(Colors.tag, table.getFont().deriveFont((float) table.getFont().getSize() - 1),
                               tag, table, CaptionIcon.Form.ROUNDED, plus, false);
        myTagMap.put(key, icon);
      }
      addOneIcon(table, value, isSelected, hasFocus, row, column, icon);
    }

    private List<Trinity<String, Boolean, Color>> getBranchesToDisplay(final GitHeavyCommit commit, final CommitI commitI) {
      final List<Trinity<String, Boolean, Color>> result = new ArrayList<Trinity<String, Boolean, Color>>();

      final List<String> localBranches = commit.getLocalBranches();
      final SymbolicRefsI symbolicRefs = myRefs.get(commitI.selectRepository(myRootsUnderVcs));
      final String currentName = symbolicRefs.getCurrentName();
      final String trackedRemoteName = symbolicRefs.getTrackedRemoteName();
      
      if (currentName != null && localBranches.contains(currentName)) {
        result.add(new Trinity<String, Boolean, Color>(currentName, true, Colors.local));
      }
      final List<String> remoteBranches = commit.getRemoteBranches();
      if (trackedRemoteName != null && remoteBranches.contains(trackedRemoteName)) {
        result.add(new Trinity<String, Boolean, Color>(trackedRemoteName, true, Colors.remote));
      }
      if (result.isEmpty()) {
        boolean remote = localBranches.isEmpty();
        result.add(new Trinity<String, Boolean, Color>(remote ? (remoteBranches.get(0)) : localBranches.get(0), false,
                                                       remote ? Colors.remote : Colors.local));
      }
      return result;
    }

    private void addOneIcon(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column, Icon icon) {
      myCurrentWidth += icon.getIconWidth();
      //myPanel.removeAll();
      //myPanel.setBackground(getLogicBackground(isSelected, row));
      myPanel.add(new JLabel(icon));
      myInner.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      myPanel.add(myInner);
    }

    private class Inner extends HighLightingRenderer {
      private final IssueLinkRenderer myIssueLinkRenderer;
      private final Consumer<String> myConsumer;

      private Inner() {
        super(HIGHLIGHT_TEXT_ATTRIBUTES, null);
        myIssueLinkRenderer = new IssueLinkRenderer(myProject, this);
        myConsumer = new Consumer<String>() {
          @Override
          public void consume(String s) {
            myWorker.tryHighlight(s);
          }
        };
      }
      @Override
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        //setBackground(getLogicBackground(selected, row));
        if (value instanceof GitHeavyCommit) {
          final GitHeavyCommit gitCommit = (GitHeavyCommit)value;
          myIssueLinkRenderer.appendTextWithLinks(gitCommit.getSubject(), SimpleTextAttributes.REGULAR_ATTRIBUTES, myConsumer);
          //super.customizeCellRenderer(table, ((GitCommit) value).getDescription(), selected, hasFocus, row, column);
        } else {
          super.customizeCellRenderer(table, value, selected, hasFocus, row, column);
        }
      }
    }
  }

  private Color getRowBg(int row) {
    final int[] selectedRows = myJBTable.getSelectedRows();
    if (selectedRows != null && selectedRows.length > 0) {
      for (int selectedRow : selectedRows) {
        if (selectedRow == row) {
          return UIUtil.getTableSelectionBackground();
        }
      }
    }
    if (myClearedHighlightingRoots.isEmpty()) {
      return myTableModel.isInCurrentBranch(row) ? Colors.highlighted : UIUtil.isUnderDarcula() ? UIUtil.getTableBackground().darker() : UIUtil.getTableBackground();
    }
    final CommitI commitAt = myTableModel.getCommitAt(row);
    if (commitAt.holdsDecoration()) {
      return UIUtil.getTableBackground();
    }
    final VirtualFile virtualFile = commitAt.selectRepository(myRootsUnderVcs);
    return myClearedHighlightingRoots.contains(virtualFile) ? UIUtil.getTableBackground() :
           (myTableModel.isInCurrentBranch(row) ? Colors.highlighted : UIUtil.getTableBackground());
  }

  //private Color getLogicBackground(final boolean isSelected, final int row) {
  //  Color bkgColor;
  //  final CommitI commitAt = myTableModel.getCommitAt(row);
  //  GitCommit gitCommit = null;
  //  if (commitAt != null && (! commitAt.holdsDecoration())) {
  //    gitCommit = fullCommitPresentation(commitAt);
  //  }
  //
  //  if (isSelected) {
  //    bkgColor = UIUtil.getTableSelectionBackground();
  //  } else {
  //    bkgColor = UIUtil.getTableBackground();
  //    if (gitCommit != null) {
  //      if (myDetailsCache.getStashName(commitAt.selectRepository(myRootsUnderVcs), gitCommit.getShortHash()) != null) {
  //        bkgColor = Colors.stashed;
  //      } else if (gitCommit.isOnLocal() && gitCommit.isOnTracked()) {
  //        bkgColor = Colors.commonThisBranch;
  //      } else if (gitCommit.isOnLocal()) {
  //        bkgColor = Colors.ownThisBranch;
  //      }
  //    }
  //  }
  //  return bkgColor;
  //}

  private ColumnInfo<Object, String> AUTHOR;

  private void initAuthor() {
    AUTHOR = new ColumnInfo<Object, String>("Author") {
      @Override
      public String valueOf(Object o) {
        if (o instanceof GitHeavyCommit) {
          return ((GitHeavyCommit)o).getAuthor();
        }
        return "";
      }

      @Override
      public TableCellRenderer getRenderer(Object o) {
        return myAuthorRenderer;
      }
    };
  }

  private final ColumnInfo<Object, String> DATE = new ColumnInfo<Object, String>("Date") {
    private final TableCellRenderer myRenderer = new SimpleRenderer(SimpleTextAttributes.REGULAR_ATTRIBUTES, false);

    @Override
    public String valueOf(Object o) {
      if (o instanceof GitHeavyCommit) {
        return DateFormatUtil.formatPrettyDateTime(((GitHeavyCommit)o).getDate());
      }
      return "";
    }

    @Override
    public TableCellRenderer getRenderer(Object o) {
      return myRenderer;
    }
  };

  public void setDetailsCache(DetailsCache detailsCache) {
    myDetailsCache = detailsCache;
  }

  // todo test action
  private class TestIndexAction extends DumbAwareAction {
    private TestIndexAction() {
      super("Test Index", "Test Index", PlatformIcons.CHECK_ICON);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myTableModel.printNavigation();
      //final BigTableTableModel.WiresGroupIterator iterator = myTableModel.getGroupIterator(myTableModel.getRowCount() - 1);
    }
  }

  private class MyRefreshAction extends RefreshAction {
    private MyRefreshAction() {
      super("Refresh", "Refresh", AllIcons.Actions.Refresh);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      rootsChanged(myRootsUnderVcs);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(true);
    }
  }

  private class MyTextFieldAction extends SearchFieldAction {
    private MyTextFieldAction() {
      super("Filter:");

    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      checkIfFilterChanged();
    }

    private void checkIfFilterChanged() {
      final String newValue = getText().trim();
      if (! Comparing.equal(myPreviousFilter, newValue)) {
        myPreviousFilter = newValue;

        reloadRequest();
      }
    }
  }

  private void reloadRequest() {
    // store state
    mySettings.setSelectedBranch(mySelectedBranch);
    mySettings.setSelectedUser(myUserFilterI.myFilter);
    mySettings.setSelectedUserIsMe(myUserFilterI.isMeSelected());
    mySettings.setSelectedPaths(myStructureFilter.myAllSelected ? null : myStructureFilter.getSelected());

    myState = StepType.CONTINUE;
    final int was = myTableModel.getRowCount();
    myDetailsCache.resetAsideCaches();
    final Collection<String> startingPoints = mySelectedBranch == null ? Collections.<String>emptyList() : Collections.singletonList(mySelectedBranch);
    final GitLogSettings.MyDateState dateState = mySettings.getDateState();
    Set<ChangesFilter.Filter> dateFilters = null;
    if (dateState.mySelectedTime) {
      dateFilters = applyDateFilter();
    }
    myDescriptionRenderer.resetIcons();
    final boolean commentFilterEmpty = StringUtil.isEmptyOrSpaces(myPreviousFilter);
    myCommentSearchContext.clear();
    myUsersSearchContext.clear();

    final boolean haveFilters = (! (commentFilterEmpty && (myUserFilterI.myFilter == null) && myStructureFilter.myAllSelected &&
                                (! mySettings.getDateState().mySelectedTime))) || myIsFilterByStarOn;
    //myThereIsDisordering = (! (commentFilterEmpty && (myUserFilterI.myFilter == null) && myStructureFilter.myAllSelected)) || myIsFilterByStarOn;
    myThereIsDisordering = haveFilters;
    final boolean topoOrder = (!myThereIsDisordering) && myRootsUnderVcs.size() == 1 ? mySettings.isTopoOrder() : false;
    myOrderLabel.setVisible(false);
    setOrderText(topoOrder);
    if (topoOrder) {
      myTableModel.useNoGroupingStrategy();
    } else {
      myTableModel.useDateGroupingStrategy();
    }

    myEqualToHeadr.getParent().setVisible(! myThereIsDisordering && myMyShowTreeAction.isSelected(null));
    if (! haveFilters) {
      /*if (myMyShowTreeAction.isSelected(null)) {
        myEqualToHeadr.getParent().setVisible(true);
      }*/
      myUsersSearchContext.clear();
      myMediator.reload(new RootsHolder(myRootsUnderVcs), startingPoints, null, new GitLogFilters(), topoOrder);
    } else if (myIsFilterByStarOn) {
      myUsersSearchContext.clear();
      myMediator.reloadSetFixed(myMarked, new RootsHolder(myRootsUnderVcs));
    } else {
      ChangesFilter.Comment comment = null;
      if (! commentFilterEmpty) {
        final String commentFilter = myCommentSearchContext.preparse(myPreviousFilter);
        comment = new ChangesFilter.Comment(commentFilter);
      }
      Set<ChangesFilter.Filter> userFilters = null;
      if (myUserFilterI.myFilter != null) {
        userFilters = applyUserFilter();
      }
      Map<VirtualFile, ChangesFilter.Filter> structureFilters = null;
      if (! myStructureFilter.myAllSelected) {
        structureFilters = applyStructureFilter();
      }

      final List<String> possibleReferencies = commentFilterEmpty ? null : Arrays.asList(myPreviousFilter.split("[\\s]"));
      myMediator.reload(new RootsHolder(myRootsUnderVcs), startingPoints, null, new GitLogFilters(comment, userFilters, dateFilters,
        structureFilters, possibleReferencies), topoOrder);
    }
    myCommentSearchContext.addHighlighter(myDetailsPanel.getHtmlHighlighter());
    updateMoreVisibility();
    mySelectionRequestsMerger.request();
    fireTableRepaint();
    myTableModel.fireTableRowsDeleted(0, was);
    myGraphGutter.getComponent().revalidate();
    myGraphGutter.getComponent().repaint();
  }

  private Set<ChangesFilter.Filter> applyDateFilter() {
    final Set<ChangesFilter.Filter> result = new HashSet<ChangesFilter.Filter>();
    final long timeBefore = mySettings.getDateState().myTimeBefore;
    if (timeBefore > 0) {
      result.add(new ChangesFilter.BeforeTime(timeBefore));
    }
    final long timeAfter = mySettings.getDateState().myTimeAfter;
    if (timeAfter > 0) {
      result.add(new ChangesFilter.AfterTime(timeAfter));
    }
    return result;
  }

  private Map<VirtualFile, ChangesFilter.Filter> applyStructureFilter() {
    Map<VirtualFile, ChangesFilter.Filter> structureFilters;
    structureFilters = new HashMap<VirtualFile, ChangesFilter.Filter>();
    final Collection<VirtualFile> selected = new ArrayList<VirtualFile>(myStructureFilter.getSelected());
    final ArrayList<VirtualFile> copy = new ArrayList<VirtualFile>(myRootsUnderVcs);
    Collections.sort(copy, FilePathComparator.getInstance());
    Collections.reverse(copy);
    for (VirtualFile root : copy) {
      final Collection<VirtualFile> selectedForRoot = new SmartList<VirtualFile>();
      final Iterator<VirtualFile> iterator = selected.iterator();
      while (iterator.hasNext()) {
        VirtualFile next = iterator.next();
        if (VfsUtil.isAncestor(root, next, false)) {
          selectedForRoot.add(next);
          iterator.remove();
        }
      }
      if (! selectedForRoot.isEmpty()) {
        final ChangesFilter.StructureFilter structureFilter = new ChangesFilter.StructureFilter();
        structureFilter.addFiles(selectedForRoot);
        structureFilters.put(root, structureFilter);
      }
    }
    return structureFilters;
  }

  private Set<ChangesFilter.Filter> applyUserFilter() {
    Set<ChangesFilter.Filter> userFilters;
    final String[] strings = myUserFilterI.myFilter.split(",");
    userFilters = new HashSet<ChangesFilter.Filter>();
    for (String string : strings) {
      string = string.trim();
      if (string.length() == 0) continue;
      myUsersSearchContext.add(string.toLowerCase());
      final String regexp = StringUtil.escapeToRegexp(string);
      userFilters.add(new ChangesFilter.Committer(regexp));
      userFilters.add(new ChangesFilter.Author(regexp));
    }
    return userFilters;
  }

  interface Colors {
    Color tag = new JBColor(new Color(0xf1ef9e), new Color(113, 111, 64));
    Color remote = new JBColor(new Color(0xbcbcfc), new Color(0xbcbcfc).darker().darker());
    Color local = new JBColor(new Color(0x75eec7), new Color(0x0D6D4F));
    Color highlighted = new JBColor(new Color(210,255,233), UIUtil.getTableBackground());
  }

  private void updateMoreVisibility() {
    if (StepType.PAUSE.equals(myState)) {
      myMoreAction.setEnabled(true);
      myMoreAction.setVisible(true);
    } else if (StepType.CONTINUE.equals(myState)) {
      myMoreAction.setVisible(true);
      myMoreAction.setEnabled(false);
    } else {
      myMoreAction.setVisible(false);
    }
  }

  private class MyFilterUi implements UserFilterI {
    private boolean myMeIsKnown;
    private String myMe;
    private String myFilter;
    private boolean myMeSelected;
    private final Runnable myReloadCallback;

    public MyFilterUi(Runnable reloadCallback) {
      myReloadCallback = reloadCallback;
    }

    @Override
    public void allSelected() {
      myFilter = null;
      myMeSelected = false;
      myReloadCallback.run();
    }

    @Override
    public void meSelected() {
      myFilter = myMe;
      myMeSelected = true;
      myReloadCallback.run();
    }

    @Override
    public void filter(String s) {
      myMeSelected = false;
      myFilter = s;
      myReloadCallback.run();
    }

    @Override
    public boolean isMeKnown() {
      return myMeIsKnown;
    }

    public boolean isMeSelected() {
      return myMeSelected;
    }

    @Override
    public String getMe() {
      return myMe;
    }

    @Override
    public String getUserIfOne() {
      final int[] selectedRows = myJBTable.getSelectedRows();
      if (selectedRows != null && selectedRows.length > 0) {
        for (int row : selectedRows) {
          final CommitI commitAt = myTableModel.getCommitAt(row);
          if (commitAt.holdsDecoration()) continue;
          final GitHeavyCommit atRow = getCommitAtRow(row);
          if (atRow != null && atRow.getCommitter() != null) return atRow.getCommitter();
        }
      }
      return null;
    }

    public void setMe(final String me) {
      myMeIsKnown = ! StringUtil.isEmptyOrSpaces(me);
      myMe = me == null ? "" : me.trim();
    }
  }

  private static class MyStructureFilter implements StructureFilterI {
    private boolean myAllSelected;
    private final List<VirtualFile> myFiles;
    private final Runnable myReloadCallback;
    private final Getter<List<VirtualFile>> myGetter;

    private MyStructureFilter(Runnable reloadCallback, final Getter<List<VirtualFile>> getter) {
      myReloadCallback = reloadCallback;
      myGetter = getter;
      myFiles = new ArrayList<VirtualFile>();
      myAllSelected = true;
    }

    @Override
    public void allSelected() {
      if (myAllSelected) return;
      myAllSelected = true;
      myReloadCallback.run();
    }

    @Override
    public void select(Collection<VirtualFile> files) {
      myAllSelected = false;
      if (Comparing.haveEqualElements(files, myFiles)) return;
      myFiles.clear();
      myFiles.addAll(files);
      myReloadCallback.run();
    }

    @Override
    public Collection<VirtualFile> getSelected() {
      return myFiles;
    }

    @Override
    public List<VirtualFile> getRoots() {
      return myGetter.get();
    }

    public boolean isAllSelected() {
      return myAllSelected;
    }
  }

  public void setProjectScope(boolean projectScope) {
    myProjectScope = projectScope;
    myRootsAction.setEnabled(! projectScope);
  }

  private static class MyRootsAction extends AnAction {
    private boolean myEnabled;
    private final Getter<List<VirtualFile>> myRootsGetter;
    private final JComponent myComponent;

    private MyRootsAction(final Getter<List<VirtualFile>> rootsGetter, final JComponent component) {
      super("Show roots", "Show roots", AllIcons.General.BalloonInformation);
      myRootsGetter = rootsGetter;
      myComponent = component;
      myEnabled = false;
    }

    public void setEnabled(boolean enabled) {
      myEnabled = enabled;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myEnabled);
      e.getPresentation().setVisible(myEnabled);
      super.update(e);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      List<VirtualFile> virtualFiles = myRootsGetter.get();
      assert virtualFiles != null && virtualFiles.size() > 0;
      SortedListModel sortedListModel = new SortedListModel(null);
      final JBList jbList = new JBList(sortedListModel);
      sortedListModel.add("Roots:");
      for (VirtualFile virtualFile : virtualFiles) {
        sortedListModel.add(virtualFile.getPath());
      }

      JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(jbList, jbList).setRequestFocus(true).createPopup();
      if (e.getInputEvent() instanceof MouseEvent) {
        popup.show(new RelativePoint((MouseEvent)e.getInputEvent()));
      } else {
        popup.showInBestPositionFor(e.getDataContext());
      }
    }
  }

  public class MyShowTreeAction extends ToggleAction implements DumbAware {
    private static final String SHOW_GRAPH_TITLE = "Show Graph";
    private static final String HIDE_GRAPH_TITLE = "Hide Graph";
    private static final String SHOW_GRAPH_DESCRIPTION = "Display commit graph";
    private static final String HIDE_GRAPH_DESCRIPTION = "Hide commit graph";

    private boolean myIsSelected;

    public MyShowTreeAction() {
      super(SHOW_GRAPH_TITLE, SHOW_GRAPH_DESCRIPTION, Git4ideaIcons.Branch);
      myIsSelected = mySettings.isShowTree();
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(!myThereIsDisordering);
      e.getPresentation().setText(isSelected(e) ? HIDE_GRAPH_TITLE : SHOW_GRAPH_TITLE);
      e.getPresentation().setDescription(isSelected(e) ? HIDE_GRAPH_DESCRIPTION : SHOW_GRAPH_DESCRIPTION);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myIsSelected;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myIsSelected = state;
      mySettings.setShowTree(state);
      if (!myThereIsDisordering) {
        myEqualToHeadr.getParent().setVisible(state);
      }
    }
  }
  
  public class MyTreeSettings {
    private final DumbAwareAction myMultiColorAction;
    private final DumbAwareAction myCalmAction;
    private final Icon myIcon;
    private JLabel myLabel;
    private final GitLogUI.MySelectRootsForTreeAction myRootsForTreeAction;
    private final DumbAwareAction myDateOrder;
    private final DumbAwareAction myTopoOrder;
    private final Icon myMarkIcon;

    public MyTreeSettings() {
      myIcon = AllIcons.General.ComboArrow;
      myMarkIcon = PlatformIcons.CHECK_ICON;

      myMultiColorAction = new DumbAwareAction("Multicolour") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          myGraphGutter.setStyle(GraphGutter.PresentationStyle.multicolour);
        }

        @Override
        public void update(AnActionEvent e) {
          super.update(e);
          e.getPresentation().setIcon(GraphGutter.PresentationStyle.multicolour.equals(myGraphGutter.getStyle()) ? AllIcons.General.Mdot : AllIcons.General.Mdot_empty);
        }
      };
      myCalmAction = new DumbAwareAction("Two Colors") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          myGraphGutter.setStyle(GraphGutter.PresentationStyle.calm);
        }
        @Override
        public void update(AnActionEvent e) {
          super.update(e);
          e.getPresentation().setIcon(GraphGutter.PresentationStyle.multicolour.equals(myGraphGutter.getStyle()) ? AllIcons.General.Mdot_empty
                                                                                                                 : AllIcons.General.Mdot);
        }
      };

      myDateOrder = new DumbAwareAction("Date Order") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          if (! mySettings.isTopoOrder()) return;
          mySettings.setTopoOrder(false);
          reloadRequest();
        }

        @Override
        public void update(AnActionEvent e) {
          super.update(e);
          e.getPresentation().setIcon(mySettings.isTopoOrder() ? AllIcons.General.Mdot_empty : AllIcons.General.Mdot);
        }
      };
      myTopoOrder = new DumbAwareAction("Topo Order") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          if (mySettings.isTopoOrder()) return;
          mySettings.setTopoOrder(true);
          reloadRequest();
        }

        @Override
        public void update(AnActionEvent e) {
          super.update(e);
          e.getPresentation().setIcon(mySettings.isTopoOrder() ? AllIcons.General.Mdot : AllIcons.General.Mdot_empty);
        }
      };

      myRootsForTreeAction = new MySelectRootsForTreeAction();
      myLabel = new JLabel(myIcon);
      myLabel.setOpaque(false);
    }

    public JLabel getLabel() {
      return myLabel;
    }

    public Icon getIcon() {
      return myIcon;
    }

    public void execute(final MouseEvent e) {
      final DefaultActionGroup group = createActionGroup();
      final DataContext parent = DataManager.getInstance().getDataContext(myEqualToHeadr);
      final DataContext dataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT.getName(), myProject, parent);
      final JBPopup popup = JBPopupFactory.getInstance()
        .createActionGroupPopup(null, group, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
                                new Runnable() {
                                  @Override
                                  public void run() {
                                  }
                                }, 20);
      popup.show(new RelativePoint(e));
    }

    private DefaultActionGroup createActionGroup() {
      final DefaultActionGroup dab = new DefaultActionGroup();
      if (myRootsUnderVcs.size() == 1) {
        dab.add(myDateOrder);
        dab.add(myTopoOrder);
        dab.add(new Separator());
      }
      dab.add(myMultiColorAction);
      dab.add(myCalmAction);
      dab.add(new Separator());
      dab.add(myRootsForTreeAction);
      return dab;
    }
  }

  public class MySelectRootsForTreeAction extends DumbAwareAction {
    public MySelectRootsForTreeAction() {
      super("Repositories...");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final CheckBoxList checkBoxList = new CheckBoxList();

      final List<VirtualFile> order = myTableModel.getOrder();
      final Set<VirtualFile> activeRoots = myTableModel.getActiveRoots();
      
      final TreeMap<String, Boolean> map = new TreeMap<String, Boolean>();
      for (VirtualFile virtualFile : order) {
        map.put(virtualFile.getPath(), activeRoots.contains(virtualFile));
      }
      checkBoxList.setStringItems(map);

      final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(checkBoxList, checkBoxList).
        setRequestFocus(true).
        addListener(new JBPopupListener() {
          @Override
          public void beforeShown(LightweightWindowEvent event) {
            checkBoxList.setSelectedIndex(0);
            IdeFocusManager.getInstance(myProject).requestFocus(checkBoxList, true);
          }

          @Override
          public void onClosed(LightweightWindowEvent event) {
            if (event.isOk()) {
              final Set<String> paths =
                new HashSet<String>(ContainedInBranchesConfigDialog.gatherSelected((DefaultListModel)checkBoxList.getModel()));
              if (paths.isEmpty()) {
                myMyShowTreeAction.setSelected(null, false);
                return;
              }
              final HashSet<VirtualFile> set = new HashSet<VirtualFile>(order);
              final Iterator<VirtualFile> iterator = set.iterator();
              while (iterator.hasNext()) {
                VirtualFile file = iterator.next();
                if (!paths.contains(file.getPath())) {
                  iterator.remove();
                }
              }

              if (myProjectScope) {
                mySettings.setActiveRoots(paths);
              }

              myTableModel.setActiveRoots(set);
              myGraphGutter.getComponent().revalidate();
              myGraphGutter.getComponent().repaint();
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  orderLabelVisibility();
                }
              });
            }
          }
        }).setTitle("Show graph for:").
        setAdText("Press Enter to complete").
        createPopup();

      final AnAction ok = new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          popup.closeOk(e.getInputEvent());
        }
      };
      ok.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, checkBoxList);
      ok.registerCustomShortcutSet(CommonShortcuts.ENTER, checkBoxList);

      if (e != null && e.getInputEvent() instanceof MouseEvent) {
        popup.show(new RelativePoint((MouseEvent)e.getInputEvent()));
      } else {
        final Dimension dimension = popup.getContent().getPreferredSize();
        final Point at = new Point(20,0);
        popup.show(new RelativePoint(myEqualToHeadr, at));
      }
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      final boolean enabled = myRootsUnderVcs.size() > 1;
      e.getPresentation().setEnabled(enabled);
      e.getPresentation().setVisible(enabled);
    }
  }

  public class MyHighlightCurrent extends DumbAwareAction {
    public MyHighlightCurrent() {
      super("Highlight subgraph");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final CommitI commitAt = getCommitIfOneRealSelected();
      if (commitAt == null) {
        return;
      }
      final VirtualFile root = commitAt.selectRepository(myRootsUnderVcs);
      myTableModel.setHead(root, commitAt.getHash());
      myClearedHighlightingRoots.remove(root);
      myJBTable.repaint();
    }

    @Override
    public void update(AnActionEvent e) {
      if (myThereIsDisordering) {
        e.getPresentation().setEnabled(false);
        return;
      }
      weNeedOneCommitSelected(e);
    }
  }

  private CommitI getCommitIfOneRealSelected() {
    final int[] selectedRows = myJBTable.getSelectedRows();
    if (selectedRows.length != 1) {
      return null;
    }
    final CommitI commitAt = myTableModel.getCommitAt(selectedRows[0]);
    if (commitAt.holdsDecoration()) {
      return null;
    }
    return commitAt;
  }

  private void weNeedOneCommitSelected(AnActionEvent e) {
    final int[] selectedRows = myJBTable.getSelectedRows();
    if (selectedRows.length != 1) {
      e.getPresentation().setEnabled(false);
      return;
    }
    final CommitI commitAt = myTableModel.getCommitAt(selectedRows[0]);
    if (commitAt.holdsDecoration()) {
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setEnabled(true);
  }

  public class MyHighlightActionGroup extends ActionGroup {
    private final DumbAwareAction myAllHeads;
    private final DumbAwareAction myClearAll;
    private final DumbAwareAction myHead;
    //private final DumbAwareAction myClear;
//    private final DumbAwareAction myCurrent;
    private final AnAction[] myAnActions;

    public MyHighlightActionGroup() {
      super("Highlight...", true);

    myAllHeads = new DumbAwareAction("All HEADs subgraphs") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        for (VirtualFile root : myTableModel.getActiveRoots()) {
          final SymbolicRefsI symbolicRefs = myRefs.get(root);
          if (symbolicRefs == null) continue;
          final AbstractHash headHash = symbolicRefs.getHeadHash();
          if (headHash == null) continue;
          myTableModel.setHead(root, headHash);
          myClearedHighlightingRoots.removeAll(myRootsUnderVcs);
        }
        myJBTable.repaint();
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        if (myThereIsDisordering) {
          e.getPresentation().setEnabled(false);
          return;
        }
        e.getPresentation().setVisible(myTableModel.getActiveRoots().size() > 1);
      }
    };
    myClearAll = new DumbAwareAction("Clear") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        for (VirtualFile root : myTableModel.getActiveRoots()) {
          myTableModel.setDumbHighlighter(root);
        }
        myClearedHighlightingRoots.addAll(myRootsUnderVcs);
        myJBTable.repaint();
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        if (myThereIsDisordering) {
          e.getPresentation().setEnabled(false);
          return;
        }
        //e.getPresentation().setVisible(myTableModel.getActiveRoots().size() > 1);
      }
    };
    myHead = new DumbAwareAction("HEAD subgraph") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final int[] selectedRows = myJBTable.getSelectedRows();
        if (selectedRows.length != 1) {
          return;
        }
        final CommitI commitAt = myTableModel.getCommitAt(selectedRows[0]);
        if (commitAt.holdsDecoration()) {
          return;
        }
        final VirtualFile root = commitAt.selectRepository(myRootsUnderVcs);
        final SymbolicRefsI symbolicRefs = myRefs.get(root);
        if (symbolicRefs == null) return;
        final AbstractHash headHash = symbolicRefs.getHeadHash();
        if (headHash == null) return;
        myTableModel.setHead(root, headHash);
        myClearedHighlightingRoots.remove(root);
        myJBTable.repaint();
      }

      @Override
      public void update(AnActionEvent e) {
        if (myThereIsDisordering) {
          e.getPresentation().setEnabled(false);
          return;
        }
        weNeedOneCommitSelected(e);
      }
    };
    /*myClear = new DumbAwareAction("Clear") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final int[] selectedRows = myJBTable.getSelectedRows();
        if (selectedRows.length != 1) {
          return;
        }
        final CommitI commitAt = myTableModel.getCommitAt(selectedRows[0]);
        if (commitAt.holdsDecoration()) {
          return;
        }
        final VirtualFile root = commitAt.selectRepository(myRootsUnderVcs);
        myTableModel.setDumbHighlighter(root);
        myClearedHighlightingRoots.add(root);
        myJBTable.repaint();
      }

      @Override
      public void update(AnActionEvent e) {
        if (myThereAreFilters) {
          e.getPresentation().setEnabled(false);
          return;
        }
        weNeedOneCommitSelected(e);
      }
    };*/
    /*myCurrent = new DumbAwareAction("Current") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final int[] selectedRows = myJBTable.getSelectedRows();
        if (selectedRows.length != 1) {
          return;
        }
        final CommitI commitAt = myTableModel.getCommitAt(selectedRows[0]);
        if (commitAt.holdsDecoration()) {
          return;
        }
        final VirtualFile root = commitAt.selectRepository(myRootsUnderVcs);
        myTableModel.setHead(root, commitAt.getHash());
      }

      @Override
      public void update(AnActionEvent e) {
        weNeedOneCommitSelected(e);
      }
    };*/

      myAnActions = new AnAction[]{myHead, myAllHeads, myClearAll};
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(!myThereIsDisordering);
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return myAnActions;
    }
  }

  public class MyToggleCommitMark extends DumbAwareAction {
    public MyToggleCommitMark() {
      super("Mark", "Mark", Git4ideaIcons.Star);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final Action action = checkSelection();
      if (Action.disabled.equals(action)) return;
      final int[] selectedRows = myJBTable.getSelectedRows();
      if (Action.unselect.equals(action)) {
        for (int selectedRow : selectedRows) {
          final CommitI commitAt = myTableModel.getCommitAt(selectedRow);
          if (commitAt.holdsDecoration()) continue;
          myMarked.remove(commitAt.getHash());
        }
      }
      if (Action.select.equals(action)) {
        for (int selectedRow : selectedRows) {
          final CommitI commitAt = myTableModel.getCommitAt(selectedRow);
          if (commitAt.holdsDecoration()) continue;
          myMarked.put(commitAt.getHash(), commitAt.getTime());
        }
      }
      myJBTable.repaint();
      myGraphGutter.getComponent().repaint();
      myDetailsPanel.redrawBranchLabels();
      myDetailsPanel.getComponent().revalidate();
      myDetailsPanel.getComponent().repaint();
    }

    @Override
    public void update(AnActionEvent e) {
      final Action action = checkSelection();
      e.getPresentation().setEnabled(! Action.disabled.equals(action));
      e.getPresentation().setVisible(! Action.disabled.equals(action));

      e.getPresentation().setText(Action.select.equals(action) ? "Mark" : "Clear mark");
    }

    private Action checkSelection() {
      final int[] selectedRows = myJBTable.getSelectedRows();
      if (selectedRows.length == 0) return Action.disabled;
      boolean haveSelected = false;
      boolean haveUnSelected = false;
      for (int selectedRow : selectedRows) {
        final CommitI commitAt = myTableModel.getCommitAt(selectedRow);
        if (commitAt.holdsDecoration()) continue;
        if (myMarked.containsKey(commitAt.getHash())) {
          haveSelected = true;
        } else {
          haveUnSelected = true;
        }
      }
      if (! haveSelected && ! haveUnSelected) return Action.disabled;
      if (haveSelected) return Action.unselect;
      return Action.select;
    }
  }

  private static enum Action {
    disabled,
    select,
    unselect
  }
  
  private class MyCheckoutRevisionAction extends DumbAwareAction {
    private MyCheckoutRevisionAction() {
      super("Checkout Revision");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final int[] selectedRows = myJBTable.getSelectedRows();
      if (selectedRows.length != 1) return;
      final CommitI commitAt = myTableModel.getCommitAt(selectedRows[0]);
      if (commitAt.holdsDecoration() || myTableModel.isStashed(commitAt)) return;

      final GitRepository repository =
        GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(commitAt.selectRepository(myRootsUnderVcs));
      if (repository == null) return;
      GitBrancher brancher = ServiceManager.getService(myProject, GitBrancher.class);
      brancher.checkout(commitAt.getHash().getString(),
                        Collections.singletonList(repository), myRefresh);
    }

    @Override
    public void update(AnActionEvent e) {
      commitCanBeUsedForCheckout(e);
    }
  }

  private class MyCheckoutNewBranchAction extends DumbAwareAction {
    private MyCheckoutNewBranchAction() {
      super("New Branch", "Create new branch starting from the selected commit", IconUtil.getAddIcon());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final int[] selectedRows = myJBTable.getSelectedRows();
      if (selectedRows.length != 1) return;
      final CommitI commitAt = myTableModel.getCommitAt(selectedRows[0]);
      if (commitAt.holdsDecoration() || myTableModel.isStashed(commitAt)) return;

      final GitRepository repository =
        GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(commitAt.selectRepository(myRootsUnderVcs));
      if (repository == null) return;

      String reference = commitAt.getHash().getString();
      final String name = GitBranchUtil
        .getNewBranchNameFromUser(myProject, Collections.singleton(repository), "Checkout New Branch From " + reference);
      if (name != null) {
        GitBrancher brancher = ServiceManager.getService(myProject, GitBrancher.class);
        brancher.checkoutNewBranchStartingFrom(name, reference, Collections.singletonList(repository), myRefresh);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      commitCanBeUsedForCheckout(e);
    }
  }

  private void commitCanBeUsedForCheckout(AnActionEvent e) {
    final int[] selectedRows = myJBTable.getSelectedRows();
    if (selectedRows.length != 1) {
      e.getPresentation().setEnabled(false);
      return;
    }
    final CommitI commitAt = myTableModel.getCommitAt(selectedRows[0]);
    boolean enabled = ! commitAt.holdsDecoration() && ! myTableModel.isStashed(commitAt);
    e.getPresentation().setEnabled(enabled);
  }

  private class MyCreateNewTagAction extends DumbAwareAction {
    private MyCreateNewTagAction() {
      super("New Tag");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final int[] selectedRows = myJBTable.getSelectedRows();
      if (selectedRows.length != 1) return;
      final CommitI commitAt = myTableModel.getCommitAt(selectedRows[0]);
      if (commitAt.holdsDecoration() || myTableModel.isStashed(commitAt)) return;

      final GitRepository repository =
        GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(commitAt.selectRepository(myRootsUnderVcs));
      if (repository == null) return;
      new GitCreateNewTag(myProject, repository, commitAt.getHash().getString(), myRefresh).execute();
    }

    @Override
    public void update(AnActionEvent e) {
      commitCanBeUsedForCheckout(e);
    }
  }

  public void selectCommit(String commitId) {
    myJBTable.getSelectionModel().clearSelection();
    myMyGotoCommitAction.tryFind(commitId);
  }

  public class MyGotoCommitAction extends DumbAwareAction {
    public MyGotoCommitAction() {
      super("Find Commit", "Find commit by hash, reference or description fragment (in loaded part)", AllIcons.Actions.Menu_find);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      final JTextField field = new JTextField(30);

      final String[] gotoString = new String[1];
      final JBPopup[] popup = new JBPopup[1];
      field.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (KeyEvent.VK_ENTER == e.getKeyCode()) {
            gotoString[0] = field.getText();
            if (gotoString[0] != null) {
              tryFind(gotoString[0]);
            }
            if (popup[0] != null) {
              popup[0].cancel();
            }
          }
        }
      });
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(field, BorderLayout.CENTER);
      final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, field);
      popup[0] = builder.setTitle("Goto")
        .setResizable(true)
        .setFocusable(true)
        .setRequestFocus(true)
        .setMovable(true)
        .setModalContext(true)
        .setAdText("Commit hash, or reference, or regexp for commit message")
        .setDimensionServiceKey(myProject, "Git.Log.Tree.Goto", true)
        .setCancelOnClickOutside(true)
        .addListener(new JBPopupListener() {
          public void beforeShown(LightweightWindowEvent event) {
            IdeFocusManager.findInstanceByContext(e.getDataContext()).requestFocus(field, true);
          }

          public void onClosed(LightweightWindowEvent event) {
          }
        })
        .createPopup();
      UIUtil.installPopupMenuColorAndFonts(popup[0].getContent());
      UIUtil.installPopupMenuBorder(popup[0].getContent());
      popup[0].showInBestPositionFor(e.getDataContext());
    }

    private void tryFind(String reference) {
      reference = reference.trim();
      final int idx = getIdx(reference);
      if (idx == -1) {
        VcsBalloonProblemNotifier.showOverChangesView(myProject, "Nothing found for: \"" + reference + "\"", MessageType.WARNING);
      } else {
        myJBTable.getSelectionModel().addSelectionInterval(idx, idx);
        final int scrollOffsetTop = myJBTable.getRowHeight() * idx - myTableViewPort.getHeight()/2;
        if (scrollOffsetTop > 0) {
          myTableViewPort.setViewPosition(new Point(0, scrollOffsetTop));
        }
      }
    }
    
    private int getIdx(String reference) {
      if (! StringUtil.containsWhitespaces(reference)) {
        final int commitByIteration = findCommitByIteration(reference);
        if (commitByIteration != -1) return commitByIteration;
      }
      for (VirtualFile root : myRootsUnderVcs) {
        final SHAHash shaHash = GitChangeUtils.commitExists(myProject, root, reference, null);
        if (shaHash != null) {
          final int commitByIteration = findCommitByIteration(shaHash.getValue());
          if (commitByIteration != -1) return commitByIteration;
        }
      }
      final Set<AbstractHash> hashes = new HashSet<AbstractHash>();
      for (VirtualFile root : myRootsUnderVcs) {
        final List<AbstractHash> abstractHashs = GitChangeUtils.commitExistsByComment(myProject, root, reference);
        if (abstractHashs != null) {
          hashes.addAll(abstractHashs);
        }
      }
      if (! hashes.isEmpty()) {
        final int commitByIteration = findCommitByIteration(hashes);
        if (commitByIteration != -1) return commitByIteration;
      }
      return -1;
    }

    private int findCommitByIteration(Set<AbstractHash> references) {
      for (int i = 0; i < myTableModel.getRowCount(); i++) {
        final CommitI commitAt = myTableModel.getCommitAt(i);
        if (commitAt.holdsDecoration()) continue;
        if (references.contains(commitAt.getHash())) {
          return i;
        }
      }
      return -1;
    }
    
    private int findCommitByIteration(String reference) {
      for (int i = 0; i < myTableModel.getRowCount(); i++) {
        final CommitI commitAt = myTableModel.getCommitAt(i);
        if (commitAt.holdsDecoration()) continue;
        final String string = commitAt.getHash().getString();
        if (string.startsWith(reference) || reference.startsWith(string)) {
          return i;
        }
      }
      return -1;
    }
  }
}
