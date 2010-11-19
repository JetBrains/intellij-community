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

import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.LayoutViewOptions;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.vcs.ComparableComparator;
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
import com.intellij.ui.content.Content;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Consumer;
import com.intellij.util.Icons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.text.DateFormatUtil;
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
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;

/**
 * @author irengrig
 */
public class GitLogUI implements Disposable {
  private final static Logger LOG = Logger.getInstance("#git4idea.history.wholeTree.GitLogUI");
  public static final SimpleTextAttributes HIGHLIGHT_TEXT_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, new Color(255,128,0));
  private final Project myProject;
  private BigTableTableModel myTableModel;
  private DetailsCache myDetailsCache;
  private final Mediator myMediator;
  private RunnerLayoutUi myUi;
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
  private String mySelectedBranch;
  private BranchSelectorAction myBranchSelectorAction;
  private MySpecificDetails myDetails;
  private final DescriptionRenderer myDescriptionRenderer;
  private FilterAction myFilterAction;

  public GitLogUI(Project project, final Mediator mediator) {
    myProject = project;
    myMediator = mediator;
    myRefs = new HashMap<VirtualFile, SymbolicRefs>();
    myRecalculatedCommon = new SymbolicRefs();
    myPreviousFilter = "";
    myDetails = new MySpecificDetails(myProject);
    myDescriptionRenderer = new DescriptionRenderer();
    createTableModel();
    mySearchContext = new ArrayList<String>();

    myUIRefresh = new UIRefresh() {
      @Override
      public void detailsLoaded() {
        fireTableRepaint();
      }

      @Override
      public void linesReloaded() {
        fireTableRepaint();
      }

      @Override
      public void acceptException(Exception e) {
        LOG.info(e);
      }

      @Override
      public void reportSymbolicRefs(VirtualFile root, SymbolicRefs symbolicRefs) {
        myRefs.put(root, symbolicRefs);

        myRecalculatedCommon.clear();
        if (myRefs.isEmpty()) return;

        String current = null;
        boolean same = true;
        for (SymbolicRefs refs : myRefs.values()) {
          myRecalculatedCommon.addLocals(refs.getLocalBranches());
          myRecalculatedCommon.addRemotes(refs.getRemoteBranches());
          myRecalculatedCommon.addTags(refs.getTags());
          if (current == null) {
            current = refs.getCurrent().getFullName();
          } else if (! current.equals(refs.getCurrent().getFullName())) {
            same = false;
          }
        }
        if (same) {
          myRecalculatedCommon.setCurrent(myRefs.values().iterator().next().getCurrent());
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

  private static class SelectorList extends AbstractList<Integer> {
    private final static SelectorList ourInstance = new SelectorList();

    public static SelectorList getInstance() {
      return ourInstance;
    }

    @Override
    public Integer get(int index) {
      return index;
    }
    @Override
    public int size() {
      return Integer.MAX_VALUE;
    }
  }

  public UIRefresh getUIRefresh() {
    return myUIRefresh;
  }

  public void createMe() {
    // todo think of disposable parent
    myUi = RunnerLayoutUi.Factory.getInstance(myProject).create("Git log", "Git log", "", this);
    //myUi.getDefaults().initTabDefaults(0, "Git log", null);
    myUi.getDefaults().initFocusContent("log1120", LayoutViewOptions.STARTUP);

    //myUi.getOptions().setTopToolbar(group, "Git log");

    myUi.getOptions().setMoveToGridActionEnabled(true);
    myUi.getOptions().setMinimizeActionEnabled(false);

    final JPanel wrapper = createMainTable();
    final Content content = myUi.createContent("log1120", wrapper, "Commits list", IconLoader.getIcon("/icons/gitlogtree.png"), null);
    myUi.addContent(content, 0, PlaceInGrid.center, false);
    content.setCloseable(false);
    content.setPinned(true);

    final JComponent component = createRepositoryBrowserDetails();
    final Content repoContent = myUi.createContent("Commit details11210", component, "Changed files", Icons.FILE_ICON, null);
    myUi.addContent(repoContent, 0, PlaceInGrid.right, false);
    repoContent.setCloseable(false);
    repoContent.setPinned(true);

    Disposer.register(content, new Disposable() {
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
        if (! myDataBeingAdded) {
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
    final int[] rows = myJBTable.getSelectedRows();
    myDetails.refresh(null);
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
    return jPanel;
  }

  public void updateSelection() {
    if (! myMissingSelectionData) return;
    updateDetailsFromSelection();
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
          myDetails.refresh(convert);
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
    final DefaultActionGroup group = new DefaultActionGroup();
    myBranchSelectorAction = new BranchSelectorAction(myProject, new Consumer<String>() {
      @Override
      public void consume(String s) {
        mySelectedBranch = s;
        reloadRequest();
      }
    });
    myFilterAction = new FilterAction(myProject);
    group.add(new MyTextFieldAction());
    group.add(myBranchSelectorAction);
    group.add(myFilterAction);
    group.add(new MyCherryPick());
    group.add(new MyRefreshAction());
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("Git log", group, true);

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
    //myJBTable.setTableHeader(null);
    myJBTable.setShowGrid(false);
    myJBTable.setModel(myTableModel);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myJBTable);
    final ComponentListener listener = new ComponentListener() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (myStarted) {
          if (adjustColumnSizes(scrollPane)) {
            myJBTable.removeComponentListener(this);
          }
        }
      }
      @Override
      public void componentMoved(ComponentEvent e) {
      }
      @Override
      public void componentShown(ComponentEvent e) {
        if (myStarted) {
          if (adjustColumnSizes(scrollPane)) {
            myJBTable.removeComponentListener(this);
          }
        }
      }
      @Override
      public void componentHidden(ComponentEvent e) {
      }
    };
    myJBTable.addComponentListener(listener);
    myMyChangeListener = new GitTableScrollChangeListener(myJBTable, myDetailsCache, myTableModel, new Runnable() {
      @Override
      public void run() {
        updateSelection();
      }
    });
    scrollPane.getViewport().addChangeListener(myMyChangeListener);

    final JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(actionToolbar.getComponent(), BorderLayout.NORTH);
    wrapper.add(scrollPane, BorderLayout.CENTER);
    final JComponent specificDetails = myDetails.create();

    final Splitter splitter = new Splitter(true, 0.6f);
    splitter.setFirstComponent(wrapper);
    splitter.setSecondComponent(specificDetails);
    splitter.setDividerWidth(4);
    return splitter;
  }

  private boolean adjustColumnSizes(JScrollPane scrollPane) {
    if (myJBTable.getWidth() <= 0) return false;
    //myJBTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    final FontMetrics metrics = myJBTable.getFontMetrics(myJBTable.getFont());
    final int dateWidth = metrics.stringWidth("Yesterday 00:00:00 ");
    final int nameWidth = metrics.stringWidth("Somelong W. UsernameToDisplay");
    final TableColumnModel columnModel = myJBTable.getColumnModel();
    int widthWas = 0;
    for (int i = 0; i < columnModel.getColumnCount(); i++) {
      widthWas += columnModel.getColumn(i).getWidth();
    }

    columnModel.getColumn(1).setWidth(nameWidth);
    columnModel.getColumn(1).setPreferredWidth(nameWidth);

    columnModel.getColumn(2).setWidth(dateWidth);
    columnModel.getColumn(2).setPreferredWidth(dateWidth);
    // todo if too small

    final int nullWidth = widthWas - dateWidth - nameWidth - columnModel.getColumnMargin() * 3 -
      scrollPane.getVerticalScrollBar().getWidth() - 10;
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
    return myUi.getComponent();
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

  private abstract class HighlightingRendererBase {
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

  private class HighLightingRenderer extends ColoredTableCellRenderer {
    private final SimpleTextAttributes myHighlightAttributes;
    private final SimpleTextAttributes myUsualAttributes;
    protected final HighlightingRendererBase myWorker;

    public HighLightingRenderer(SimpleTextAttributes highlightAttributes, SimpleTextAttributes usualAttributes) {
      myHighlightAttributes = highlightAttributes;
      myUsualAttributes = usualAttributes == null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : usualAttributes;
      myWorker = new HighlightingRendererBase() {
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
  
  private static class PositionAndBorderContainer implements TableCellRenderer {
    private final JPanel myPanel;
    private final ColoredTableCellRenderer myDelegate;
    private final DottedBorder myDottedBorder;

    public PositionAndBorderContainer(ColoredTableCellRenderer delegate) {
      myDelegate = delegate;
      myPanel = new JPanel(new BorderLayout());
      myPanel.setBackground(UIUtil.getTableBackground());
      myDottedBorder = new DottedBorder(UIUtil.getFocusedBoundsColor());
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      myPanel.removeAll();
      final Component component = myDelegate.getTableCellRendererComponent(table, value, isSelected, false, row, column);
      myPanel.add(component, BorderLayout.SOUTH);
      if (hasFocus) {
        myDelegate.setBorder(myDottedBorder);
      } else {
        myDelegate.setBorder(null);
      }
      return myPanel;
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
    final Color bkgColor;
    final CommitI commitAt = myTableModel.getCommitAt(row);
    GitCommit gitCommit = null;
    if (commitAt != null & (! commitAt.holdsDecoration())) {
      gitCommit = myDetailsCache.convert(commitAt.selectRepository(myRootsUnderVcs), commitAt.getHash());
    }

    if (isSelected) {
      bkgColor = UIUtil.getTableSelectionBackground();
    } else {
      if (gitCommit != null && gitCommit.isOnLocal() && gitCommit.isOnTracked()) {
        bkgColor = Colors.commonThisBranch;
      } else if (gitCommit != null && gitCommit.isOnLocal()) {
        bkgColor = Colors.ownThisBranch;
      } else {
        bkgColor = UIUtil.getTableBackground();
      }
    }
    return bkgColor;
  }

  private class MyTestDescriptionRenderer implements TableCellRenderer {
    private JPanel myJPanel;
    private final ColoredTableCellRenderer myHeaderRenderer;
    private final ColoredTableCellRenderer myInnerRenderer;
    private int myHeight;
    private final DottedBorder myDottedBorder;
    private int myLeading;

    private MyTestDescriptionRenderer() {
      myJPanel = new JPanel(new BorderLayout());
      myDottedBorder = new DottedBorder(UIUtil.getFocusedBoundsColor());
      myJPanel.setBackground(UIUtil.getTableBackground());
      myHeaderRenderer = new SimpleRenderer(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, false);
      myInnerRenderer = new HighLightingRenderer(HIGHLIGHT_TEXT_ATTRIBUTES, null);
      myInnerRenderer.setBorder(null);
      myHeight = -1;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (myHeight == -1) {
        final Font font = myJBTable.getFont();
        final FontMetrics fm = myJBTable.getFontMetrics(font);
        final Graphics g = myJBTable.getGraphics();
        final Rectangle2D bounds = fm.getStringBounds("AWQ", g);
        final FontMetrics metrics = g.getFontMetrics();
        myLeading = metrics.getLeading();
        myHeight = (int) (metrics.getHeight() * 1.3);
      }
      if (! (value instanceof GitCommit)) {
        myInnerRenderer.getTableCellRendererComponent(table, value.toString(), isSelected, hasFocus, row, column);
        table.setRowHeight(row, myHeight);
        return myInnerRenderer;
      }
      final GitCommit gitCommit = (GitCommit) value;
      final CommitI commitAt = myTableModel.getCommitAt(row);
      if (commitAt != null && commitAt.holdsDecoration()) {
        myJPanel.removeAll();
        myJPanel.add(myHeaderRenderer, BorderLayout.NORTH);
        myJPanel.add(myInnerRenderer, BorderLayout.CENTER);
        myInnerRenderer.getTableCellRendererComponent(table, gitCommit.getDescription(), isSelected, false, row, column);
        myHeaderRenderer.getTableCellRendererComponent(table, commitAt.getDecorationString(), false, false, row, column);
        /*if (hasFocus) {
          myJPanel.setBorder(myDottedBorder);
        } else {
          myJPanel.setBorder(null);
        }*/
        if (hasFocus) {
          myInnerRenderer.setBorder(myDottedBorder);
        } else {
          myInnerRenderer.setBorder(null);
        }
        table.setRowHeight(row, (int) (2 * myHeight));
        return myJPanel;
      } else {
        myInnerRenderer.getTableCellRendererComponent(table, gitCommit.getDescription(), isSelected, false, row, column);
        if (hasFocus) {
          myInnerRenderer.setBorder(myDottedBorder);
        } else {
          myInnerRenderer.setBorder(null);
        }
        table.setRowHeight(row, myHeight);
        return myInnerRenderer;
      }
    }
  }

  private final ColumnInfo<Object, String> AUTHOR = new ColumnInfo<Object, String>("Author") {
    private final TableCellRenderer myRenderer = new HighLightingRenderer(HIGHLIGHT_TEXT_ATTRIBUTES, SimpleTextAttributes.REGULAR_ATTRIBUTES);

    @Override
    public String valueOf(Object o) {
      if (o instanceof GitCommit) {
        return ((GitCommit) o).getCommitter();
      }
      return "";
    }

    @Override
    public TableCellRenderer getRenderer(Object o) {
      return myRenderer;
    }
  };
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
    final int was = myTableModel.getRowCount();
    final Collection<String> startingPoints = mySelectedBranch == null ? Collections.<String>emptyList() : Collections.singletonList(mySelectedBranch);
    myDescriptionRenderer.resetIcons();
    if (StringUtil.isEmptyOrSpaces(myPreviousFilter)) {
      mySearchContext.clear();
      myMediator.reload(new RootsHolder(myRootsUnderVcs), startingPoints, Collections.<ChangesFilter.Filter>emptyList(), null);
    } else {
      final List<ChangesFilter.Filter> filters = new ArrayList<ChangesFilter.Filter>();
      final Pair<String, List<String>> preparse = preparse(myPreviousFilter);
      filters.add(new ChangesFilter.Comment(preparse.getFirst()));

      for (String s : preparse.getSecond()) {
        filters.add(new ChangesFilter.Author(s));
        filters.add(new ChangesFilter.Committer(s));
      }

      myMediator.reload(new RootsHolder(myRootsUnderVcs), startingPoints, filters, myPreviousFilter.split("[\\s]"));
    }
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
  }

  private class MySpecificDetails {
    private JEditorPane myJEditorPane;
    private JPanel myMarksPanel;
    private BoxLayout myBoxLayout;
    private final Project myProject;

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

    public void refresh(final GitCommit commit) {
      if (commit == null) {
        myJEditorPane.setText("");
        myMarksPanel.removeAll();
      } else {
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
        myJEditorPane.setText(parseDetails(commit));
      }
    }

    private String parseDetails(final GitCommit c) {
      final List<String> branches = c.getBranches();
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
        .append("</head><body><table><tr valign=\"top\"><td><i>Hash:</i></td><td>").append(
          hash).append("</td></tr>" + "<tr valign=\"top\"><td><i>Author:</i></td><td>")
        .append(author).append(" (").append(c.getAuthorEmail()).append(") <i>at</i> ")
        .append(DateFormatUtil.formatPrettyDateTime(c.getAuthorTime()))
        .append("</td></tr>" + "<tr valign=\"top\"><td><i>Commiter:</i></td><td>")
        .append(committer).append(" (").append(c.getComitterEmail()).append(") <i>at</i> ")
        .append(DateFormatUtil.formatPrettyDateTime(c.getDate())).append(
          "</td></tr>" + "<tr valign=\"top\"><td><i>Description:</i></td><td><b>")
        .append(comment).append("</b></td></tr>");
      sb.append("<tr><td><i>Contained in branches:<i></td><td>");
      if (branches != null && (! branches.isEmpty())) {
        for (int i = 0; i < branches.size(); i++) {
          String s = branches.get(i);
          sb.append(s);
          if (i + 1 < branches.size()) {
            sb.append(", ");
          }
        }
      } else {
        sb.append("<font color=gray>no branches</font>");
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
      final boolean enabled = getSelectedCommitsAndCheck() != null;
      e.getPresentation().setEnabled(enabled);
    }
  }
}
