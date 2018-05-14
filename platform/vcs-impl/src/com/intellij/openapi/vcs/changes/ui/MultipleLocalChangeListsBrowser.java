// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.ex.ThreeStateCheckboxAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.RollbackDialogAction;
import com.intellij.openapi.vcs.changes.actions.diff.UnversionedDiffRequestProducer;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker.ExclusionState;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vcs.impl.PartialChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ThreeStateCheckBox.State;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.openapi.util.text.StringUtil.shortenTextWithEllipsis;
import static com.intellij.openapi.vcs.changes.ui.ChangesListView.UNVERSIONED_FILES_DATA_KEY;
import static com.intellij.util.FontUtil.spaceAndThinSpace;
import static com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT;

public class MultipleLocalChangeListsBrowser extends CommitDialogChangesBrowser implements Disposable {
  @NotNull private final MergingUpdateQueue myUpdateQueue =
    new MergingUpdateQueue("MultipleLocalChangeListsBrowser", 300, true, ANY_COMPONENT, this);

  private final boolean myEnableUnversioned;
  private final boolean myEnablePartialCommit;
  @Nullable private JComponent myBottomDiffComponent;

  @NotNull private final ChangeListChooser myChangeListChooser;
  @NotNull private final DeleteProvider myDeleteProvider = new VirtualFileDeleteProvider();

  private final List<Change> myChanges = new ArrayList<>();
  private final List<VirtualFile> myUnversioned = new ArrayList<>();
  private boolean myHasHiddenUnversioned;

  @NotNull private LocalChangeList myChangeList;

  @Nullable private Runnable mySelectedListChangeListener;

  public MultipleLocalChangeListsBrowser(@NotNull Project project,
                                         boolean showCheckboxes,
                                         boolean highlightProblems,
                                         boolean enableUnversioned,
                                         boolean enablePartialCommit) {
    super(project, showCheckboxes, highlightProblems);
    myEnableUnversioned = enableUnversioned;
    myEnablePartialCommit = enablePartialCommit;

    myChangeList = ChangeListManager.getInstance(project).getDefaultChangeList();
    myChangeListChooser = new ChangeListChooser();

    if (Registry.is("vcs.skip.single.default.changelist")) {
      List<LocalChangeList> allChangeLists = ChangeListManager.getInstance(project).getChangeLists();
      if (allChangeLists.size() == 1 && allChangeLists.get(0).isBlank()) {
        myChangeListChooser.setVisible(false);
      }
    }

    ChangeListManager.getInstance(myProject).addChangeListListener(new MyChangeListListener(), this);
    init();

    updateDisplayedChangeLists();
    updateSelectedChangeList(myChangeList);
  }

  @NotNull
  @Override
  protected ChangesBrowserTreeList createTreeList(@NotNull Project project, boolean showCheckboxes, boolean highlightProblems) {
    return new MyChangesBrowserTreeList(project, showCheckboxes, highlightProblems);
  }

  @Nullable
  @Override
  protected JComponent createHeaderPanel() {
    return JBUI.Panels.simplePanel(myChangeListChooser)
                      .withBorder(JBUI.Borders.emptyLeft(6));
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    return ContainerUtil.append(
      super.createToolbarActions(),
      new RollbackDialogAction(),
      ActionManager.getInstance().getAction("ChangesView.Refresh"),
      ActionManager.getInstance().getAction("Vcs.CheckinProjectToolbar")
    );
  }

