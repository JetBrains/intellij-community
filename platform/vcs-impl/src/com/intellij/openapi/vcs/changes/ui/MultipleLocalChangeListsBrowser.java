// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.ex.ThreeStateCheckboxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.RollbackDialogAction;
import com.intellij.openapi.vcs.changes.actions.diff.UnversionedDiffRequestProducer;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ThreeStateCheckBox.State;
import com.intellij.util.ui.update.DisposableUpdate;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.vcs.commit.PartialCommitChangeNodeDecorator;
import com.intellij.vcs.commit.PartialCommitInclusionModel;
import com.intellij.vcs.commit.SingleChangeListCommitWorkflowUi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG;
import static com.intellij.openapi.vcs.changes.ui.ChangesListView.EXACTLY_SELECTED_FILES_DATA_KEY;
import static com.intellij.openapi.vcs.changes.ui.ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY;
import static com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT;

class MultipleLocalChangeListsBrowser extends CommitDialogChangesBrowser implements Disposable {
  @NotNull private final MergingUpdateQueue myUpdateQueue =
    new MergingUpdateQueue("MultipleLocalChangeListsBrowser", 300, true, ANY_COMPONENT, this);

  private final boolean myEnableUnversioned;
  private final boolean myEnablePartialCommit;
  @Nullable private Supplier<? extends JComponent> myBottomDiffComponent;

  @NotNull private final ChangeListChooser myChangeListChooser;
  @NotNull private final DeleteProvider myDeleteProvider = new VirtualFileDeleteProvider();

  @NotNull private final PartialCommitInclusionModel myInclusionModel;
  @NotNull private LocalChangeList myChangeList;
  private final List<Change> myChanges = new ArrayList<>();
  private final List<FilePath> myUnversioned = new ArrayList<>();

  @Nullable private SingleChangeListCommitWorkflowUi.ChangeListListener mySelectedListChangeListener;
  private final RollbackDialogAction myRollbackDialogAction;

  MultipleLocalChangeListsBrowser(@NotNull Project project,
                                  boolean showCheckboxes,
                                  boolean highlightProblems,
                                  boolean enableUnversioned,
                                  boolean enablePartialCommit) {
    super(project, showCheckboxes, highlightProblems);
    myEnableUnversioned = enableUnversioned;
    myEnablePartialCommit = enablePartialCommit;

    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    myChangeList = changeListManager.getDefaultChangeList();
    myChangeListChooser = new ChangeListChooser();

    myRollbackDialogAction = new RollbackDialogAction();
    myRollbackDialogAction.registerCustomShortcutSet(this, null);

    if (!changeListManager.areChangeListsEnabled()) {
      myChangeListChooser.setVisible(false);
    }
    else if (Registry.is("vcs.skip.single.default.changelist")) {
      List<LocalChangeList> allChangeLists = changeListManager.getChangeLists();
      if (allChangeLists.size() == 1 && allChangeLists.get(0).isBlank()) {
        myChangeListChooser.setVisible(false);
      }
    }

    myInclusionModel = new PartialCommitInclusionModel(myProject);
    Disposer.register(this, myInclusionModel);
    getViewer().setInclusionModel(myInclusionModel);

    changeListManager.addChangeListListener(new MyChangeListListener(), this);
    init();

    updateDisplayedChangeLists();
    updateSelectedChangeList(myChangeList);

    project.getMessageBus().connect(this)
      .subscribe(VcsManagedFilesHolder.TOPIC, () -> {
        ApplicationManager.getApplication().invokeLater(() -> {
          myViewer.repaint();
        });
      });
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
    AnAction rollbackGroup = createRollbackGroup(true);
    return ContainerUtil.append(
      super.createToolbarActions(),
      rollbackGroup,
      ActionManager.getInstance().getAction("ChangesView.Refresh"),
      ActionManager.getInstance().getAction("Vcs.CheckinProjectToolbar")
    );
  }

