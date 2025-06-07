// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.ex.ThreeStateCheckboxAction;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.RollbackDialogAction;
import com.intellij.openapi.vcs.changes.actions.diff.UnversionedDiffRequestProducer;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ThreeStateCheckBox.State;
import com.intellij.util.ui.update.DisposableUpdate;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.vcs.commit.PartialCommitChangeNodeDecorator;
import com.intellij.vcs.commit.PartialCommitInclusionModel;
import com.intellij.vcs.commit.SingleChangeListCommitWorkflowUi;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG;
import static com.intellij.openapi.vcs.changes.ui.ChangesListView.EXACTLY_SELECTED_FILES_DATA_KEY;
import static com.intellij.openapi.vcs.changes.ui.ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY;
import static com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT;

class MultipleLocalChangeListsBrowser extends CommitDialogChangesBrowser implements Disposable {
  private final @NotNull MergingUpdateQueue myUpdateQueue =
    new MergingUpdateQueue("MultipleLocalChangeListsBrowser", 300, true, ANY_COMPONENT, this);

  private final Collection<AbstractVcs> myAffectedVcses;
  private final boolean myEnableUnversioned;
  private final boolean myEnablePartialCommit;
  private @Nullable Supplier<? extends JComponent> myBottomDiffComponent;

  private final @NotNull ChangeListChooser myChangeListChooser;
  private final @NotNull DeleteProvider myDeleteProvider = new VirtualFileDeleteProvider();

  private final @NotNull PartialCommitInclusionModel myInclusionModel;
  private @NotNull LocalChangeList myChangeList;
  private List<Change> myChanges = Collections.emptyList();
  private List<FilePath> myUnversioned = Collections.emptyList();

  private @Nullable SingleChangeListCommitWorkflowUi.ChangeListListener mySelectedListChangeListener;