  @NotNull
  @Override
  protected List<AnAction> createPopupMenuActions() {
    List<AnAction> result = new ArrayList<>(super.createPopupMenuActions());

    result.add(ActionManager.getInstance().getAction("ChangesView.Refresh"));

    if (myEnableUnversioned) {
      result.add(new ShowHideUnversionedFilesAction());

      // We do not add "Delete" key shortcut for deleting unversioned files as this shortcut is already used to uncheck checkboxes in the tree.
      ActionGroup unversionedGroup = UnversionedViewDialog.getUnversionedPopupGroup();
      result.add(unversionedGroup);
      ActionUtil.recursiveRegisterShortcutSet(unversionedGroup, myViewer, null);
    }
    else {
      // avoid duplicated actions on toolbar
      result.add(ActionManager.getInstance().getAction(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST));
    }

    EmptyAction.registerWithShortcutSet(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST, CommonShortcuts.getMove(), myViewer);

    RollbackDialogAction rollbackAction = new RollbackDialogAction();
    rollbackAction.registerCustomShortcutSet(this, null);
    result.add(rollbackAction);

    EditSourceForDialogAction editSourceAction = new EditSourceForDialogAction(this);
    editSourceAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), this);
    result.add(editSourceAction);

    result.add(ActionManager.getInstance().getAction("Vcs.CheckinProjectMenu"));
    return result;
  }

  @NotNull
  @Override
  protected List<AnAction> createDiffActions() {
    return ContainerUtil.append(
      super.createDiffActions(),
      new ToggleChangeDiffAction()
    );
  }

  @Override
  protected void updateDiffContext(@NotNull DiffRequestChain chain) {
    super.updateDiffContext(chain);
    chain.putUserData(DiffUserDataKeysEx.BOTTOM_PANEL, myBottomDiffComponent);
    chain.putUserData(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, myEnablePartialCommit);
  }


  public void setBottomDiffComponent(@NotNull JComponent value) {
    myBottomDiffComponent = value;
  }

  public void setSelectedListChangeListener(@Nullable Runnable runnable) {
    mySelectedListChangeListener = runnable;
  }

  private boolean isShowUnversioned() {
    return myEnableUnversioned && VcsConfiguration.getInstance(myProject).SHOW_UNVERSIONED_FILES_WHILE_COMMIT;
  }

  private void setShowUnversioned(boolean value) {
    VcsConfiguration.getInstance(myProject).SHOW_UNVERSIONED_FILES_WHILE_COMMIT = value;
    updateDisplayedChanges();
  }

  @NotNull
  @Override
  public LocalChangeList getSelectedChangeList() {
    return myChangeList;
  }

  public void setSelectedChangeList(@NotNull LocalChangeList list) {
    myChangeListChooser.setSelectedChangeList(list);
  }

  private void updateSelectedChangeList(@NotNull LocalChangeList list) {
    boolean isListChanged = !myChangeList.getId().equals(list.getId());
    if (isListChanged) {
      LineStatusTrackerManager.getInstanceImpl(myProject).resetExcludedFromCommitMarkers();
    }
    myChangeList = list;
    myChangeListChooser.setToolTipText(list.getName());
    updateDisplayedChanges();
    if (isListChanged && mySelectedListChangeListener != null) mySelectedListChangeListener.run();

    ((MyChangesBrowserTreeList)myViewer).updateExclusionStates();
  }

  public void updateDisplayedChangeLists() {
    List<LocalChangeList> changeLists = ChangeListManager.getInstance(myProject).getChangeLists();
    myChangeListChooser.setAvailableLists(changeLists);
  }

  public void updateDisplayedChanges() {
    myChanges.clear();
    myUnversioned.clear();
    myHasHiddenUnversioned = false;

    myChanges.addAll(myChangeList.getChanges());

    if (myEnableUnversioned) {
      List<VirtualFile> unversioned = ChangeListManagerImpl.getInstanceImpl(myProject).getUnversionedFiles();
      if (isShowUnversioned()) {
        myUnversioned.addAll(unversioned);
      }
      if (!isShowUnversioned() && !unversioned.isEmpty()) {
        myHasHiddenUnversioned = true;
      }
    }

    myViewer.rebuildTree();
  }

  @NotNull
  @Override
  protected DefaultTreeModel buildTreeModel() {
    MyChangeNodeDecorator decorator = new MyChangeNodeDecorator();

    TreeModelBuilder builder = new TreeModelBuilder(myProject, getGrouping());
    builder.setChanges(myChanges, decorator);
    builder.setUnversioned(myUnversioned);

    if (myHasHiddenUnversioned) {
      myViewer.getEmptyText()
        .setText("Unversioned files available. ")
        .appendText("Show", SimpleTextAttributes.LINK_ATTRIBUTES, e -> setShowUnversioned(true));
    }
    else {
      myViewer.getEmptyText()
        .setText(DiffBundle.message("diff.count.differences.status.text", 0));
    }

    return builder.build();
  }

  @Nullable
  @Override
  protected ChangeDiffRequestChain.Producer getDiffRequestProducer(@NotNull Object entry) {
    if (entry instanceof VirtualFile) {
      return UnversionedDiffRequestProducer.create(myProject, (VirtualFile)entry);
    }
    return super.getDiffRequestProducer(entry);
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    if (UNVERSIONED_FILES_DATA_KEY.is(dataId)) {
      return VcsTreeModelData.selected(myViewer).userObjectsStream(VirtualFile.class);
    }
    else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return myDeleteProvider;
    }
    else if (VcsDataKeys.CHANGE_LISTS.is(dataId)) {
      return new ChangeList[]{myChangeList};
    }
    return super.getData(dataId);
  }


  @NotNull
  @Override
  public List<Change> getDisplayedChanges() {
    return VcsTreeModelData.all(myViewer).userObjects(Change.class);
  }

  @NotNull
  @Override
  public List<Change> getSelectedChanges() {
    return VcsTreeModelData.selected(myViewer).userObjects(Change.class);
  }

  @NotNull
  @Override
  public List<Change> getIncludedChanges() {
    return VcsTreeModelData.included(myViewer).userObjects(Change.class);
  }

  @NotNull
  @Override
  public List<VirtualFile> getDisplayedUnversionedFiles() {
    if (!isShowUnversioned()) return Collections.emptyList();

    VcsTreeModelData treeModelData = VcsTreeModelData.allUnderTag(myViewer, ChangesBrowserNode.UNVERSIONED_FILES_TAG);
    if (containsCollapsedUnversionedNode(treeModelData)) return myUnversioned;

    return treeModelData.userObjects(VirtualFile.class);
  }

  @NotNull
  @Override
  public List<VirtualFile> getSelectedUnversionedFiles() {
    if (!isShowUnversioned()) return Collections.emptyList();

    VcsTreeModelData treeModelData = VcsTreeModelData.selectedUnderTag(myViewer, ChangesBrowserNode.UNVERSIONED_FILES_TAG);
    if (containsCollapsedUnversionedNode(treeModelData)) return myUnversioned;

    return treeModelData.userObjects(VirtualFile.class);
  }

  @NotNull
  @Override
  public List<VirtualFile> getIncludedUnversionedFiles() {
    if (!isShowUnversioned()) return Collections.emptyList();

    VcsTreeModelData treeModelData = VcsTreeModelData.includedUnderTag(myViewer, ChangesBrowserNode.UNVERSIONED_FILES_TAG);
    if (containsCollapsedUnversionedNode(treeModelData)) return myUnversioned;

    return treeModelData.userObjects(VirtualFile.class);
  }

  private static boolean containsCollapsedUnversionedNode(@NotNull VcsTreeModelData treeModelData) {
    Optional<ChangesBrowserNode> node = treeModelData.nodesStream()
      .filter(it -> it instanceof ChangesBrowserUnversionedFilesNode).findAny();
    if (!node.isPresent()) return false;

    ChangesBrowserUnversionedFilesNode unversionedFilesNode = (ChangesBrowserUnversionedFilesNode)node.get();
    return unversionedFilesNode.isManyFiles();
  }

  private class MyChangeNodeDecorator implements ChangeNodeDecorator {
    private final ChangeNodeDecorator myRemoteRevisionsDecorator = RemoteRevisionsCache.getInstance(myProject).getChangesNodeDecorator();

    @Override
    public void decorate(Change change, SimpleColoredComponent renderer, boolean isShowFlatten) {
      PartialLocalLineStatusTracker tracker = PartialChangesUtil.getPartialTracker(myProject, change);
      if (tracker != null) {
        List<PartialLocalLineStatusTracker.LocalRange> ranges = tracker.getRanges();
        if (ranges != null) {
          int rangesToCommit = ContainerUtil.count(ranges, it -> {
            return it.getChangelistId().equals(myChangeList.getId()) && !it.isExcludedFromCommit();
          });
          if (rangesToCommit != 0 && rangesToCommit != ranges.size()) {
            renderer.append(String.format(spaceAndThinSpace() + "%s of %s changes", rangesToCommit, ranges.size()),
                            SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
          }
        }
      }

      myRemoteRevisionsDecorator.decorate(change, renderer, isShowFlatten);
    }

    @Override
    public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean isShowFlatten) {
      myRemoteRevisionsDecorator.preDecorate(change, renderer, isShowFlatten);
    }
  }


  private class ChangeListChooser extends JPanel {
    private final static int MAX_NAME_LEN = 35;
    @NotNull private final ComboBox<LocalChangeList> myChooser = new ComboBox<>();

    public ChangeListChooser() {
      myChooser.setEditable(false);
      myChooser.setRenderer(new ColoredListCellRenderer<LocalChangeList>() {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends LocalChangeList> list, LocalChangeList value,
                                             int index, boolean selected, boolean hasFocus) {
          String name = shortenTextWithEllipsis(value.getName().trim(), MAX_NAME_LEN, 0);
          append(name, value.isDefault() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      });

      myChooser.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            LocalChangeList changeList = (LocalChangeList)myChooser.getSelectedItem();
            if (changeList != null) {
              updateSelectedChangeList(changeList);
            }
          }
        }
      });

      setLayout(new BorderLayout(4, 2));

      JLabel label = new JLabel(VcsBundle.message("commit.dialog.changelist.label"));
      label.setLabelFor(myChooser);
      add(label, BorderLayout.WEST);

      add(myChooser, BorderLayout.CENTER);
    }

    public void setAvailableLists(@NotNull List<LocalChangeList> lists) {
      LocalChangeList currentList = ContainerUtil.find(lists, getSelectedChangeList());
      if (currentList == null) currentList = lists.get(0);

      myChooser.setModel(new CollectionComboBoxModel<>(lists, currentList));
      myChooser.setEnabled(lists.size() > 1);

      updateSelectedChangeList(currentList);
    }

    public void setSelectedChangeList(@NotNull LocalChangeList list) {
      ComboBoxModel<LocalChangeList> model = myChooser.getModel();
      for (int i = 0; i < model.getSize(); i++) {
        LocalChangeList element = model.getElementAt(i);
        if (element.getName().equals(list.getName())) {
          myChooser.setSelectedIndex(i);
          updateSelectedChangeList(element);
          return;
        }
      }
    }
  }


  private class ShowHideUnversionedFilesAction extends ToggleAction implements DumbAware {
    private ShowHideUnversionedFilesAction() {
      super("Show Unversioned Files", null, AllIcons.Vcs.ShowUnversionedFiles);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return isShowUnversioned();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      setShowUnversioned(state);
    }
  }

  private class ToggleChangeDiffAction extends ThreeStateCheckboxAction implements CustomComponentAction {
    public ToggleChangeDiffAction() {
      super(VcsBundle.message("commit.dialog.include.action.name"));
    }

    @NotNull
    @Override
    public State isSelected(AnActionEvent e) {
      Object object = getUserObject(e);
      if (object == null) return State.NOT_SELECTED;
      return ((MyChangesBrowserTreeList)myViewer).getUserObjectState(object);
    }

    @Override
    public void setSelected(AnActionEvent e, @NotNull State state) {
      Object object = getUserObject(e);
      if (object == null) return;

      if (state != State.NOT_SELECTED) {
        myViewer.includeChange(object);
      }
      else {
        myViewer.excludeChange(object);
      }
    }

    @Nullable
    private Object getUserObject(AnActionEvent e) {
      Object object = e.getData(VcsDataKeys.CURRENT_CHANGE);
      if (object == null) object = e.getData(VcsDataKeys.CURRENT_UNVERSIONED);
      return object;
    }
  }


  private class MyChangeListListener extends ChangeListAdapter {
    @Override
    public void changeListsChanged() {
      myUpdateQueue.queue(new Update("updateChangeLists") {
        @Override
        public void run() {
          updateDisplayedChangeLists();
        }
      });
    }
  }

  private class MyChangesBrowserTreeList extends ChangesBrowserTreeList {
    private final Set<Object> myIncludedChanges = new THashSet<>();
    private final Map<Change, ExclusionState> myTrackerExclusionStates = new HashMap<>();

    public MyChangesBrowserTreeList(@NotNull Project project, boolean showCheckboxes, boolean highlightProblems) {
      super(MultipleLocalChangeListsBrowser.this, project, showCheckboxes, highlightProblems);

      PartialLocalLineStatusTracker.ListenerAdapter trackerListener = new PartialLocalLineStatusTracker.ListenerAdapter() {
        @Override
        public void onExcludedFromCommitChange(@NotNull PartialLocalLineStatusTracker tracker) {
          scheduleExclusionStatesUpdate();
        }
      };

      LineStatusTrackerManager.ListenerAdapter trackerManagerListener = new LineStatusTrackerManager.ListenerAdapter() {
        @Override
        public void onTrackerAdded(@NotNull LineStatusTracker<?> tracker) {
          if (tracker instanceof PartialLocalLineStatusTracker) {
            PartialLocalLineStatusTracker partialTracker = (PartialLocalLineStatusTracker)tracker;

            Change change = findChangeFor(tracker);
            if (change != null) {
              partialTracker.setExcludedFromCommit(!myIncludedChanges.contains(change));
            }

            partialTracker.addListener(trackerListener, MultipleLocalChangeListsBrowser.this);
          }
        }

        @Override
        public void onTrackerRemoved(@NotNull LineStatusTracker<?> tracker) {
          if (tracker instanceof PartialLocalLineStatusTracker) {
            Change change = findChangeFor(tracker);
            if (change != null) {
              myTrackerExclusionStates.remove(change);

              ExclusionState exclusionState = ((PartialLocalLineStatusTracker)tracker).getExcludedFromCommitState(myChangeList.getId());
              if (exclusionState != ExclusionState.NO_CHANGES) {
                if (exclusionState != ExclusionState.ALL_EXCLUDED) {
                  myIncludedChanges.add(change);
                }
                else {
                  myIncludedChanges.remove(change);
                }
              }

              scheduleExclusionStatesUpdate();
            }
          }
        }
      };

      LineStatusTrackerManager.getInstanceImpl(project).addTrackerListener(trackerManagerListener, MultipleLocalChangeListsBrowser.this);
      for (LineStatusTracker<?> tracker : LineStatusTrackerManager.getInstanceImpl(project).getTrackers()) {
        if (tracker instanceof PartialLocalLineStatusTracker) {
          ((PartialLocalLineStatusTracker)tracker).addListener(trackerListener, MultipleLocalChangeListsBrowser.this);
        }
      }
    }

    @NotNull
    private State getUserObjectState(@NotNull Object change) {
      ExclusionState exclusionState = getExclusionState(change);
      if (exclusionState == ExclusionState.ALL_INCLUDED) {
        return State.SELECTED;
      }
      else if (exclusionState == ExclusionState.ALL_EXCLUDED) {
        return State.NOT_SELECTED;
      }
      else {
        return State.DONT_CARE;
      }
    }

    @NotNull
    @Override
    protected State getNodeStatus(@NotNull ChangesBrowserNode<?> node) {
      boolean hasIncluded = false;
      boolean hasExcluded = false;

      for (Object change : VcsTreeModelData.children(node).userObjects()) {
        ExclusionState exclusionState = getExclusionState(change);

        if (exclusionState == ExclusionState.ALL_INCLUDED) {
          hasIncluded = true;
        }
        else if (exclusionState == ExclusionState.ALL_EXCLUDED) {
          hasExcluded = true;
        }
        else {
          hasIncluded = true;
          hasExcluded = true;
        }
      }

      if (hasIncluded && hasExcluded) return State.DONT_CARE;
      if (hasIncluded) return State.SELECTED;
      return State.NOT_SELECTED;
    }

    @Override
    public boolean isIncluded(Object change) {
      ExclusionState trackerState = getExclusionState(change);
      return trackerState != ExclusionState.ALL_EXCLUDED;
    }

    @NotNull
    @Override
    public Set<Object> getIncludedSet() {
      HashSet<Object> set = new HashSet<>(myIncludedChanges);

      for (Map.Entry<Change, ExclusionState> entry : myTrackerExclusionStates.entrySet()) {
        Change change = entry.getKey();
        ExclusionState trackerState = entry.getValue();

        if (trackerState == ExclusionState.ALL_EXCLUDED) {
          set.remove(change);
        }
        else {
          set.add(change);
        }
      }

      return set;
    }

    @Override
    public void setIncludedChanges(@NotNull Collection<?> changes) {
      HashSet<Object> set = new HashSet<>(changes);
      getTrackersStream().forEach(pair -> {
        Change change = pair.first;
        PartialLocalLineStatusTracker tracker = pair.second;
        tracker.setExcludedFromCommit(!set.contains(change));
      });

      myIncludedChanges.clear();
      myIncludedChanges.addAll(changes);

      updateExclusionStates();
    }

    @Override
    public void includeChanges(Collection<?> changes) {
      for (Object change : changes) {
        if (change instanceof Change) {
          PartialLocalLineStatusTracker tracker = PartialChangesUtil.getPartialTracker(myProject, (Change)change);
          if (tracker != null) {
            tracker.setExcludedFromCommit(false);
          }
        }
      }

      myIncludedChanges.addAll(changes);

      updateExclusionStates();
    }

    @Override
    public void excludeChanges(Collection<?> changes) {
      for (Object change : changes) {
        if (change instanceof Change) {
          PartialLocalLineStatusTracker tracker = PartialChangesUtil.getPartialTracker(myProject, (Change)change);
          if (tracker != null) {
            tracker.setExcludedFromCommit(true);
          }
        }
      }

      myIncludedChanges.removeAll(changes);

      updateExclusionStates();
    }

    @Override
    protected void toggleChanges(Collection<?> changes) {
      boolean hasExcluded = false;
      for (Object value : changes) {
        ExclusionState exclusionState = getExclusionState(value);
        if (exclusionState != ExclusionState.ALL_INCLUDED) {
          hasExcluded = true;
          break;
        }
      }

      if (hasExcluded) {
        includeChanges(changes);
      }
      else {
        excludeChanges(changes);
      }
    }


    @NotNull
    private ExclusionState getExclusionState(@NotNull Object change) {
      //noinspection SuspiciousMethodCalls
      ExclusionState exclusionState = myTrackerExclusionStates.get(change);
      if (exclusionState != null) return exclusionState;
      return myIncludedChanges.contains(change) ? ExclusionState.ALL_INCLUDED : ExclusionState.ALL_EXCLUDED;
    }

    private void updateExclusionStates() {
      myTrackerExclusionStates.clear();

      getTrackersStream().forEach(pair -> {
        Change change = pair.first;
        PartialLocalLineStatusTracker tracker = pair.second;
        ExclusionState state = tracker.getExcludedFromCommitState(myChangeList.getId());
        if (state != ExclusionState.NO_CHANGES) myTrackerExclusionStates.put(change, state);
      });

      notifyInclusionListener();
      invalidateNodeSizes();
      repaint();
    }

    @Nullable
    private Change findChangeFor(@NotNull LineStatusTracker<?> tracker) {
      return VcsTreeModelData.all(this).userObjectsStream(Change.class).filter(change -> {
        return tracker.getVirtualFile().equals(PartialChangesUtil.getVirtualFile(change));
      }).findFirst().orElse(null);
    }

    @NotNull
    private Stream<Pair<Change, PartialLocalLineStatusTracker>> getTrackersStream() {
      return VcsTreeModelData.all(this).userObjectsStream(Change.class).map(change -> {
        PartialLocalLineStatusTracker tracker = PartialChangesUtil.getPartialTracker(myProject, change);
        if (tracker != null) {
          return Pair.create(change, tracker);
        }
        else {
          return null;
        }
      }).filter(Objects::nonNull);
    }

    private void scheduleExclusionStatesUpdate() {
      myUpdateQueue.queue(new Update("updateExcludedFromCommit") {
        @Override
        public void run() {
          updateExclusionStates();
        }
      });
    }

    private void invalidateNodeSizes() {
      TreeUI ui = getUI();
      if (ui instanceof WideSelectionTreeUI) {
        ((WideSelectionTreeUI)ui).invalidateNodeSizes();
      }
    }
  }
}