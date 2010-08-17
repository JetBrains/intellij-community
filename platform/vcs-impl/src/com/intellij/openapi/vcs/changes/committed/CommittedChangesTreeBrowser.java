/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.committed;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.pom.Navigatable;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeCopyProvider;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionListener;
import java.text.DateFormat;
import java.util.*;
import java.util.List;

/**
 * @author yole
 */
public class CommittedChangesTreeBrowser extends JPanel implements TypeSafeDataProvider, Disposable, DecoratorManager {
  private static final Border LEFT_BORDER = BorderFactory.createMatteBorder(1, 0, 0, 1, UIUtil.getBorderSeparatorColor());
  private static final Border MIDDLE_BORDER = BorderFactory.createMatteBorder(1, 1, 0, 1, UIUtil.getBorderSeparatorColor());
  private static final Border RIGHT_BORDER = BorderFactory.createMatteBorder(1, 1, 0, 0, UIUtil.getBorderSeparatorColor());

  private final Project myProject;
  private final Tree myChangesTree;
  private final RepositoryChangesBrowser myDetailsView;
  private List<CommittedChangeList> myChangeLists;
  private List<CommittedChangeList> mySelectedChangeLists;
  private ChangeListGroupingStrategy myGroupingStrategy = new ChangeListGroupingStrategy.DateChangeListGroupingStrategy();
  private final CompositeChangeListFilteringStrategy myFilteringStrategy = new CompositeChangeListFilteringStrategy();
  private final JPanel myLeftPanel;
  private final FilterChangeListener myFilterChangeListener = new FilterChangeListener();
  private final SplitterProportionsData mySplitterProportionsData = new SplitterProportionsDataImpl();
  private final CopyProvider myCopyProvider;
  private final TreeExpander myTreeExpander;
  private String myHelpId;

  public static final Topic<CommittedChangesReloadListener> ITEMS_RELOADED = new Topic<CommittedChangesReloadListener>("ITEMS_RELOADED", CommittedChangesReloadListener.class);

  private final List<CommittedChangeListDecorator> myDecorators;

  @NonNls public static final String ourHelpId = "reference.changesToolWindow.incoming";

  private final WiseSplitter myInnerSplitter;
  private MessageBusConnection myConnection;
  private TreeState myState;

