/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.MoveChangesToAnotherListAction;
import com.intellij.openapi.vcs.changes.actions.RollbackDialogAction;
import com.intellij.openapi.vcs.changes.actions.diff.UnversionedDiffRequestProducer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
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
import java.util.Optional;

import static com.intellij.openapi.util.text.StringUtil.shortenTextWithEllipsis;
import static com.intellij.openapi.vcs.changes.ui.ChangesListView.UNVERSIONED_FILES_DATA_KEY;

public class MultipleLocalChangeListsBrowser extends CommitDialogChangesBrowser implements Disposable {
  private final boolean myEnableUnversioned;
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
                                         boolean enableUnversioned) {
    super(project, showCheckboxes, highlightProblems);
    myEnableUnversioned = enableUnversioned;

    myChangeList = ChangeListManager.getInstance(project).getDefaultChangeList();
    myChangeListChooser = new ChangeListChooser();

    ChangeListManager.getInstance(myProject).addChangeListListener(new MyChangeListListener(), this);
    init();

    updateDisplayedChangeLists();
    updateSelectedChangeList(myChangeList);
  }


  @Nullable
  @Override
  protected JComponent createHeaderPanel() {
    return myChangeListChooser;
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> result = new ArrayList<>(super.createToolbarActions());

    result.add(ActionManager.getInstance().getAction("ChangesView.Refresh"));

    if (myEnableUnversioned) {
      result.add(new ShowHideUnversionedFilesAction());

      // We do not add "Delete" key shortcut for deleting unversioned files as this shortcut is already used to uncheck checkboxes in the tree.
      result.add(UnversionedViewDialog.getUnversionedActionGroup());
      UnversionedViewDialog.registerUnversionedActionsShortcuts(myViewer);
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

    result.add(ActionManager.getInstance().getAction("Vcs.CheckinProjectToolbar"));
    return result;
  }

  @NotNull
  @Override
  protected List<AnAction> createDiffActions() {
    return ContainerUtil.append(
      super.createDiffActions(),
      new ToggleChangeDiffAction(),
      new MoveChangeDiffAction()
    );
  }

  @Override
  protected void updateDiffContext(@NotNull DiffRequestChain chain) {
    super.updateDiffContext(chain);
    chain.putUserData(DiffUserDataKeysEx.BOTTOM_PANEL, myBottomDiffComponent);
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
    myChangeList = list;
    myChangeListChooser.setToolTipText(list.getName());
    updateDisplayedChanges();
    if (mySelectedListChangeListener != null) mySelectedListChangeListener.run();
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
  protected DefaultTreeModel buildTreeModel(boolean showFlatten) {
    RemoteStatusChangeNodeDecorator decorator = RemoteRevisionsCache.getInstance(myProject).getChangesNodeDecorator();

    TreeModelBuilder builder = new TreeModelBuilder(myProject, showFlatten);
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

  private class MoveChangeDiffAction extends MoveChangesToAnotherListAction {
    @Override
    protected boolean isEnabled(@NotNull AnActionEvent e) {
      return e.getData(VcsDataKeys.CURRENT_CHANGE) != null ||
             e.getData(VcsDataKeys.CURRENT_UNVERSIONED) != null;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Change change = e.getData(VcsDataKeys.CURRENT_CHANGE);
      VirtualFile file = e.getData(VcsDataKeys.CURRENT_UNVERSIONED);
      List<Change> changes = change == null ? Collections.emptyList() : Collections.singletonList(change);
      List<VirtualFile> unversionedFiles = file == null ? Collections.emptyList() : Collections.singletonList(file);
      askAndMove(myProject, changes, unversionedFiles);
    }
  }

  private class ToggleChangeDiffAction extends CheckboxAction implements DumbAware {
    public ToggleChangeDiffAction() {
      super(VcsBundle.message("commit.dialog.include.action.name"));
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      Object object = getUserObject(e);
      if (object == null) return false;
      return myViewer.isIncluded(object);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      Object object = getUserObject(e);
      if (object == null) return;

      if (state) {
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
    @NotNull private final MergingUpdateQueue myUpdateQueue =
      new MergingUpdateQueue("MultipleLocalChangeListsBrowser", 300, true,
                             MultipleLocalChangeListsBrowser.this, MultipleLocalChangeListsBrowser.this);

    private void doUpdate() {
      myUpdateQueue.queue(new Update("update") {
        @Override
        public void run() {
          updateDisplayedChangeLists();
        }
      });
    }

    @Override
    public void changeListsChanged() {
      doUpdate();
    }
  }
}