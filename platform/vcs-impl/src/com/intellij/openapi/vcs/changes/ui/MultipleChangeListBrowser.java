/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
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

public class MultipleChangeListBrowser extends ChangesBrowserBase<Object> {

  @NotNull private final ChangeListChooser myChangeListChooser;
  @NotNull final ChangeListListener myChangeListListener = new MyChangeListListener();
  private final boolean myShowingAllChangeLists;
  @NotNull private final EventDispatcher<SelectedListChangeListener> myDispatcher =
    EventDispatcher.create(SelectedListChangeListener.class);
  @Nullable private final Runnable myRebuildListListener;
  private Collection<Change> myAllChanges;
  private Map<Change, LocalChangeList> myChangeListsMap;
  private boolean myInRebuildList;

  // todo terrible constructor
  public MultipleChangeListBrowser(Project project,
                                   List<? extends ChangeList> changeLists,
                                   @NotNull List<Object> changes,
                                   ChangeList initialListSelection,
                                   boolean capableOfExcludingChanges,
                                   boolean highlightProblems,
                                   @Nullable Runnable rebuildListListener,
                                   @Nullable Runnable inclusionListener) {
    super(project, changeLists, changes, initialListSelection, capableOfExcludingChanges, highlightProblems, inclusionListener,
          ChangesBrowser.MyUseCase.LOCAL_CHANGES, null, Object.class);
    myRebuildListListener = rebuildListListener;

    myChangeListChooser = new ChangeListChooser(changeLists);
    myHeaderPanel.add(myChangeListChooser, BorderLayout.EAST);
    myShowingAllChangeLists = Comparing.haveEqualElements(changeLists, ChangeListManager.getInstance(project).getChangeLists());
    ChangeListManager.getInstance(myProject).addChangeListListener(myChangeListListener);

    setupRebuildListForActions();
  }

  private void setupRebuildListForActions() {
    ActionManager actionManager = ActionManager.getInstance();
    final AnAction moveAction = actionManager.getAction(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST);

    actionManager.addAnActionListener(new AnActionListener.Adapter() {
      @Override
      public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (moveAction.equals(action)) {
          rebuildList();
        }
      }
    }, this);
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

    myChangeListsMap = ContainerUtil.newHashMap();
    for (LocalChangeList list : manager.getChangeListsCopy()) {
      for (Change change : list.getChanges()) {
        result.add(change);
        myChangeListsMap.put(change, list);
      }
    }

    return result;
  }

  @Override
  @NotNull
  public List<Change> getCurrentIncludedChanges() {
    return filterBySelectedChangeList(findChanges(myViewer.getIncludedChanges()));
  }

  @NotNull
  @Override
  protected DefaultTreeModel buildTreeModel(@NotNull List<Object> objects,
                                            @Nullable ChangeNodeDecorator changeNodeDecorator,
                                            boolean showFlatten) {
    TreeModelBuilder builder = new TreeModelBuilder(myProject, showFlatten);
    return builder.buildModel(findChanges(objects), changeNodeDecorator);
  }

  @NotNull
  @Override
  protected List<Object> getSelectedObjects(@NotNull ChangesBrowserNode<Object> node) {
    //noinspection unchecked
    return (List)node.getAllChangesUnder();
  }

  @Nullable
  @Override
  protected Object getLeadSelectedObject(@NotNull ChangesBrowserNode node) {
    return ObjectUtils.tryCast(node.getUserObject(), Change.class);
  }

  @NotNull
  @Override
  public List<Object> getCurrentDisplayedObjects() {
    //noinspection unchecked
    return (List)getCurrentDisplayedChanges();
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

  @NotNull
  private List<Change> filterBySelectedChangeList(@NotNull Collection<Change> changes) {
    return ContainerUtil.findAll(changes, new Condition<Change>() {
      @Override
      public boolean value(@NotNull Change change) {
        return Comparing.equal(getList(change), mySelectedChangeList);
      }
    });
  }

  @Nullable
  private ChangeList getList(@NotNull Change change) {
    return myChangeListsMap.get(change);
  }

  @Override
  protected void buildToolBar(@NotNull DefaultActionGroup toolBarGroup) {
    super.buildToolBar(toolBarGroup);

    EmptyAction.registerWithShortcutSet(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST, CommonShortcuts.getMove(), myViewer);
    toolBarGroup.add(ActionManager.getInstance().getAction(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST));

    toolBarGroup.add(new AnAction("Refresh Changes", null, AllIcons.Actions.Refresh) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        rebuildList();
      }
    });
    RollbackDialogAction rollback = new RollbackDialogAction();
    EmptyAction.setupAction(rollback, IdeActions.CHANGES_VIEW_ROLLBACK, this);
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
        if (myShowingAllChangeLists) {
          myChangeListChooser.updateLists(ChangeListManager.getInstance(myProject).getChangeListsCopy());
        }
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

    public ChangeListChooser(@NotNull List<? extends ChangeList> lists) {
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

      updateLists(lists);
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
