/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractFilterChildren;
import com.intellij.openapi.vcs.CheckSamePattern;
import com.intellij.openapi.vcs.ComparableComparator;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vcs.ui.SearchFieldAction;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.AdjustComponentWhenShown;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import git4idea.GitVcs;
import git4idea.history.browser.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
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
  private BigTableTableModel myTableModel;
  private DetailsCache myDetailsCache;
  private final Mediator myMediator;
  private Splitter mySplitter;
  private GitTableScrollChangeListener myMyChangeListener;
  private List<VirtualFile> myRootsUnderVcs;
  private final Map<VirtualFile, SymbolicRefs> myRefs;
  private final SymbolicRefs myRecalculatedCommon;
  private UIRefresh myUIRefresh;
  private JBTable myJBTable;
  private RepositoryChangesBrowser myRepositoryChangesBrowser;
  private boolean myDataBeingAdded;
  private boolean myMissingSelectionData;
  private CardLayout myRepoLayout;
  private JPanel myRepoPanel;
  private boolean myStarted;
//  private JTextField myFilterField;
  private String myPreviousFilter;
  private List<String> mySearchContext;
  private List<String> myUsersSearchContext;
  private String mySelectedBranch;
  private BranchSelectorAction myBranchSelectorAction;
  private MySpecificDetails myDetails;
  private final DescriptionRenderer myDescriptionRenderer;
  private final Speedometer mySelectionSpeedometer;

  private StepType myState;
  private MoreAction myMoreAction;
  private UsersFilterAction myUsersFilterAction;
  private MyFilterUi myUserFilterI;
  private MyCherryPick myCherryPickAction;
  private MyRefreshAction myRefreshAction;
  private AnAction myCopyHashAction;

  public GitLogUI(Project project, final Mediator mediator) {
    myProject = project;
    myMediator = mediator;
    mySearchContext = new ArrayList<String>();
    myUsersSearchContext = new ArrayList<String>();
    myRefs = new HashMap<VirtualFile, SymbolicRefs>();
    myRecalculatedCommon = new SymbolicRefs();
    myPreviousFilter = "";
    myDetails = new MySpecificDetails(myProject);
    myDescriptionRenderer = new DescriptionRenderer();
    mySelectionSpeedometer = new Speedometer(20, 400);
    createTableModel();
    myState = StepType.CONTINUE;

    myUIRefresh = new UIRefresh() {
      @Override
      public void detailsLoaded() {
        fireTableRepaint();
      }

      @Override
      public void linesReloaded(boolean drawMore) {
        if ((! StepType.STOP.equals(myState)) && (! StepType.FINISHED.equals(myState))) {
          myState = drawMore ? StepType.PAUSE : StepType.CONTINUE;
        }
        fireTableRepaint();
        updateMoreVisibility();
      }

      @Override
      public void acceptException(Exception e) {
        LOG.info(e);
      }

      @Override
      public void finished() {
        myState = StepType.FINISHED;
        updateMoreVisibility();
      }

      @Override
      public void reportSymbolicRefs(VirtualFile root, SymbolicRefs symbolicRefs) {
        myRefs.put(root, symbolicRefs);

        myRecalculatedCommon.clear();
        if (myRefs.isEmpty()) return;

        final CheckSamePattern<String> currentUser = new CheckSamePattern<String>();
        final CheckSamePattern<String> currentBranch = new CheckSamePattern<String>();
        for (SymbolicRefs refs : myRefs.values()) {
          myRecalculatedCommon.addLocals(refs.getLocalBranches());
          myRecalculatedCommon.addRemotes(refs.getRemoteBranches());
          myRecalculatedCommon.addTags(refs.getTags());
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
      }
    };
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
  }

  private void start() {
    myStarted = true;
    myMyChangeListener.start();
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
    mySplitter.setDividerWidth(4);

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

    /*final JComponent specificDetails = myDetails.create();
    final Content specificDetailsContent = myUi.createContent("Specific0", specificDetails, "Details", Icons.UNSELECT_ALL_ICON, null);
    myUi.addContent(specificDetailsContent, 0, PlaceInGrid.bottom, false);
    repoContent.setCloseable(false);
    repoContent.setPinned(true);

    myUi.getDefaults().initTabDefaults(0, "Git log", null);*/

    // todo should look like it, but the behaviour of search differs
    /*new TableSpeedSearch(myJBTable, new Convertor<Object, String>() {
      @Override
      public String convert(Object o) {
        if (o instanceof CommitI) {
          return ((CommitI) o).getDecorationString();
        }
        return o == null ? null : o.toString();
      }
    });*/

    //myUi.getDefaults().initTabDefaults(0, "Git log", );
    /*    myUi = RunnerLayoutUi.Factory.getInstance(project).create("Debug", "unknown!", sessionName, this);
    myUi.getDefaults().initTabDefaults(0, "Debug", null);

    myUi.getOptions().setTopToolbar(createTopToolbar(), ActionPlaces.DEBUGGER_TOOLBAR);
*/
  }

  private JComponent createRepositoryBrowserDetails() {
    myRepoLayout = new CardLayout();
    myRepoPanel = new JPanel(myRepoLayout);
    myRepositoryChangesBrowser = new RepositoryChangesBrowser(myProject, Collections.<CommittedChangeList>emptyList(), Collections.<Change>emptyList(), null);
    myRepositoryChangesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), myJBTable);
    myJBTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (! myDataBeingAdded && (! e.getValueIsAdjusting())) {
          selectionChanged();
        }
      }
    });
    myRepoPanel.add("main", myRepositoryChangesBrowser);
    // todo loading circle
    myRepoPanel.add("loading", panelWithCenteredText("Loading..."));
    myRepoPanel.add("tooMuch", panelWithCenteredText("Too many rows selected"));
    myRepoPanel.add("empty", panelWithCenteredText("Nothing selected"));
    myRepoLayout.show(myRepoPanel, "empty");
    return myRepoPanel;
  }

  private void selectionChanged() {
    mySelectionSpeedometer.event();

    final int[] rows = myJBTable.getSelectedRows();
    myDetails.refresh(null, null);
    if (rows.length == 0) {
      myRepoLayout.show(myRepoPanel, "empty");
      myRepoPanel.repaint();
      myMissingSelectionData = false;
      return;
    } else if (rows.length >= 10) {
      myRepoLayout.show(myRepoPanel, "tooMuch");
      myRepoPanel.repaint();
      myMissingSelectionData = false;
      return;
    }
    myRepoLayout.show(myRepoPanel, "loading");
    myRepoPanel.repaint();
    updateDetailsFromSelection();
  }

  private static JPanel panelWithCenteredText(final String text) {
    final JPanel jPanel = new JPanel(new BorderLayout());
    jPanel.setBackground(UIUtil.getTableBackground());
    final JLabel label = new JLabel(text, JLabel.CENTER);
    label.setUI(new MultiLineLabelUI());
    jPanel.add(label, BorderLayout.CENTER);
    jPanel.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
    return jPanel;
  }

  public void updateSelection() {
    updateBranchesFor();
    if (! myMissingSelectionData) return;
    updateDetailsFromSelection();
  }

  private void updateBranchesFor() {
    final int[] rows = myJBTable.getSelectedRows();
    if (rows.length == 1 && myDetails.isMissingBranchesInfo() && mySelectionSpeedometer.getSpeed() < 0.1) {
      final CommitI commit = myTableModel.getCommitAt(rows[0]);
      final VirtualFile root = commit.selectRepository(myRootsUnderVcs);
      if (commit.holdsDecoration()) return;
      final GitCommit gitCommit = myDetailsCache.convert(root, commit.getHash());
      if (gitCommit == null) return;
      final List<String> branches = myDetailsCache.getBranches(root, commit.getHash());
      if (branches != null) {
        myDetails.putBranches(root, gitCommit, branches);
      }

      final CommitI commitI = myTableModel.getCommitAt(rows[0]);
      if (! commit.equals(commitI)) return;
      myDetailsCache.loadAndPutBranches(root, gitCommit.getHash(), commit.getHash(), new Consumer<List<String>>() {
        @Override
        public void consume(List<String> strings) {
          if (myProject.isDisposed()) return;

          final int[] afterRows = myJBTable.getSelectedRows();
          if (myDetails.isMissingBranchesInfo() && afterRows.length == 1 && afterRows[0] == rows[0]) {
            final CommitI afterCommit = myTableModel.getCommitAt(rows[0]);
            if (afterCommit.holdsDecoration() || (! afterCommit.equals(commit))) return;
            myDetails.putBranches(root, gitCommit, strings);
          }
        }
      });
    }
  }

  private void updateDetailsFromSelection() {
    if (myDataBeingAdded) return;
    myMissingSelectionData = false;
    final int[] rows = myJBTable.getSelectedRows();
    if (rows.length == 1) {
      final CommitI commitI = myTableModel.getCommitAt(rows[0]);
      if (commitI != null && (! commitI.holdsDecoration())) {
        final VirtualFile root = commitI.selectRepository(myRootsUnderVcs);
        final GitCommit convert = myDetailsCache.convert(root, commitI.getHash());
        if (convert != null) {
          myDetails.refresh(root, convert);
        }
      }
    }
    final List<GitCommit> commits = new ArrayList<GitCommit>();
    final MultiMap<VirtualFile,AbstractHash> missingHashes = new MultiMap<VirtualFile, AbstractHash>();
    for (int i = rows.length - 1; i >= 0; --i) {
      final int row = rows[i];
      final CommitI commitI = myTableModel.getCommitAt(row);
      if (commitI == null || commitI.holdsDecoration()) continue;
      VirtualFile root = commitI.selectRepository(myRootsUnderVcs);
      AbstractHash hash = commitI.getHash();
      final GitCommit details = myDetailsCache.convert(root, hash);
      commits.add(details);
      myMissingSelectionData |= details == null;
      missingHashes.putValue(root, hash);
    }
    if (myMissingSelectionData) {
      myDetailsCache.acceptQuestion(missingHashes);
      return;
    }
    final List<Change> changes = new ArrayList<Change>();
    for (GitCommit commit : commits) {
      changes.addAll(commit.getChanges());
    }
    final List<Change> zipped = CommittedChangesTreeBrowser.zipChanges(changes);
    myRepositoryChangesBrowser.setChangesToDisplay(zipped);
    myRepoLayout.show(myRepoPanel, "main");
    myRepoPanel.repaint();
  }

  private JPanel createMainTable() {
    final ActionToolbar actionToolbar = createToolbar();

    myJBTable = new JBTable(myTableModel) {
      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        final TableCellRenderer custom = myTableModel.getColumnInfo(column).getRenderer(myTableModel.getValueAt(row, column));
        return custom == null ? super.getCellRenderer(row, column) : custom;
      }
    };
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
    tableLinkListener.install(myJBTable);
    myJBTable.getExpandableItemsHandler().setEnabled(false);
    myJBTable.setShowGrid(false);
    myJBTable.setModel(myTableModel);
    myJBTable.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        createContextMenu().getComponent().show(comp,x,y);
      }
    });

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myJBTable);

    new AdjustComponentWhenShown() {
      @Override
      protected boolean init() {
        return adjustColumnSizes(scrollPane);
      }

      @Override
      protected boolean canExecute() {
        return myStarted;
      }
    }.install(myJBTable);

    myMyChangeListener = new GitTableScrollChangeListener(myJBTable, myDetailsCache, myTableModel, new Runnable() {
      @Override
      public void run() {
        updateSelection();
      }
    });
    scrollPane.getViewport().addChangeListener(myMyChangeListener);

    final JPanel wrapper = new DataProviderPanel(new BorderLayout());
    wrapper.add(actionToolbar.getComponent(), BorderLayout.NORTH);
    final JPanel mainBorderWrapper = new JPanel(new BorderLayout());
    mainBorderWrapper.add(scrollPane, BorderLayout.CENTER);
    mainBorderWrapper.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
    wrapper.add(mainBorderWrapper, BorderLayout.CENTER);
    final JComponent specificDetails = myDetails.create();
    final JPanel borderWrapper = new JPanel(new BorderLayout());
    borderWrapper.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
    borderWrapper.add(specificDetails, BorderLayout.CENTER);

    final Splitter splitter = new Splitter(true, 0.6f);
    splitter.setFirstComponent(wrapper);
    splitter.setSecondComponent(borderWrapper);
    splitter.setDividerWidth(4);
    return splitter;
  }

  private ActionPopupMenu createContextMenu() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(myCopyHashAction);
    final Point location = MouseInfo.getPointerInfo().getLocation();
    SwingUtilities.convertPointFromScreen(location, myJBTable);
    final int row = myJBTable.rowAtPoint(location);
    if (row >= 0) {
      final GitCommit commit = getCommitAtRow(row);
      if (commit != null) {
        myUsersFilterAction.setPreselectedUser(commit.getCommitter());
      }
    }
    group.add(myBranchSelectorAction.asTextAction());
    group.add(myUsersFilterAction.asTextAction());
    group.add(myCherryPickAction);
    group.add(ActionManager.getInstance().getAction("ChangesView.CreatePatchFromChanges"));
    group.add(myRefreshAction);
    return ActionManager.getInstance().createActionPopupMenu(GIT_LOG_TABLE_PLACE, group);
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
    myUserFilterI = new MyFilterUi(new Runnable() {
      @Override
      public void run() {
        reloadRequest();
      }
    });
    myUsersFilterAction = new UsersFilterAction(myProject, myUserFilterI);
    group.add(new MyTextFieldAction());
    group.add(myBranchSelectorAction);
    group.add(myUsersFilterAction);
    myCherryPickAction = new MyCherryPick();
    group.add(myCherryPickAction);
    group.add(ActionManager.getInstance().getAction("ChangesView.CreatePatchFromChanges"));
    myRefreshAction = new MyRefreshAction();
    group.add(myRefreshAction);
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

  private class DataProviderPanel extends JPanel implements TypeSafeDataProvider {
    private DataProviderPanel(LayoutManager layout) {
      super(layout);
    }

    @Override
    public void calcData(DataKey key, DataSink sink) {
      if (VcsDataKeys.CHANGES.equals(key)) {
        final int[] rows = myJBTable.getSelectedRows();
        if (rows.length != 1) return;
        final List<Change> changes = new ArrayList<Change>();
        for (int row : rows) {
          final GitCommit gitCommit = getCommitAtRow(row);
          if (gitCommit == null) return;
          changes.addAll(gitCommit.getChanges());
        }
        sink.put(key, changes.toArray(new Change[changes.size()]));
      } else if (VcsDataKeys.PRESET_COMMIT_MESSAGE.equals(key)) {
        final int[] rows = myJBTable.getSelectedRows();
        if (rows.length != 1) return;
        final CommitI commitAt = myTableModel.getCommitAt(rows[0]);
        if (commitAt == null) return;
        final GitCommit gitCommit = myDetailsCache.convert(commitAt.selectRepository(myRootsUnderVcs), commitAt.getHash());
        if (gitCommit == null) return;
        sink.put(key, gitCommit.getDescription());
      }
    }
  }

  @Nullable
  private GitCommit getCommitAtRow(int row) {
    final CommitI commitAt = myTableModel.getCommitAt(row);
    if (commitAt == null) return null;
    final GitCommit gitCommit = myDetailsCache.convert(commitAt.selectRepository(myRootsUnderVcs), commitAt.getHash());
    if (gitCommit == null) return null;
    return gitCommit;
  }

  private boolean adjustColumnSizes(JScrollPane scrollPane) {
    if (myJBTable.getWidth() <= 0) return false;
    //myJBTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    final TableColumnModel columnModel = myJBTable.getColumnModel();
    final FontMetrics metrics = myJBTable.getFontMetrics(myJBTable.getFont());
    final int height = metrics.getHeight();
    myJBTable.setRowHeight((int) (height * 1.1) + 1);
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

  private Pair<String, List<String>> preparse(String previousFilter) {
    final String[] strings = previousFilter.split("[\\s]");
    StringBuilder sb = new StringBuilder();
    mySearchContext.clear();
    final List<String> words = new ArrayList<String>();
    for (String string : strings) {
      if (string.trim().length() == 0) continue;
      mySearchContext.add(string.toLowerCase());
      final String word = StringUtil.escapeToRegexp(string);
      sb.append(word).append(".*");
      words.add(word);
    }
    new SubstringsFilter().doFilter(mySearchContext);
    return new Pair<String, List<String>>(sb.toString(), words);
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
    myRootsUnderVcs = rootsUnderVcs;
    final RootsHolder rootsHolder = new RootsHolder(rootsUnderVcs);
    myTableModel.setRootsHolder(rootsHolder);
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
      if (o instanceof GitCommit) {
        return o;
      }
      if (BigTableTableModel.LOADING == o) return o;
      return o == null ? "" : o.toString();
    }

    @Override
    public TableCellRenderer getRenderer(Object o) {
      return o instanceof GitCommit ? myDescriptionRenderer : mySimpleRenderer;
    }
  };

  private static abstract class HighlightingRendererBase {
    private final List<String> mySearchContext;

    protected HighlightingRendererBase(List<String> searchContext) {
      mySearchContext = searchContext;
    }

    protected abstract void usual(final String s);
    protected abstract void highlight(final String s);

    void tryHighlight(String text) {
      final String lower = text.toLowerCase();
      int idxFrom = 0;
      while (idxFrom < text.length()) {
        boolean adjusted = false;
        int adjustedIdx = text.length() + 1;
        int adjLen = 0;
        for (String word : mySearchContext) {
          final int next = lower.indexOf(word, idxFrom);
          if ((next != -1) && (adjustedIdx > next)) {
            adjustedIdx = next;
            adjLen = word.length();
            adjusted = true;
          }
        }
        if (adjusted) {
          if (idxFrom != adjustedIdx) {
            usual(text.substring(idxFrom, adjustedIdx));
          }
          idxFrom = adjustedIdx + adjLen;
          highlight(text.substring(adjustedIdx, idxFrom));
          continue;
        }
        usual(text.substring(idxFrom));
        return;
      }
    }
  }

  private class AuthorRenderer extends ColoredTableCellRenderer {
    private final SimpleTextAttributes myCurrentUserAttributes;
    private final SimpleTextAttributes myHighlightAttributes;
    private final List<String> mySearchContext;
    private final SimpleTextAttributes myUsualAttributes;
    protected final HighlightingRendererBase myWorker;

    private AuthorRenderer(SimpleTextAttributes currentUserAttributes,
                           SimpleTextAttributes highlightAttributes,
                           List<String> searchContext,
                           SimpleTextAttributes usualAttributes) {
      myCurrentUserAttributes = currentUserAttributes;
      myHighlightAttributes = highlightAttributes;
      mySearchContext = searchContext;
      myUsualAttributes = usualAttributes;
      myWorker = new HighlightingRendererBase(searchContext) {
        @Override
        protected void usual(String s) {
          append(s, myUsualAttributes);
        }

        @Override
        protected void highlight(String s) {
          append(s, myHighlightAttributes);
        }
      };
    }

    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      setBackground(getLogicBackground(selected, row));
      if (BigTableTableModel.LOADING == value) {
        return;
      }
      final String text = value.toString();

      if (mySearchContext.isEmpty()) {
        append(text, myUsualAttributes);
        return;
      }
      myWorker.tryHighlight(text);
    }
  }

  private class HighLightingRenderer extends ColoredTableCellRenderer {
    private final SimpleTextAttributes myHighlightAttributes;
    private final List<String> mySearchContext;
    private final SimpleTextAttributes myUsualAttributes;
    private SimpleTextAttributes myUsualAttributesForRun;
    protected final HighlightingRendererBase myWorker;

    public HighLightingRenderer(SimpleTextAttributes highlightAttributes, SimpleTextAttributes usualAttributes,
                                final List<String> searchContext) {
      myHighlightAttributes = highlightAttributes;
      mySearchContext = searchContext;
      myUsualAttributes = usualAttributes == null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : usualAttributes;
      myUsualAttributesForRun = myUsualAttributes;
      myWorker = new HighlightingRendererBase(searchContext) {
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

    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      setBackground(getLogicBackground(selected, row));
      if (BigTableTableModel.LOADING == value) {
        return;
      }
      final String text = value.toString();
      myUsualAttributesForRun = isCurrentUser(row, text) ?
                                SimpleTextAttributes.merge(myUsualAttributes, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES) : myUsualAttributes;
      if (mySearchContext.isEmpty()) {
        append(text, myUsualAttributesForRun);
        return;
      }
      myWorker.tryHighlight(text);
    }

    private boolean isCurrentUser(final int row, final String text) {
      final CommitI commitAt = myTableModel.getCommitAt(row);
      if (commitAt == null) return false;
      final SymbolicRefs symbolicRefs = myRefs.get(commitAt.selectRepository(myRootsUnderVcs));
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
      setBackground(getLogicBackground(selected, row));
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
      if (value instanceof GitCommit) {
        final GitCommit commit = (GitCommit)value;
        final int localSize = commit.getLocalBranches() == null ? 0 : commit.getLocalBranches().size();
        final int remoteSize = commit.getRemoteBranches() == null ? 0 : commit.getRemoteBranches().size();
        final int tagsSize = commit.getTags().size();

        if (localSize + remoteSize > 0) {
          final String branch = localSize == 0 ? (commit.getRemoteBranches().get(0)) : commit.getLocalBranches().get(0);

          Icon icon = myBranchMap.get(branch);
          if (icon == null) {
            final boolean plus = localSize + remoteSize + tagsSize > 1;
            final Color color = localSize == 0 ? Colors.remote : Colors.local;
            icon = new CaptionIcon(color, table.getFont().deriveFont((float) table.getFont().getSize() - 1), branch, table,
                                   CaptionIcon.Form.SQUARE, plus, branch.equals(commit.getCurrentBranch()));
            myBranchMap.put(branch, icon);
          }
          myCurrentWidth = icon.getIconWidth();
          myPanel.removeAll();
          myPanel.setBackground(getLogicBackground(isSelected, row));
          myPanel.add(new JLabel(icon));
          myInner.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          myPanel.add(myInner);
          return myPanel;
        }
        if ((localSize + remoteSize == 0) && (tagsSize > 0)) {
          final String tag = commit.getTags().get(0);
          Icon icon = myTagMap.get(tag);
          if (icon == null) {
            icon = new CaptionIcon(Colors.tag, table.getFont().deriveFont((float) table.getFont().getSize() - 1),
                                   tag, table, CaptionIcon.Form.ROUNDED, tagsSize > 1, false);
            myTagMap.put(tag, icon);
          }
          myCurrentWidth = icon.getIconWidth();
          myPanel.removeAll();
          myPanel.setBackground(getLogicBackground(isSelected, row));
          myPanel.add(new JLabel(icon));
          myInner.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          myPanel.add(myInner);
          return myPanel;
        }
      }
      myInner.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      return myInner;
    }

    private class Inner extends HighLightingRenderer {
      private final IssueLinkRenderer myIssueLinkRenderer;
      private final Consumer<String> myConsumer;

      private Inner() {
        super(HIGHLIGHT_TEXT_ATTRIBUTES, null, mySearchContext);
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
        setBackground(getLogicBackground(selected, row));
        if (value instanceof GitCommit) {
          final GitCommit gitCommit = (GitCommit)value;
          myIssueLinkRenderer.appendTextWithLinks(gitCommit.getDescription(), SimpleTextAttributes.REGULAR_ATTRIBUTES, myConsumer);
          //super.customizeCellRenderer(table, ((GitCommit) value).getDescription(), selected, hasFocus, row, column);
        } else {
          super.customizeCellRenderer(table, value, selected, hasFocus, row, column);
        }
      }
    }
  }

  private Color getLogicBackground(final boolean isSelected, final int row) {
    Color bkgColor;
    final CommitI commitAt = myTableModel.getCommitAt(row);
    GitCommit gitCommit = null;
    VirtualFile root = null;
    if (commitAt != null & (! commitAt.holdsDecoration())) {
      root = commitAt.selectRepository(myRootsUnderVcs);
      gitCommit = myDetailsCache.convert(root, commitAt.getHash());
    }

    if (isSelected) {
      bkgColor = UIUtil.getTableSelectionBackground();
    } else {
      bkgColor = UIUtil.getTableBackground();
      if (gitCommit != null) {
        if (myDetailsCache.getStashName(root, gitCommit.getShortHash()) != null) {
          bkgColor = Colors.stashed;
        } else if (gitCommit.isOnLocal() && gitCommit.isOnTracked()) {
          bkgColor = Colors.commonThisBranch;
        } else if (gitCommit.isOnLocal()) {
          bkgColor = Colors.ownThisBranch;
        }
      }
    }
    return bkgColor;
  }

  private ColumnInfo<Object, String> AUTHOR;

  private void initAuthor() {
    AUTHOR = new ColumnInfo<Object, String>("Author") {
      private final TableCellRenderer myRenderer = new HighLightingRenderer(HIGHLIGHT_TEXT_ATTRIBUTES,
                                                                            SimpleTextAttributes.REGULAR_ATTRIBUTES, myUsersSearchContext);

      @Override
      public String valueOf(Object o) {
        if (o instanceof GitCommit) {
          return ((GitCommit)o).getAuthor();
        }
        return "";
      }

      @Override
      public TableCellRenderer getRenderer(Object o) {
        return myRenderer;
      }
    };
  }

  private final ColumnInfo<Object, String> DATE = new ColumnInfo<Object, String>("Date") {
    private final TableCellRenderer myRenderer = new SimpleRenderer(SimpleTextAttributes.REGULAR_ATTRIBUTES, false);

    @Override
    public String valueOf(Object o) {
      if (o instanceof GitCommit) {
        return DateFormatUtil.formatPrettyDateTime(((GitCommit)o).getDate());
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

  private class MyRefreshAction extends DumbAwareAction {
    private MyRefreshAction() {
      super("Refresh", "Refresh", IconLoader.getIcon("/actions/sync.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      rootsChanged(myRootsUnderVcs);
    }
  }

  private class MyTextFieldAction extends SearchFieldAction {
    private MyTextFieldAction() {
      super("Find:");
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
    myState = StepType.CONTINUE;
    final int was = myTableModel.getRowCount();
    myDetailsCache.resetAsideCaches();
    final Collection<String> startingPoints = mySelectedBranch == null ? Collections.<String>emptyList() : Collections.singletonList(mySelectedBranch);
    myDescriptionRenderer.resetIcons();
    final boolean commentFilterEmpty = StringUtil.isEmptyOrSpaces(myPreviousFilter);
    mySearchContext.clear();
    myUsersSearchContext.clear();

    if (commentFilterEmpty && (myUserFilterI.myFilter == null)) {
      myUsersSearchContext.clear();
      myMediator.reload(new RootsHolder(myRootsUnderVcs), startingPoints, new GitLogFilters());
    } else {
      ChangesFilter.Comment comment = null;
      if (! commentFilterEmpty) {
        final Pair<String, List<String>> preparse = preparse(myPreviousFilter);
        final String first = preparse.getFirst();
        comment = new ChangesFilter.Comment(first);
      }
      Set<ChangesFilter.Filter> userFilters = null;
      if (myUserFilterI.myFilter != null) {
        final String[] strings = myUserFilterI.myFilter.split(",");
        userFilters = new HashSet<ChangesFilter.Filter>();
        for (String string : strings) {
          string = string.trim();
          if (string.length() == 0) continue;
          myUsersSearchContext.add(string.toLowerCase());
          final String regexp = "\"" + string + "\"";
          userFilters.add(new ChangesFilter.Committer(regexp));
          userFilters.add(new ChangesFilter.Author(regexp));
        }
      }

      final List<String> possibleReferencies = commentFilterEmpty ? null : Arrays.asList(myPreviousFilter.split("[\\s]"));
      myMediator.reload(new RootsHolder(myRootsUnderVcs), startingPoints, new GitLogFilters(comment, userFilters, null,
                                                                                            possibleReferencies));
    }
    updateMoreVisibility();
    selectionChanged();
    fireTableRepaint();
    myTableModel.fireTableRowsDeleted(0, was);
  }

  private interface Colors {
    Color tag = new Color(241, 239, 158);
    Color remote = new Color(188,188,252);
    Color local = new Color(117,238,199);
    Color ownThisBranch = new Color(198,255,226);
    Color commonThisBranch = new Color(223,223,255);
    Color stashed = new Color(225,225,225);
  }

  private class MySpecificDetails {
    private JEditorPane myJEditorPane;
    private JPanel myMarksPanel;
    private BoxLayout myBoxLayout;
    private final Project myProject;
    private boolean myMissingBranchesInfo;

    private MySpecificDetails(final Project project) {
      myProject = project;
    }

    public JComponent create() {
      myJEditorPane = new JEditorPane(UIUtil.HTML_MIME, "");
      myJEditorPane.setPreferredSize(new Dimension(150, 100));
      myJEditorPane.setEditable(false);
      myJEditorPane.setBackground(UIUtil.getComboBoxDisabledBackground());
      myJEditorPane.addHyperlinkListener(new BrowserHyperlinkListener());
      myMarksPanel = new JPanel();
      myBoxLayout = new BoxLayout(myMarksPanel, BoxLayout.X_AXIS);
      myMarksPanel.setLayout(myBoxLayout);
      final JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.add(myMarksPanel, BorderLayout.NORTH);
      wrapper.add(myJEditorPane, BorderLayout.CENTER);
      final Color color = UIUtil.getTableBackground();
      myJEditorPane.setBackground(color);
      wrapper.setBackground(color);
      myMarksPanel.setBackground(color);
      final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(wrapper);
      return scrollPane;
    }

    public void putBranches(VirtualFile root, final GitCommit commit, final List<String> branches) {
      myMissingBranchesInfo = branches == null;
      myJEditorPane.setText(parseDetails(root, commit, branches));
    }

    public boolean isMissingBranchesInfo() {
      return myMissingBranchesInfo;
    }

    public void refresh(VirtualFile root, final GitCommit commit) {
      if (commit == null) {
        myJEditorPane.setText("");
        myMarksPanel.removeAll();
        myMissingBranchesInfo = false;
      } else {
        final List<String> branches = myDetailsCache.getBranches(root, commit.getShortHash());
        myMissingBranchesInfo = branches == null;
        final Font font = myJEditorPane.getFont().deriveFont((float) (myJEditorPane.getFont().getSize() - 1));
        final String currentBranch = commit.getCurrentBranch();
        myMarksPanel.removeAll();
        for (String s : commit.getLocalBranches()) {
          myMarksPanel.add(new JLabel(new CaptionIcon(Colors.local, font, s, myMarksPanel, CaptionIcon.Form.SQUARE, false,
                                           s.equals(currentBranch))));
        }
        for (String s : commit.getRemoteBranches()) {
          myMarksPanel.add(new JLabel(new CaptionIcon(Colors.remote, font, s, myMarksPanel, CaptionIcon.Form.SQUARE, false,
                                           s.equals(currentBranch))));
        }
        for (String s : commit.getTags()) {
          myMarksPanel.add(new JLabel(new CaptionIcon(Colors.tag, font, s, myMarksPanel, CaptionIcon.Form.ROUNDED, false,
                                           s.equals(currentBranch))));
        }
        myMarksPanel.repaint();
        myJEditorPane.setText(parseDetails(root, commit, branches));
      }
    }

    private String parseDetails(VirtualFile root, final GitCommit c, final List<String> branches) {
      final String hash = new HtmlHighlighter(c.getHash().getValue()).getResult();
      final String author = new HtmlHighlighter(c.getAuthor()).getResult();
      final String committer = new HtmlHighlighter(c.getCommitter()).getResult();
      final String comment = IssueLinkHtmlRenderer.formatTextWithLinks(myProject, c.getDescription(),
                                                                       new Convertor<String, String>() {
                                                                         @Override
                                                                         public String convert(String o) {
                                                                           return new HtmlHighlighter(o).getResult();
                                                                         }
                                                                       });

      final StringBuilder sb = new StringBuilder().append("<html><head>").append(UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()))
        .append("</head><body><table>");
      final String stashName = myDetailsCache.getStashName(root, c.getShortHash());
      if (! StringUtil.isEmptyOrSpaces(stashName)) {
        sb.append("<tr valign=\"top\"><td><b>").append(stashName).append("</b></td><td></td></tr>");
      }
      sb.append("<tr valign=\"top\"><td><i>Hash:</i></td><td>").append(
        hash).append("</td></tr>" + "<tr valign=\"top\"><td><i>Author:</i></td><td>")
        .append(author).append(" (").append(c.getAuthorEmail()).append(") <i>at</i> ")
        .append(DateFormatUtil.formatPrettyDateTime(c.getAuthorTime()))
        .append("</td></tr>" + "<tr valign=\"top\"><td><i>Commiter:</i></td><td>")
        .append(committer).append(" (").append(c.getComitterEmail()).append(") <i>at</i> ")
        .append(DateFormatUtil.formatPrettyDateTime(c.getDate())).append(
        "</td></tr>" + "<tr valign=\"top\"><td><i>Description:</i></td><td><b>")
        .append(comment).append("</b></td></tr>");
      sb.append("<tr valign=\"top\"><td><i>Contained in branches:<i></td><td>");
      if (branches != null && (! branches.isEmpty())) {
        for (int i = 0; i < branches.size(); i++) {
          String s = branches.get(i);
          sb.append(s);
          if (i + 1 < branches.size()) {
            sb.append(", ");
          }
        }
      } else if (branches != null && branches.isEmpty()) {
        sb.append("<font color=gray>&lt;no branches&gt;</font>");
      } else {
        sb.append("<font color=gray>Loading...</font>");
      }
      sb.append("</td></tr>");
      sb.append("</table></body></html>");
      return sb.toString();
    }
  }

  private class HtmlHighlighter extends HighlightingRendererBase {
    private final String myText;
    private final StringBuilder mySb;

    private HtmlHighlighter(String text) {
      super(mySearchContext);
      myText = text;
      mySb = new StringBuilder();
    }

    @Override
    protected void highlight(String s) {
      mySb.append("<font color=rgb(255,128,0)>").append(s).append("</font>");
    }

    @Override
    protected void usual(String s) {
      mySb.append(s);
    }

    public String getResult() {
      tryHighlight(myText);
      return mySb.toString();
    }
  }

  private class MyCherryPick extends DumbAwareAction {
    private final Set<AbstractHash> myIdsInProgress;

    private MyCherryPick() {
      super("Cherry-pick", "Cherry-pick", IconLoader.getIcon("/icons/cherryPick.png"));
      myIdsInProgress = new HashSet<AbstractHash>();
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final MultiMap<VirtualFile, GitCommit> commits = getSelectedCommitsAndCheck();
      if (commits == null) return;
      final int result = Messages.showOkCancelDialog("You are going to cherry-pick changes into current branch. Continue?", "Cherry-pick",
                                                     Messages.getQuestionIcon());
      if (result != 0) return;
      for (GitCommit commit : commits.values()) {
        myIdsInProgress.add(commit.getShortHash());
      }

      final Application application = ApplicationManager.getApplication();
      application.executeOnPooledThread(new Runnable() {
        public void run() {
          for (VirtualFile file : commits.keySet()) {
            final List<GitCommit> part = (List<GitCommit>)commits.get(file);
            // earliest first!!!
            Collections.reverse(part);
            new CherryPicker(GitVcs.getInstance(myProject), part, new LowLevelAccessImpl(myProject, file)).execute();
          }

          application.invokeLater(new Runnable() {
            public void run() {
              for (GitCommit commit : commits.values()) {
                myIdsInProgress.remove(commit.getShortHash());
              }
            }
          });
        }
      });
    }

    // newest first
    @Nullable
    private MultiMap<VirtualFile, GitCommit> getSelectedCommitsAndCheck() {
      if (myJBTable == null) return null;
      final int[] rows = myJBTable.getSelectedRows();
      final MultiMap<VirtualFile, GitCommit> hashes = new MultiMap<VirtualFile, GitCommit>();

      for (int row : rows) {
        final CommitI commitI = myTableModel.getCommitAt(row);
        if (commitI == null) return null;
        if (commitI.holdsDecoration()) return null;
        if (myIdsInProgress.contains(commitI.getHash())) return null;
        final VirtualFile root = commitI.selectRepository(myRootsUnderVcs);
        final GitCommit gitCommit = myDetailsCache.convert(root, commitI.getHash());
        if (gitCommit == null) return null;
        hashes.putValue(root, gitCommit);
      }
      return hashes;
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(enabled());
    }

    private boolean enabled() {
      final MultiMap<VirtualFile, GitCommit> commitsAndCheck = getSelectedCommitsAndCheck();
      if (commitsAndCheck == null) return false;
      for (VirtualFile root : commitsAndCheck.keySet()) {
        final SymbolicRefs refs = myRefs.get(root);
        final String currentBranch = refs == null ? null : (refs.getCurrent() == null ? null : refs.getCurrent().getName());
        if (currentBranch == null) continue;
        final Collection<GitCommit> commits = commitsAndCheck.get(root);
        for (GitCommit commit : commits) {
          if (commit.getParentsHashes().size() > 1) return false;
          final List<String> branches = myDetailsCache.getBranches(root, commit.getShortHash());
          if (branches != null && branches.contains(currentBranch)) {
            return false;
          }
        }
      }
      return true;
    }
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

  private static class MyFilterUi implements UserFilterI {
    private boolean myMeIsKnown;
    private String myMe;
    private String myFilter;
    private final Runnable myReloadCallback;

    public MyFilterUi(Runnable reloadCallback) {
      myReloadCallback = reloadCallback;
    }

    @Override
    public void allSelected() {
      myFilter = null;
      myReloadCallback.run();
    }

    @Override
    public void meSelected() {
      myFilter = myMe;
      myReloadCallback.run();
    }

    @Override
    public void filter(String s) {
      myFilter = s;
      myReloadCallback.run();
    }

    @Override
    public boolean isMeKnown() {
      return myMeIsKnown;
    }

    @Override
    public String getMe() {
      return myMe;
    }

    public void setMe(final String me) {
      myMeIsKnown = ! StringUtil.isEmptyOrSpaces(me);
      myMe = me == null ? "" : me.trim();
    }
  }
}