  MultipleLocalChangeListsBrowser(@NotNull Project project,
                                  @NotNull Collection<AbstractVcs> affectedVcses,
                                  boolean showCheckboxes,
                                  boolean highlightProblems,
                                  boolean enableUnversioned,
                                  boolean enablePartialCommit) {
    super(project, showCheckboxes, highlightProblems);
    myAffectedVcses = affectedVcses;
    myEnableUnversioned = enableUnversioned;
    myEnablePartialCommit = enablePartialCommit;

    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    myChangeList = changeListManager.getDefaultChangeList();
    myChangeListChooser = new ChangeListChooser();

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

  @Override
  protected @Nullable JComponent createHeaderPanel() {
    return JBUI.Panels.simplePanel(myChangeListChooser)
      .withBorder(JBUI.Borders.emptyLeft(6));
  }

  @Override
  protected @NotNull List<AnAction> createToolbarActions() {
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
      return new RollbackDialogAction();
    }
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RollbackDialogAction());
    group.addAll(rollbackActions);
    ActionUtil.copyFrom(group, IdeActions.CHANGES_VIEW_ROLLBACK);
    group.setPopup(popup);
    return group;
  }

  private List<? extends AnAction> createAdditionalRollbackActions() {
    List<AnAction> result = new ArrayList<>();
    for (AbstractVcs vcs : myAffectedVcses) {
      RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
      if (rollbackEnvironment == null) continue;
      result.addAll(rollbackEnvironment.createCustomRollbackActions());
    }
    return result;
  }

  @Override
  protected @NotNull List<AnAction> createPopupMenuActions() {
    List<AnAction> result = new ArrayList<>(super.createPopupMenuActions());

    result.add(ActionManager.getInstance().getAction("ChangesView.Refresh"));

    if (myEnableUnversioned) {
      result.add(UnversionedViewDialog.registerUnversionedPopupGroup(myViewer));
    }
    else {
      // avoid duplicated actions on toolbar
      result.add(ActionManager.getInstance().getAction(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST));
    }

    ActionUtil.wrap(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST).registerCustomShortcutSet(CommonShortcuts.getMove(), myViewer);

    result.add(createRollbackGroup(false));

    EditSourceForDialogAction editSourceAction = new EditSourceForDialogAction(this);
    editSourceAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), this);
    result.add(editSourceAction);

    result.add(ActionManager.getInstance().getAction("Vcs.CheckinProjectMenu"));
    return result;
  }

  @Override
  protected @NotNull List<AnAction> createDiffActions() {
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

  @Override
  public @NotNull LocalChangeList getSelectedChangeList() {
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
    myChangeListChooser.setAvailableLists(new ArrayList<>(changeLists));
  }

  public void updateDisplayedChanges() {
    myChanges = new ArrayList<>(myChangeList.getChanges());
    myUnversioned = myEnableUnversioned ? ChangeListManager.getInstance(myProject).getUnversionedFilesPaths()
                                        : Collections.emptyList();

    myViewer.rebuildTree();
  }

  @Override
  protected @NotNull DefaultTreeModel buildTreeModel() {
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307313, EA-736680")) {
      PartialCommitChangeNodeDecorator decorator =
        new PartialCommitChangeNodeDecorator(myProject, RemoteRevisionsCache.getInstance(myProject).getChangesNodeDecorator());
      TreeModelBuilder builder = new TreeModelBuilder(myProject, getGrouping());
      builder.setChanges(myChanges, decorator);
      builder.setUnversioned(myUnversioned);

      myViewer.getEmptyText()
        .setText(DiffBundle.message("diff.count.differences.status.text", 0));

      return builder.build();
    }
  }

  @Override
  protected @Nullable ChangeDiffRequestChain.Producer getDiffRequestProducer(@NotNull Object entry) {
    if (entry instanceof FilePath) {
      return UnversionedDiffRequestProducer.create(myProject, (FilePath)entry);
    }
    return super.getDiffRequestProducer(entry);
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(UNVERSIONED_FILE_PATHS_DATA_KEY,
             VcsTreeModelData.selectedUnderTag(myViewer, UNVERSIONED_FILES_TAG)
               .iterateUserObjects(FilePath.class));
    sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, myDeleteProvider);
    sink.set(VcsDataKeys.CHANGE_LISTS, new ChangeList[]{myChangeList});
    sink.set(EXACTLY_SELECTED_FILES_DATA_KEY,
             VcsTreeModelData.mapToExactVirtualFile(VcsTreeModelData.exactlySelected(myViewer)));
  }

  @Override
  public @NotNull List<Change> getDisplayedChanges() {
    return VcsTreeModelData.all(myViewer).userObjects(Change.class);
  }

  @Override
  public @NotNull List<Change> getSelectedChanges() {
    return VcsTreeModelData.selected(myViewer).userObjects(Change.class);
  }

  @Override
  public @NotNull List<Change> getIncludedChanges() {
    return VcsTreeModelData.included(myViewer).userObjects(Change.class);
  }

  @Override
  public @NotNull List<FilePath> getDisplayedUnversionedFiles() {
    if (!myEnableUnversioned) return Collections.emptyList();

    VcsTreeModelData treeModelData = VcsTreeModelData.allUnderTag(myViewer, ChangesBrowserNode.UNVERSIONED_FILES_TAG);
    if (containsCollapsedUnversionedNode(treeModelData)) {
      return myUnversioned;
    }

    return treeModelData.userObjects(FilePath.class);
  }

  @Override
  public @NotNull List<FilePath> getSelectedUnversionedFiles() {
    if (!myEnableUnversioned) return Collections.emptyList();

    VcsTreeModelData treeModelData = VcsTreeModelData.selectedUnderTag(myViewer, ChangesBrowserNode.UNVERSIONED_FILES_TAG);
    if (containsCollapsedUnversionedNode(treeModelData)) {
      return myUnversioned;
    }

    return treeModelData.userObjects(FilePath.class);
  }

  @Override
  public @NotNull List<FilePath> getIncludedUnversionedFiles() {
    if (!myEnableUnversioned) return Collections.emptyList();

    VcsTreeModelData treeModelData = VcsTreeModelData.includedUnderTag(myViewer, ChangesBrowserNode.UNVERSIONED_FILES_TAG);
    if (containsCollapsedUnversionedNode(treeModelData)) {
      return myUnversioned;
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
    private static final int MAX_NAME_LEN = 35;
    private final @NotNull ComboBox<LocalChangeList> myChooser = new ComboBox<>();

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

    @Contract(mutates = "this,param1")
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

    @Override
    public @NotNull State isSelected(AnActionEvent e) {
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

    private static @Nullable Object getUserObject(@NotNull AnActionEvent e) {
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