  public CommittedChangesTreeBrowser(final Project project, final List<CommittedChangeList> changeLists) {
    super(new BorderLayout());

    myProject = project;
    myDecorators = new LinkedList<CommittedChangeListDecorator>();
    myChangeLists = changeLists;
    myChangesTree = new ChangesBrowserTree();
    myChangesTree.setRootVisible(false);
    myChangesTree.setShowsRootHandles(true);
    myChangesTree.setCellRenderer(new CommittedChangeListRenderer(project, myDecorators));
    TreeUtil.expandAll(myChangesTree);

    myDetailsView = new RepositoryChangesBrowser(project, changeLists);
    myDetailsView.getViewer().setScrollPaneBorder(RIGHT_BORDER);

    myChangesTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updateBySelectionChange();
      }
    });

    final TreeLinkMouseListener linkMouseListener = new TreeLinkMouseListener(new CommittedChangeListRenderer(project, myDecorators));
    linkMouseListener.install(myChangesTree);

    myLeftPanel = new JPanel(new BorderLayout());

    final Splitter filterSplitter = new Splitter(false, 0.5f);

    filterSplitter.setSecondComponent(new JScrollPane(myChangesTree));
    myLeftPanel.add(filterSplitter, BorderLayout.CENTER);
    final Splitter mainSplitter = new Splitter(false, 0.7f);
    mainSplitter.setFirstComponent(myLeftPanel);
    mainSplitter.setSecondComponent(myDetailsView);

    add(mainSplitter, BorderLayout.CENTER);

    myInnerSplitter = new WiseSplitter(new Runnable() {
      public void run() {
        filterSplitter.doLayout();
        updateModel();
      }
    }, filterSplitter);

    mySplitterProportionsData.externalizeFromDimensionService("CommittedChanges.SplitterProportions");
    mySplitterProportionsData.restoreSplitterProportions(this);

    updateBySelectionChange();

    ActionManager.getInstance().getAction("CommittedChanges.Details").registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_QUICK_JAVADOC)),
      this);

    myCopyProvider = new TreeCopyProvider(myChangesTree);
    myTreeExpander = new DefaultTreeExpander(myChangesTree);
    myDetailsView.addToolbarAction(ActionManager.getInstance().getAction("Vcs.ShowTabbedFileHistory"));

    myHelpId = ourHelpId;

    myDetailsView.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), myChangesTree);

    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(ITEMS_RELOADED, new CommittedChangesReloadListener() {
      public void itemsReloaded() {
      }
      public void emptyRefresh() {
        updateGrouping();
      }
    });
  }

  public void addFilter(final ChangeListFilteringStrategy strategy) {
    myFilteringStrategy.addStrategy("permanent", strategy);
    strategy.addChangeListener(myFilterChangeListener);
  }

  private void updateGrouping() {
    if (myGroupingStrategy.changedSinceApply()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          updateModel();
        }
      }, ModalityState.NON_MODAL);
    }
  }

  private TreeModel buildTreeModel(final List<CommittedChangeList> filteredChangeLists) {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultTreeModel model = new DefaultTreeModel(root);
    DefaultMutableTreeNode lastGroupNode = null;
    String lastGroupName = null;
    Collections.sort(filteredChangeLists, myGroupingStrategy.getComparator());
    myGroupingStrategy.beforeStart();
    for(CommittedChangeList list: filteredChangeLists) {
      String groupName = myGroupingStrategy.getGroupName(list);
      if (!Comparing.equal(groupName, lastGroupName)) {
        lastGroupName = groupName;
        lastGroupNode = new DefaultMutableTreeNode(lastGroupName);
        root.add(lastGroupNode);
      }
      assert lastGroupNode != null;
      lastGroupNode.add(new DefaultMutableTreeNode(list));
    }
    return model;
  }

  public void setHelpId(final String helpId) {
    myHelpId = helpId;
  }

  public void setEmptyText(final String emptyText) {
    myChangesTree.setEmptyText(emptyText);
  }

  public void clearEmptyText() {
    myChangesTree.clearEmptyText();
  }

  public void appendEmptyText(final String text, final SimpleTextAttributes attrs) {
    myChangesTree.appendEmptyText(text, attrs);
  }

  public void appendEmptyText(final String text, final SimpleTextAttributes attrs, ActionListener clickListener) {
    myChangesTree.appendEmptyText(text, attrs, clickListener);
  }

  public void setToolBar(JComponent toolBar) {
    myLeftPanel.add(toolBar, BorderLayout.NORTH);
    Dimension prefSize = myDetailsView.getHeaderPanel().getPreferredSize();
    if (prefSize.height < toolBar.getPreferredSize().height) {
      prefSize.height = toolBar.getPreferredSize().height;
      myDetailsView.getHeaderPanel().setPreferredSize(prefSize);
    }
  }

  public void dispose() {
    myConnection.disconnect();
    mySplitterProportionsData.saveSplitterProportions(this);
    mySplitterProportionsData.externalizeToDimensionService("CommittedChanges.SplitterProportions");
    myDetailsView.dispose();
  }

  public void setItems(@NotNull List<CommittedChangeList> items, final boolean keepFilter, final CommittedChangesBrowserUseCase useCase) {
    myDetailsView.setUseCase(useCase);
    myChangeLists = items;
    if (!keepFilter) {
      myFilteringStrategy.setFilterBase(items);
    }
    myProject.getMessageBus().syncPublisher(ITEMS_RELOADED).itemsReloaded();
    updateModel();
  }

  private void updateModel() {
    final List<CommittedChangeList> filteredChangeLists = myFilteringStrategy.filterChangeLists(myChangeLists);
    myChangesTree.setModel(buildTreeModel(filteredChangeLists));
    TreeUtil.expandAll(myChangesTree);
  }

  public void setGroupingStrategy(ChangeListGroupingStrategy strategy) {
    myGroupingStrategy = strategy;
    updateModel();
  }

  private void updateBySelectionChange() {
    List<CommittedChangeList> selection = new ArrayList<CommittedChangeList>();
    final TreePath[] selectionPaths = myChangesTree.getSelectionPaths();
    if (selectionPaths != null) {
      for(TreePath path: selectionPaths) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getUserObject() instanceof CommittedChangeList) {
          selection.add((CommittedChangeList) node.getUserObject());
        }
      }
    }

    if (!selection.equals(mySelectedChangeLists)) {
      mySelectedChangeLists = selection;
      myDetailsView.setChangesToDisplay(collectChanges(mySelectedChangeLists, false));
    }
  }

  public static List<Change> collectChanges(final List<CommittedChangeList> selectedChangeLists, final boolean withMovedTrees) {
    List<Change> result = new ArrayList<Change>();
    Collections.sort(selectedChangeLists, new Comparator<CommittedChangeList>() {
      public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
        return o1.getCommitDate().compareTo(o2.getCommitDate());
      }
    });
    for(CommittedChangeList cl: selectedChangeLists) {
      final Collection<Change> changes = withMovedTrees ? cl.getChangesWithMovedTrees() : cl.getChanges();
      for(Change c: changes) {
        addOrReplaceChange(result, c);
      }
    }
    return result;
  }

  private static void addOrReplaceChange(final List<Change> changes, final Change c) {
    final ContentRevision beforeRev = c.getBeforeRevision();
    // todo!!! further improvements needed
    if (beforeRev != null) {
      final String beforeName = beforeRev.getFile().getName();
      final String beforeAbsolutePath = beforeRev.getFile().getIOFile().getAbsolutePath();
      for(Change oldChange: changes) {
        ContentRevision rev = oldChange.getAfterRevision();
        // first compare name, which is many times faster - to remove 99% not matching
        if (rev != null && (rev.getFile().getName().equals(beforeName)) && rev.getFile().getIOFile().getAbsolutePath().equals(beforeAbsolutePath)) {
          changes.remove(oldChange);
          if (oldChange.getBeforeRevision() != null || c.getAfterRevision() != null) {
            changes.add(new Change(oldChange.getBeforeRevision(), c.getAfterRevision()));
          }
          return;
        }
      }
    }
    changes.add(c);
  }

  private List<CommittedChangeList> getSelectedChangeLists() {
    return TreeUtil.collectSelectedObjectsOfType(myChangesTree, CommittedChangeList.class);
  }

  public void setTableContextMenu(final ActionGroup group, final List<AnAction> auxiliaryActions) {
    DefaultActionGroup menuGroup = new DefaultActionGroup();
    menuGroup.add(group);
    for (AnAction action : auxiliaryActions) {
      menuGroup.add(action);
    }
    menuGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY));
    PopupHandler.installPopupHandler(myChangesTree, menuGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
  }

  public void removeFilteringStrategy(final String key) {
    final ChangeListFilteringStrategy strategy = myFilteringStrategy.removeStrategy(key);
    if (strategy != null) {
      strategy.removeChangeListener(myFilterChangeListener);
    }
    myInnerSplitter.remove(key);
  }

  public boolean setFilteringStrategy(final String key, final ChangeListFilteringStrategy filteringStrategy) {
    if (myInnerSplitter.canAdd()) {
      filteringStrategy.setFilterBase(myChangeLists);
      filteringStrategy.addChangeListener(myFilterChangeListener);

      myFilteringStrategy.addStrategy(key, filteringStrategy);

      final JComponent filterUI = filteringStrategy.getFilterUI();
      if (filterUI != null) {
        myInnerSplitter.add(key, filterUI);
      }
      return true;
    }
    return false;
  }

  public ActionToolbar createGroupFilterToolbar(final Project project, final ActionGroup leadGroup, @Nullable final ActionGroup tailGroup,
                                                final List<AnAction> extra) {
    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(leadGroup);
    toolbarGroup.addSeparator();
    toolbarGroup.add(new SelectFilteringAction(project, this));
    toolbarGroup.add(new SelectGroupingAction(this));
    final ExpandAllAction expandAllAction = new ExpandAllAction(myChangesTree);
    final CollapseAllAction collapseAllAction = new CollapseAllAction(myChangesTree);
    expandAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_EXPAND_ALL)),
      myChangesTree);
    collapseAllAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_COLLAPSE_ALL)),
      myChangesTree);
    toolbarGroup.add(expandAllAction);
    toolbarGroup.add(collapseAllAction);
    toolbarGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY));
    toolbarGroup.add(new ContextHelpAction(myHelpId));
    if (tailGroup != null) {
      toolbarGroup.add(tailGroup);
    }
    for (AnAction anAction : extra) {
      toolbarGroup.add(anAction);
    }
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarGroup, true);
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key.equals(VcsDataKeys.CHANGES)) {
      final Collection<Change> changes = collectChanges(getSelectedChangeLists(), false);
      sink.put(VcsDataKeys.CHANGES, changes.toArray(new Change[changes.size()]));
    }
    else if (key.equals(VcsDataKeys.CHANGES_WITH_MOVED_CHILDREN)) {
      final Collection<Change> changes = collectChanges(getSelectedChangeLists(), true);
      sink.put(VcsDataKeys.CHANGES_WITH_MOVED_CHILDREN, changes.toArray(new Change[changes.size()]));
    }
    else if (key.equals(VcsDataKeys.CHANGE_LISTS)) {
      final List<CommittedChangeList> lists = getSelectedChangeLists();
      if (lists.size() > 0) {
        sink.put(VcsDataKeys.CHANGE_LISTS, lists.toArray(new CommittedChangeList[lists.size()]));
      }
    }
    else if (key.equals(PlatformDataKeys.NAVIGATABLE_ARRAY)) {
      final Collection<Change> changes = collectChanges(getSelectedChangeLists(), false);
      Navigatable[] result = ChangesUtil.getNavigatableArray(myProject, ChangesUtil.getFilesFromChanges(changes));
      sink.put(PlatformDataKeys.NAVIGATABLE_ARRAY, result);
    }
    else if (key.equals(PlatformDataKeys.HELP_ID)) {
      sink.put(PlatformDataKeys.HELP_ID, myHelpId);
    } else if (VcsDataKeys.SELECTED_CHANGES_IN_DETAILS.equals(key)) {
      final List<Change> selectedChanges = myDetailsView.getSelectedChanges();
      sink.put(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS, selectedChanges.toArray(new Change[selectedChanges.size()]));
    }
  }

  public TreeExpander getTreeExpander() {
    return myTreeExpander;
  }

  public void repaintTree() {
    myChangesTree.revalidate();
    myChangesTree.repaint();
  }

  public void install(final CommittedChangeListDecorator decorator) {
    myDecorators.add(decorator);
    repaintTree();
  }

  public void remove(final CommittedChangeListDecorator decorator) {
    myDecorators.remove(decorator);
    repaintTree();
  }

  public void reportLoadedLists(final CommittedChangeListsListener listener) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        listener.onBeforeStartReport();
        for (CommittedChangeList list : myChangeLists) {
          listener.report(list);
        }
        listener.onAfterEndReport();
      }
    });
  }

  // for appendable view
  public void reset() {
    myChangeLists.clear();
    myFilteringStrategy.resetFilterBase();

    myState = TreeState.createOn(myChangesTree, (DefaultMutableTreeNode)myChangesTree.getModel().getRoot());
    updateModel();
  }

  public void append(final List<CommittedChangeList> list) {
    final TreeState state = (myChangeLists.isEmpty() && myState != null) ? myState :
      TreeState.createOn(myChangesTree, (DefaultMutableTreeNode)myChangesTree.getModel().getRoot());
    state.setScrollToSelection(false);
    myChangeLists.addAll(list);

    myFilteringStrategy.appendFilterBase(list);

    myChangesTree.setModel(buildTreeModel(myFilteringStrategy.filterChangeLists(myChangeLists)));
    state.applyTo(myChangesTree, (DefaultMutableTreeNode)myChangesTree.getModel().getRoot());
    TreeUtil.expandAll(myChangesTree);
    myProject.getMessageBus().syncPublisher(ITEMS_RELOADED).itemsReloaded();
  }

  public static class CommittedChangeListRenderer extends ColoredTreeCellRenderer {
    private final static DateFormat myDateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    private static final SimpleTextAttributes LINK_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, Color.blue);
    private final IssueLinkRenderer myRenderer;
    private final List<CommittedChangeListDecorator> myDecorators;
    private final Project myProject;

    public CommittedChangeListRenderer(final Project project, final List<CommittedChangeListDecorator> decorators) {
      myProject = project;
      myRenderer = new IssueLinkRenderer(project, this);
      myDecorators = decorators;
    }

    public static String getDateOfChangeList(final Date date) {
      return myDateFormat.format(date);
    }

    public static Pair<String, Boolean> getDescriptionOfChangeList(final String text) {
      String description = text;
      int pos = description.indexOf("\n");
      if (pos >= 0) {
        description = description.substring(0, pos).trim();
        return new Pair<String, Boolean>(description, Boolean.TRUE);
      }
      return new Pair<String, Boolean>(description, Boolean.FALSE);
    }

    public static String truncateDescription(final String initDescription, final FontMetrics fontMetrics, int maxWidth) {
      String description = initDescription;
      int descWidth = fontMetrics.stringWidth(description);
      while(description.length() > 0 && (descWidth > maxWidth)) {
        description = trimLastWord(description);
        descWidth = fontMetrics.stringWidth(description + " ");
      }
      return description;
    }

    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      if (node.getUserObject() instanceof CommittedChangeList) {
        CommittedChangeList changeList = (CommittedChangeList) node.getUserObject();

        final Container parent = tree.getParent();
        int parentWidth = parent == null ? 100 : parent.getWidth() - 44;
        String date = ", " + getDateOfChangeList(changeList.getCommitDate());
        final FontMetrics fontMetrics = tree.getFontMetrics(tree.getFont());
        final FontMetrics boldMetrics = tree.getFontMetrics(tree.getFont().deriveFont(Font.BOLD));
        int size = fontMetrics.stringWidth(date);
        size += boldMetrics.stringWidth(changeList.getCommitterName());

        final Pair<String, Boolean> descriptionInfo = getDescriptionOfChangeList(changeList.getName().trim());
        boolean truncated = descriptionInfo.getSecond().booleanValue();
        String description = descriptionInfo.getFirst();

        for (CommittedChangeListDecorator decorator : myDecorators) {
          final Icon icon = decorator.decorate(changeList);
          if (icon != null) {
            setIcon(icon);
          }
        }

        int descMaxWidth = parentWidth - size - 8;
        boolean partial = (changeList instanceof ReceivedChangeList) && ((ReceivedChangeList)changeList).isPartial();
        if (partial) {
          final String partialMarker = VcsBundle.message("committed.changes.partial.list") + " ";
          append(partialMarker, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          descMaxWidth -= boldMetrics.stringWidth(partialMarker);
        }

        int descWidth = fontMetrics.stringWidth(description);

        int numberWidth = 0;
        final AbstractVcs vcs = changeList.getVcs();
        if (vcs != null) {
          final CachingCommittedChangesProvider provider = vcs.getCachingCommittedChangesProvider();
          if (provider != null && provider.getChangelistTitle() != null) {
            String number = "#" + changeList.getNumber() + "  ";
            numberWidth = fontMetrics.stringWidth(number);
            descWidth += numberWidth;
            append(number, SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        }

        if (description.length() == 0 && !truncated) {
          append(VcsBundle.message("committed.changes.empty.comment"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
          appendAlign(parentWidth - size);
        }
        else if (descMaxWidth < 0) {
          myRenderer.appendTextWithLinks(description);
        }
        else if (descWidth < descMaxWidth && !truncated) {
          myRenderer.appendTextWithLinks(description);
          appendAlign(parentWidth - size);
        }
        else {
          final String moreMarker = VcsBundle.message("changes.browser.details.marker");
          int moreWidth = fontMetrics.stringWidth(moreMarker);
          description = truncateDescription(description, fontMetrics, (descMaxWidth - moreWidth - numberWidth));
          myRenderer.appendTextWithLinks(description);
          // we don't have place for changelist number in this case
          append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          append(moreMarker, LINK_ATTRIBUTES, new MoreLauncher(myProject, changeList));
          appendAlign(parentWidth - size);
        }

        append(changeList.getCommitterName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        append(date, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      else if (node.getUserObject() != null) {
        append(node.getUserObject().toString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    }

    private void appendDescriptionAndNumber(final String description, final String number) {
      myRenderer.appendTextWithLinks(description);
      if (number != null) {
        append(number, SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }

    private static String trimLastWord(final String description) {
      int pos = description.trim().lastIndexOf(' ');
      if (pos >= 0) {
        return description.substring(0, pos).trim();
      }
      return description.substring(0, description.length()-1);
    }

    public Dimension getPreferredSize() {
      return new Dimension(2000, super.getPreferredSize().height);
    }
  }

  public static class MoreLauncher implements Runnable {
    private final Project myProject;
    private final CommittedChangeList myList;

    public MoreLauncher(final Project project, final CommittedChangeList list) {
      myProject = project;
      myList = list;
    }

    public void run() {
      ChangeListDetailsAction.showDetailsPopup(myProject, myList);
    }
  }

  private class FilterChangeListener implements ChangeListener {
    public void stateChanged(ChangeEvent e) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        updateModel();
      } else {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            updateModel();
          }
        });
      }
    }
  }

  private class ChangesBrowserTree extends Tree implements TypeSafeDataProvider {
    public ChangesBrowserTree() {
      super(buildTreeModel(myFilteringStrategy.filterChangeLists(myChangeLists)));
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    public void calcData(final DataKey key, final DataSink sink) {
      if (key.equals(PlatformDataKeys.COPY_PROVIDER)) {
        sink.put(PlatformDataKeys.COPY_PROVIDER, myCopyProvider);
      }
      else if (key.equals(PlatformDataKeys.TREE_EXPANDER)) {
        sink.put(PlatformDataKeys.TREE_EXPANDER, myTreeExpander);
      }
    }
  }

  public interface CommittedChangesReloadListener {
    void itemsReloaded();
    void emptyRefresh();
  }

  public void setLoading(final boolean value) {
    new AbstractCalledLater(myProject, ModalityState.NON_MODAL) {
      public void run() {
        myChangesTree.setPaintBusy(value);
      }
    }.callMe();
  }
}
