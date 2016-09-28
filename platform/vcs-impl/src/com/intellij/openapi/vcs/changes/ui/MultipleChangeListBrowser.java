/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.MoveChangesToAnotherListAction;
import com.intellij.openapi.vcs.changes.actions.RollbackDialogAction;
import com.intellij.openapi.vfs.VirtualFile;
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

import static java.util.stream.Collectors.toList;

public class MultipleChangeListBrowser extends ChangesBrowserBase<Object> {

  @NotNull private final ChangeListChooser myChangeListChooser;
  @NotNull final ChangeListListener myChangeListListener = new MyChangeListListener();
  @NotNull private final EventDispatcher<SelectedListChangeListener> myDispatcher =
    EventDispatcher.create(SelectedListChangeListener.class);
  @Nullable private final Runnable myRebuildListListener;
  @NotNull private final VcsConfiguration myVcsConfiguration;
  private final boolean myUnversionedFilesEnabled;
  private Collection<Change> myAllChanges;
  private boolean myInRebuildList;
  private AnAction myMoveActionWithCustomShortcut;

  // todo terrible constructor
  public MultipleChangeListBrowser(@NotNull Project project,
                                   @NotNull List<? extends ChangeList> changeLists,
                                   @NotNull List<Object> changes,
                                   @Nullable ChangeList initialListSelection,
                                   boolean capableOfExcludingChanges,
                                   boolean highlightProblems,
                                   @Nullable Runnable rebuildListListener,
                                   @Nullable Runnable inclusionListener,
                                   boolean unversionedFilesEnabled) {
    super(project, changes, capableOfExcludingChanges, highlightProblems, inclusionListener, ChangesBrowser.MyUseCase.LOCAL_CHANGES, null,
          Object.class);
    myRebuildListListener = rebuildListListener;
    myVcsConfiguration = ObjectUtils.assertNotNull(VcsConfiguration.getInstance(myProject));
    myUnversionedFilesEnabled = unversionedFilesEnabled;

    init();
    setInitialSelection(changeLists, changes, initialListSelection);

    myChangeListChooser = new ChangeListChooser();
    myChangeListChooser.updateLists(changeLists);
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

  @Override
  protected void setInitialSelection(@NotNull List<? extends ChangeList> changeLists,
                                     @NotNull List<Object> changes,
                                     @Nullable ChangeList initialListSelection) {
    myAllChanges = ContainerUtil.newArrayList();
    mySelectedChangeList = initialListSelection;

    for (ChangeList list : changeLists) {
      if (list instanceof LocalChangeList) {
        myAllChanges.addAll(list.getChanges());
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

      myAllChanges = getLocalChanges();
      updateListsInChooser();
      super.rebuildList();
      if (myRebuildListListener != null) {
        myRebuildListListener.run();
      }
    } finally {
      myInRebuildList = false;
    }
  }

  @NotNull
  private Collection<Change> getLocalChanges() {
    Collection<Change> result = ContainerUtil.newArrayList();
    ChangeListManager manager = ChangeListManager.getInstance(myProject);

    for (LocalChangeList list : manager.getChangeListsCopy()) {
      for (Change change : list.getChanges()) {
        result.add(change);
      }
    }

    return result;
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

    builder.setChanges(findChanges(objects), changeNodeDecorator);
    if (isShowUnversioned()) {
      builder.setUnversioned(manager.getUnversionedFiles(), manager.getUnversionedFilesSize());
    }

    return builder.build();
  }

  @NotNull
  @Override
  protected List<Object> getSelectedObjects(@NotNull ChangesBrowserNode<Object> node) {
    List<Object> result = ContainerUtil.newArrayList();

    result.addAll(node.getAllChangesUnder());
    if (isShowUnversioned() && isUnderUnversioned(node)) {
      result.addAll(node.getAllFilesUnder());
    }

    return result;
  }

  @Nullable
  @Override
  protected Object getLeadSelectedObject(@NotNull ChangesBrowserNode node) {
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
           : Collections.<VirtualFile>emptyList();
  }

  @Override
  public int getUnversionedFilesCount() {
    int result = 0;

    if (isShowUnversioned()) {
      ChangesBrowserUnversionedFilesNode node = findUnversionedFilesNode();

      if (node != null) {
        result = node.getUnversionedSize();
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
  @NotNull
  public Set<AbstractVcs> getAffectedVcses() {
    return ChangesUtil.getAffectedVcses(myAllChanges, myProject);
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
  protected List<AnAction> createDiffActions() {
    List<AnAction> actions = super.createDiffActions();
    actions.add(new MoveAction());
    return actions;
  }

  private void updateListsInChooser() {
    Runnable runnable = new Runnable() {
      public void run() {
        myChangeListChooser.updateLists(ChangeListManager.getInstance(myProject).getChangeListsCopy());
      }
    };
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable, ModalityState.stateForComponent(this));
    }
  }

  @Nullable
  private static ChangeList findDefaultList(@NotNull List<? extends ChangeList> lists) {
    return ContainerUtil.find(lists, new Condition<ChangeList>() {
      @Override
      public boolean value(@NotNull ChangeList list) {
        return list instanceof LocalChangeList && ((LocalChangeList)list).isDefault();
      }
    });
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
            String name = StringUtil.shortenTextWithEllipsis(value.getName().trim(), MAX_LEN, 0);

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
      super("Show Unversioned Files", null, AllIcons.Debugger.Disable_value_calculation);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);

      e.getPresentation().setEnabledAndVisible(ActionPlaces.isToolbarPlace(e.getPlace()));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myVcsConfiguration.SHOW_UNVERSIONED_FILES_WHILE_COMMIT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myVcsConfiguration.SHOW_UNVERSIONED_FILES_WHILE_COMMIT = state;
      rebuildList();
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
      askAndMove(myProject, Collections.singletonList(change), Collections.<VirtualFile>emptyList());
    }
  }
}