  private AnAction createRollbackGroup(boolean popup) {
    List<? extends AnAction> rollbackActions = createAdditionalRollbackActions();
    if (rollbackActions.isEmpty()) {
      return myRollbackDialogAction;
    }
    DefaultActionGroup group = new DefaultActionGroup(myRollbackDialogAction);
    group.addAll(rollbackActions);
    ActionUtil.copyFrom(group, IdeActions.CHANGES_VIEW_ROLLBACK);
    group.setPopup(popup);
    return group;
  }

  protected List<? extends AnAction> createAdditionalRollbackActions() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  protected List<AnAction> createPopupMenuActions() {
    List<AnAction> result = new ArrayList<>(super.createPopupMenuActions());

    result.add(ActionManager.getInstance().getAction("ChangesView.Refresh"));

    if (myEnableUnversioned) {
      result.add(UnversionedViewDialog.registerUnversionedPopupGroup(myViewer));
    }
    else {
      // avoid duplicated actions on toolbar
      result.add(ActionManager.getInstance().getAction(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST));
    }

    EmptyAction.registerWithShortcutSet(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST, CommonShortcuts.getMove(), myViewer);

    result.add(createRollbackGroup(false));

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
    if (myBottomDiffComponent != null) {
      chain.putUserData(DiffUserDataKeysEx.BOTTOM_PANEL, myBottomDiffComponent.get());
    }
    chain.putUserData(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, myEnablePartialCommit);
    chain.putUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true);
  }


  public void setBottomDiffComponent(@Nullable Supplier<? extends JComponent> value) {
    myBottomDiffComponent = value;
  }

  public void setSelectedListChangeListener(@Nullable SingleChangeListCommitWorkflowUi.ChangeListListener runnable) {
    mySelectedListChangeListener = runnable;
  }

  @NotNull
  @Override
  public LocalChangeList getSelectedChangeList() {
    return myChangeList;
  }

  public void setSelectedChangeList(@NotNull LocalChangeList list) {
    myChangeListChooser.setSelectedChangeList(list);
  }

  private void updateSelectedChangeList(@NotNull LocalChangeList newChangeList) {
    LocalChangeList oldChangeList = myChangeList;
    boolean isListChanged = !oldChangeList.getId().equals(newChangeList.getId());
    if (isListChanged) {
      LineStatusTrackerManager.getInstanceImpl(myProject).resetExcludedFromCommitMarkers();
    }
    myChangeList = newChangeList;
    myChangeListChooser.setToolTipText(newChangeList.getName());
    updateDisplayedChanges();
    if (isListChanged && mySelectedListChangeListener != null) mySelectedListChangeListener.changeListChanged(oldChangeList, newChangeList);

    myInclusionModel.setChangeLists(List.of(newChangeList));
  }

  @Override
  public void updateDisplayedChangeLists() {
    List<LocalChangeList> changeLists = ChangeListManager.getInstance(myProject).getChangeLists();
    myChangeListChooser.setAvailableLists(changeLists);
  }

  public void updateDisplayedChanges() {
    myChanges.clear();
    myUnversioned.clear();

    myChanges.addAll(myChangeList.getChanges());

    if (myEnableUnversioned) {
      List<FilePath> unversioned = ChangeListManager.getInstance(myProject).getUnversionedFilesPaths();
      myUnversioned.addAll(unversioned);
    }

    myViewer.rebuildTree();
  }

  @NotNull
  @Override
  protected DefaultTreeModel buildTreeModel() {
    PartialCommitChangeNodeDecorator decorator =
      new PartialCommitChangeNodeDecorator(myProject, RemoteRevisionsCache.getInstance(myProject).getChangesNodeDecorator());
    TreeModelBuilder builder = new TreeModelBuilder(myProject, getGrouping());
    builder.setChanges(myChanges, decorator);
    builder.setUnversioned(myUnversioned);

    myViewer.getEmptyText()
      .setText(DiffBundle.message("diff.count.differences.status.text", 0));

    return builder.build();
  }

  @Nullable
  @Override
  protected ChangeDiffRequestChain.Producer getDiffRequestProducer(@NotNull Object entry) {
    if (entry instanceof FilePath) {
      return UnversionedDiffRequestProducer.create(myProject, (FilePath)entry);
    }
    return super.getDiffRequestProducer(entry);
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (UNVERSIONED_FILE_PATHS_DATA_KEY.is(dataId)) {
      return VcsTreeModelData.selectedUnderTag(myViewer, UNVERSIONED_FILES_TAG)
        .iterateUserObjects(FilePath.class);
    }
    else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return myDeleteProvider;
    }
    else if (VcsDataKeys.CHANGE_LISTS.is(dataId)) {
      return new ChangeList[]{myChangeList};
    }
    else if (EXACTLY_SELECTED_FILES_DATA_KEY.is(dataId)) {
      return VcsTreeModelData.mapToExactVirtualFile(VcsTreeModelData.exactlySelected(myViewer));
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
  public List<FilePath> getDisplayedUnversionedFiles() {
    if (!myEnableUnversioned) return Collections.emptyList();

    VcsTreeModelData treeModelData = VcsTreeModelData.allUnderTag(myViewer, ChangesBrowserNode.UNVERSIONED_FILES_TAG);
    if (containsCollapsedUnversionedNode(treeModelData)) {
      return List.copyOf(myUnversioned);
    }

    return treeModelData.userObjects(FilePath.class);
  }

  @NotNull
  @Override
  public List<FilePath> getSelectedUnversionedFiles() {
    if (!myEnableUnversioned) return Collections.emptyList();

    VcsTreeModelData treeModelData = VcsTreeModelData.selectedUnderTag(myViewer, ChangesBrowserNode.UNVERSIONED_FILES_TAG);
    if (containsCollapsedUnversionedNode(treeModelData)) {
      return List.copyOf(myUnversioned);
    }

    return treeModelData.userObjects(FilePath.class);
  }

  @NotNull
  @Override
  public List<FilePath> getIncludedUnversionedFiles() {
    if (!myEnableUnversioned) return Collections.emptyList();

    VcsTreeModelData treeModelData = VcsTreeModelData.includedUnderTag(myViewer, ChangesBrowserNode.UNVERSIONED_FILES_TAG);
    if (containsCollapsedUnversionedNode(treeModelData)) {
      return List.copyOf(myUnversioned);
    }

    return treeModelData.userObjects(FilePath.class);
  }

  private static boolean containsCollapsedUnversionedNode(@NotNull VcsTreeModelData treeModelData) {
    ChangesBrowserUnversionedFilesNode unversionedFilesNode = treeModelData.iterateNodes()
      .filter(ChangesBrowserUnversionedFilesNode.class)
      .first();
    if (unversionedFilesNode == null) return false;

    return unversionedFilesNode.isManyFiles();
  }

  private class ChangeListChooser extends JPanel {
    private final static int MAX_NAME_LEN = 35;
    @NotNull private final ComboBox<LocalChangeList> myChooser = new ComboBox<>();

    ChangeListChooser() {
      myChooser.setEditable(false);
      myChooser.setRenderer(new ColoredListCellRenderer<>() {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends LocalChangeList> list, LocalChangeList value,
                                             int index, boolean selected, boolean hasFocus) {
          String name = StringUtil.shortenTextWithEllipsis(value.getName().trim(), MAX_NAME_LEN, 0);
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


  private class ToggleChangeDiffAction extends ThreeStateCheckboxAction implements CustomComponentAction, DumbAware {
    ToggleChangeDiffAction() {
      super(VcsBundle.messagePointer("commit.dialog.include.action.name"));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @NotNull
    @Override
    public State isSelected(AnActionEvent e) {
      Object object = getUserObject(e);
      if (object == null) return State.NOT_SELECTED;
      return myInclusionModel.getInclusionState(object);
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
    private Object getUserObject(@NotNull AnActionEvent e) {
      Object object = e.getData(VcsDataKeys.CURRENT_CHANGE);
      if (object == null) object = e.getData(VcsDataKeys.CURRENT_UNVERSIONED);
      return object;
    }
  }


  private class MyChangeListListener extends ChangeListAdapter {
    @Override
    public void changeListsChanged() {
      myUpdateQueue.queue(DisposableUpdate.createDisposable(myUpdateQueue, "updateChangeLists", () -> {
        updateDisplayedChangeLists();
      }));
    }
  }
}