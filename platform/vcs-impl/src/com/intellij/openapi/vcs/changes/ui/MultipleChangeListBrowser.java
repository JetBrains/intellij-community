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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.MoveChangesToAnotherListAction;
import com.intellij.openapi.vcs.changes.actions.RollbackDialogAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColoredListCellRendererWrapper;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static com.intellij.openapi.util.text.StringUtil.shortenTextWithEllipsis;
import static java.util.stream.Collectors.toList;

public class MultipleChangeListBrowser extends ChangesBrowserBase<Object> {

  @NotNull private final ChangeListChooser myChangeListChooser;
  @NotNull final ChangeListListener myChangeListListener = new MyChangeListListener();
  @NotNull private final EventDispatcher<SelectedListChangeListener> myDispatcher =
    EventDispatcher.create(SelectedListChangeListener.class);
  @Nullable private final Runnable myRebuildListListener;
  @NotNull private final VcsConfiguration myVcsConfiguration;
  private final boolean myUnversionedFilesEnabled;
  private boolean myInRebuildList;
  private AnAction myMoveActionWithCustomShortcut;

  // todo terrible constructor
  public MultipleChangeListBrowser(@NotNull Project project,
                                   @NotNull List<? extends ChangeList> changeLists,
                                   @NotNull List<Object> changes,
                                   @Nullable ChangeList initialListSelection,
                                   @Nullable Runnable rebuildListListener,
                                   @Nullable Runnable inclusionListener,
                                   boolean unversionedFilesEnabled) {
    super(project, changes, true, true, inclusionListener, ChangesBrowser.MyUseCase.LOCAL_CHANGES, null, Object.class);
    myRebuildListListener = rebuildListListener;
    myVcsConfiguration = ObjectUtils.assertNotNull(VcsConfiguration.getInstance(myProject));
    myUnversionedFilesEnabled = unversionedFilesEnabled;

    init();
    setInitialSelection(changeLists, changes, initialListSelection);

    myChangeListChooser = new ChangeListChooser();
    myHeaderPanel.add(myChangeListChooser, BorderLayout.EAST);
    ChangeListManager.getInstance(myProject).addChangeListListener(myChangeListListener);

    setupRebuildListForActions();
    rebuildList();
  }

