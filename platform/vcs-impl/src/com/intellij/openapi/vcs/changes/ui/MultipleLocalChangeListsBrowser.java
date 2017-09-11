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

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.MoveChangesToAnotherListAction;
import com.intellij.openapi.vcs.changes.actions.RollbackDialogAction;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.shortenTextWithEllipsis;
import static com.intellij.openapi.vcs.changes.ui.ChangesListView.UNVERSIONED_FILES_DATA_KEY;

public class MultipleLocalChangeListsBrowser extends ChangesBrowserBase implements Disposable {
  private final boolean myEnableUnversioned;

  @NotNull private final ChangeListChooser myChangeListChooser;
  @NotNull private final DeleteProvider myDeleteProvider = new VirtualFileDeleteProvider();

  private final List<Change> myChanges = new ArrayList<>();
  private final List<VirtualFile> myUnversioned = new ArrayList<>();
  private boolean myHasHiddenUnversioned;

  @NotNull private LocalChangeList myChangeList;

  public MultipleLocalChangeListsBrowser(@NotNull Project project,
                                         boolean showCheckboxes,
                                         boolean highlightProblems,
                                         boolean enableUnversioned) {
    super(project, showCheckboxes, highlightProblems);
    myEnableUnversioned = enableUnversioned;

    myChangeList = ChangeListManager.getInstance(project).getDefaultChangeList();
    myChangeListChooser = new ChangeListChooser();

    installChangeListListener();

    init();

    updateDisplayedChangeLists();
    setSelectedChangeList(myChangeList);
  }

  @Override
  public void dispose() {
  }

  private void installChangeListListener() {
    ChangeListAdapter changeListListener = new MyChangeListListener();
    ChangeListManager.getInstance(myProject).addChangeListListener(changeListListener);
    Disposer.register(this, () -> ChangeListManager.getInstance(myProject).removeChangeListListener(changeListListener));
  }


  @Nullable
  @Override
  protected JComponent createHeaderPanel() {
    return myChangeListChooser;
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> result = new ArrayList<>();
    result.addAll(super.createToolbarActions());

    result.add(new MyRefreshAction());

    if (myEnableUnversioned) {
      result.add(new ShowHideUnversionedFilesAction());

      // TODO: do not access DataContext
      // We do not add "Delete" key shortcut for deleting unversioned files as this shortcut is already used to uncheck checkboxes in the tree.
      result.add(UnversionedViewDialog.getUnversionedActionGroup());
      UnversionedViewDialog.registerUnversionedActionsShortcuts(DataManager.getInstance().getDataContext(this), myViewer);
    }
    else {
      // avoid duplicated actions on toolbar
      result.add(ActionManager.getInstance().getAction(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST));
      EmptyAction.registerWithShortcutSet(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST, CommonShortcuts.getMove(), myViewer);
    }

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


  private boolean isShowUnversioned() {
    return myEnableUnversioned && VcsConfiguration.getInstance(myProject).SHOW_UNVERSIONED_FILES_WHILE_COMMIT;
  }

  private void setShowUnversioned(boolean value) {
    VcsConfiguration.getInstance(myProject).SHOW_UNVERSIONED_FILES_WHILE_COMMIT = value;
    updateDisplayedChanges();
  }

  @NotNull
  public LocalChangeList getSelectedChangeList() {
    return myChangeList;
  }

  public void setSelectedChangeList(@NotNull LocalChangeList list) {
    myChangeList = list;
    myChangeListChooser.setToolTipText(list.getName());
    updateDisplayedChanges();
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
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            LocalChangeList changeList = (LocalChangeList)myChooser.getSelectedItem();
            if (changeList != null) {
              setSelectedChangeList(changeList);
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

      setSelectedChangeList(currentList);
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
      return e.getData(VcsDataKeys.CURRENT_CHANGE) != null;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Change change = e.getRequiredData(VcsDataKeys.CURRENT_CHANGE);
      askAndMove(myProject, Collections.singletonList(change), Collections.emptyList());
    }
  }

  private class ToggleChangeDiffAction extends CheckboxAction implements DumbAware {
    public ToggleChangeDiffAction() {
      super(VcsBundle.message("commit.dialog.include.action.name"));
    }

    public boolean isSelected(AnActionEvent e) {
      Change change = e.getData(VcsDataKeys.CURRENT_CHANGE);
      if (change == null) return false;
      return myViewer.isIncluded(change);
    }

    public void setSelected(AnActionEvent e, boolean state) {
      Change change = e.getData(VcsDataKeys.CURRENT_CHANGE);
      if (change == null) return;

      if (state) {
        myViewer.includeChange(change);
      }
      else {
        myViewer.excludeChange(change);
      }
    }
  }

  private class MyRefreshAction extends DumbAwareAction {
    public MyRefreshAction() {
      super("Refresh Changes", null, AllIcons.Actions.Refresh);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      updateDisplayedChangeLists();
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

    public void changeListAdded(ChangeList list) {
      doUpdate();
    }

    @Override
    public void changeListRemoved(ChangeList list) {
      doUpdate();
    }

    @Override
    public void changeListRenamed(ChangeList list, String oldName) {
      doUpdate();
    }

    @Override
    public void changesRemoved(Collection<Change> changes, ChangeList fromList) {
      doUpdate();
    }

    @Override
    public void changesAdded(Collection<Change> changes, ChangeList toList) {
      doUpdate();
    }

    @Override
    public void changesMoved(Collection<Change> changes, ChangeList fromList, ChangeList toList) {
      doUpdate();
    }

    @Override
    public void changeListUpdateDone() {
      doUpdate();
    }
  }
}