  private void setupRebuildListForActions() {
    ActionManager actionManager = ActionManager.getInstance();
    final AnAction moveAction = actionManager.getAction(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST);
    final AnAction deleteAction = actionManager.getAction("ChangesView.DeleteUnversioned.From.Dialog");

    actionManager.addAnActionListener(new AnActionListener.Adapter() {
      @Override
      public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (moveAction.equals(action) || myMoveActionWithCustomShortcut != null && myMoveActionWithCustomShortcut.equals(action)) {
          rebuildList();
        }
        else if (deleteAction.equals(action)) {
          UnversionedViewDialog.refreshChanges(myProject, MultipleChangeListBrowser.this);
        }
      }
    }, this);
  }

  private boolean isShowUnversioned() {
    return myUnversionedFilesEnabled && myVcsConfiguration.SHOW_UNVERSIONED_FILES_WHILE_COMMIT;
  }

  private void setShowUnversioned(boolean value) {
    myVcsConfiguration.SHOW_UNVERSIONED_FILES_WHILE_COMMIT = value;
    rebuildList();
  }

  @Override
  protected void setInitialSelection(@NotNull List<? extends ChangeList> changeLists,
                                     @NotNull List<Object> changes,
                                     @Nullable ChangeList initialListSelection) {
    mySelectedChangeList = initialListSelection;

    for (ChangeList list : changeLists) {
      if (list instanceof LocalChangeList) {
        if (initialListSelection == null && ContainerUtil.intersects(list.getChanges(), changes)) {
          mySelectedChangeList = list;
        }
      }
    }

    if (mySelectedChangeList == null) {
      mySelectedChangeList = ObjectUtils.chooseNotNull(findDefaultList(changeLists), ContainerUtil.getFirstItem(changeLists));
    }
  }

  @Override
  public void dispose() {
    ChangeListManager.getInstance(myProject).removeChangeListListener(myChangeListListener);
  }

  public void addSelectedListChangeListener(@NotNull SelectedListChangeListener listener) {
    myDispatcher.addListener(listener);
  }

  private void setSelectedList(@Nullable ChangeList list) {
    mySelectedChangeList = list;
    rebuildList();
    myDispatcher.getMulticaster().selectedListChanged();
  }

  @Override
  public void rebuildList() {
    if (myInRebuildList) return;
    try {
      myInRebuildList = true;

      updateListsInChooser();
      super.rebuildList();
      if (myRebuildListListener != null) {
        myRebuildListListener.run();
      }
    } finally {
      myInRebuildList = false;
    }
  }

  @Override
  @NotNull
  public List<Change> getCurrentIncludedChanges() {
    Collection<Object> includedObjects = myViewer.getIncludedChanges();

    return mySelectedChangeList.getChanges().stream().filter(includedObjects::contains).collect(toList());
  }

  @NotNull
  @Override
  protected DefaultTreeModel buildTreeModel(@NotNull List<Object> objects,
                                            @Nullable ChangeNodeDecorator changeNodeDecorator,
                                            boolean showFlatten) {
    ChangeListManagerImpl manager = ChangeListManagerImpl.getInstanceImpl(myProject);
    TreeModelBuilder builder = new TreeModelBuilder(myProject, showFlatten);
    List<VirtualFile> unversionedFiles = manager.getUnversionedFiles();

    builder.setChanges(findChanges(objects), changeNodeDecorator);
    if (isShowUnversioned()) {
      builder.setUnversioned(unversionedFiles);
    }
    if (myUnversionedFilesEnabled) {
      if (!isShowUnversioned() && !unversionedFiles.isEmpty()) {
        myViewer.getEmptyText()
          .setText("Unversioned files available. ")
          .appendText("Show", SimpleTextAttributes.LINK_ATTRIBUTES, e -> setShowUnversioned(true));
      }
      else {
        myViewer.getEmptyText().setText(capitalize(DiffBundle.message("diff.count.differences.status.text", 0)));
      }
    }

    return builder.build();
  }

  @NotNull
  @Override
  protected List<Object> getSelectedObjects(@NotNull ChangesBrowserNode<?> node) {
    List<Object> result = ContainerUtil.newArrayList();

    result.addAll(node.getAllChangesUnder());
    if (isShowUnversioned() && isUnderUnversioned(node)) {
      result.addAll(node.getAllFilesUnder());
    }

    return result;
  }

  @Nullable
  @Override
  protected Object getLeadSelectedObject(@NotNull ChangesBrowserNode<?> node) {
    Object result = null;
    Object userObject = node.getUserObject();

    if (userObject instanceof Change || isShowUnversioned() && isUnderUnversioned(node) && userObject instanceof VirtualFile) {
      result = userObject;
    }

    return result;
  }

  @NotNull
  @Override
  public List<Object> getCurrentDisplayedObjects() {
    //noinspection unchecked
    return (List)getCurrentDisplayedChanges();
  }

  @NotNull
  @Override
  public List<VirtualFile> getIncludedUnversionedFiles() {
    return isShowUnversioned()
           ? ContainerUtil.findAll(myViewer.getIncludedChanges(), VirtualFile.class)
           : Collections.emptyList();
  }

  @Override
  public int getUnversionedFilesCount() {
    int result = 0;

    if (isShowUnversioned()) {
      ChangesBrowserUnversionedFilesNode node = findUnversionedFilesNode();

      if (node != null) {
        result = node.getFileCount();
      }
    }

    return result;
  }

  @Nullable
  private ChangesBrowserUnversionedFilesNode findUnversionedFilesNode() {
    //noinspection unchecked
    Enumeration<ChangesBrowserNode> nodes = myViewer.getRoot().breadthFirstEnumeration();

    return ContainerUtil.findInstance(ContainerUtil.iterate(nodes), ChangesBrowserUnversionedFilesNode.class);
  }

  @NotNull
  @Override
  public List<Change> getSelectedChanges() {
    Set<Change> changes = ContainerUtil.newLinkedHashSet();
    TreePath[] paths = myViewer.getSelectionPaths();

    if (paths != null) {
      for (TreePath path : paths) {
        ChangesBrowserNode<?> node = (ChangesBrowserNode)path.getLastPathComponent();
        changes.addAll(node.getAllChangesUnder());
      }
    }

    return ContainerUtil.newArrayList(changes);
  }

  @NotNull
  @Override
  public List<Change> getAllChanges() {
    return myViewer.getRoot().getAllChangesUnder();
  }

  @Override
  protected void buildToolBar(@NotNull DefaultActionGroup toolBarGroup) {
    super.buildToolBar(toolBarGroup);

    toolBarGroup.add(new AnAction("Refresh Changes", null, AllIcons.Actions.Refresh) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        rebuildList();
      }
    });
    if (myUnversionedFilesEnabled) {
      toolBarGroup.add(new ShowHideUnversionedFilesAction());
      toolBarGroup.add(UnversionedViewDialog.getUnversionedActionGroup());
    }
    else {
      toolBarGroup.add(ActionManager.getInstance().getAction(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST));
    }
    UnversionedViewDialog.registerUnversionedActionsShortcuts(DataManager.getInstance().getDataContext(this), myViewer);
    // We do not add "Delete" key shortcut for deleting unversioned files as this shortcut is already used to uncheck
    // checkboxes in the tree.
    myMoveActionWithCustomShortcut =
      EmptyAction.registerWithShortcutSet(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST, CommonShortcuts.getMove(), myViewer);

    RollbackDialogAction rollback = new RollbackDialogAction();
    rollback.registerCustomShortcutSet(this, null);
    toolBarGroup.add(rollback);

    EditSourceForDialogAction editSourceAction = new EditSourceForDialogAction(this);
    editSourceAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), this);
    toolBarGroup.add(editSourceAction);

    toolBarGroup.add(ActionManager.getInstance().getAction("Vcs.CheckinProjectToolbar"));
  }

  @Override
  protected void afterDiffRefresh() {
    rebuildList();
    setDataIsDirty(false);
    ApplicationManager.getApplication().invokeLater(
      () -> IdeFocusManager.findInstance().requestFocus(myViewer.getPreferredFocusedComponent(), true));
  }

  @Override
  protected List<AnAction> createDiffActions() {
    List<AnAction> actions = super.createDiffActions();
    actions.add(new MoveAction());
    return actions;
  }

  private void updateListsInChooser() {
    Runnable runnable = () -> myChangeListChooser.updateLists(ChangeListManager.getInstance(myProject).getChangeListsCopy());
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable, ModalityState.stateForComponent(this));
    }
  }

  @Nullable
  private static ChangeList findDefaultList(@NotNull List<? extends ChangeList> lists) {
    return ContainerUtil.find(lists, (Condition<ChangeList>)list -> list instanceof LocalChangeList && ((LocalChangeList)list).isDefault());
  }

  private class ChangeListChooser extends JPanel {
    private final static int MAX_LEN = 35;
    @NotNull private final ComboBox myChooser;

    public ChangeListChooser() {
      super(new BorderLayout(4, 2));
      myChooser = new ComboBox();
      //noinspection unchecked
      myChooser.setRenderer(new ColoredListCellRendererWrapper<LocalChangeList>() {
        @Override
        protected void doCustomize(JList list, LocalChangeList value, int index, boolean selected, boolean hasFocus) {
          if (value != null) {
            String name = shortenTextWithEllipsis(value.getName().trim(), MAX_LEN, 0);

            append(name, value.isDefault() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        }
      });

      myChooser.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            final LocalChangeList changeList = (LocalChangeList)myChooser.getSelectedItem();
            setSelectedList(changeList);
            myChooser.setToolTipText(changeList == null ? "" : (changeList.getName()));
          }
        }
      });

      myChooser.setEditable(false);
      add(myChooser, BorderLayout.CENTER);

      JLabel label = new JLabel(VcsBundle.message("commit.dialog.changelist.label"));
      label.setLabelFor(myChooser);
      add(label, BorderLayout.WEST);
    }

    public void updateLists(@NotNull List<? extends ChangeList> lists) {
      //noinspection unchecked
      myChooser.setModel(new DefaultComboBoxModel(lists.toArray()));
      myChooser.setEnabled(lists.size() > 1);
      if (lists.contains(mySelectedChangeList)) {
        myChooser.setSelectedItem(mySelectedChangeList);
      } else {
        if (myChooser.getItemCount() > 0) {
          myChooser.setSelectedIndex(0);
        }
      }
      mySelectedChangeList = (ChangeList) myChooser.getSelectedItem();
    }
  }

  private class MyChangeListListener extends ChangeListAdapter {
    public void changeListAdded(ChangeList list) {
      updateListsInChooser();
    }
  }

  private class ShowHideUnversionedFilesAction extends ToggleAction {

    private ShowHideUnversionedFilesAction() {
      super("Show Unversioned Files", null, AllIcons.Vcs.ShowUnversionedFiles);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);

      e.getPresentation().setEnabledAndVisible(e.isFromActionToolbar());
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myVcsConfiguration.SHOW_UNVERSIONED_FILES_WHILE_COMMIT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      setShowUnversioned(state);
    }
  }

  private class MoveAction extends MoveChangesToAnotherListAction {
    @Override
    protected boolean isEnabled(@NotNull AnActionEvent e) {
      Change change = e.getData(VcsDataKeys.CURRENT_CHANGE);
      if (change == null) return false;
      return super.isEnabled(e);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Change change = e.getRequiredData(VcsDataKeys.CURRENT_CHANGE);
      askAndMove(myProject, Collections.singletonList(change), Collections.emptyList());
    }
  }
}
