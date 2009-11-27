/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 28.11.2006
 * Time: 14:15:18
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.MoveChangesToAnotherListAction;
import com.intellij.openapi.vcs.changes.actions.RollbackDialogAction;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

public class MultipleChangeListBrowser extends ChangesBrowser {
  private final ChangeListChooser myChangeListChooser;
  private final ChangeListListener myChangeListListener = new MyChangeListListener();
  private final boolean myShowingAllChangeLists;
  private final EventDispatcher<SelectedListChangeListener> myDispatcher = EventDispatcher.create(SelectedListChangeListener.class);
  private Collection<Change> myAllChanges;
  private Map<Change, LocalChangeList> myChangeListsMap;

  private final ChangesBrowserExtender myExtender;
  private final Runnable myRebuildListListener;

  public MultipleChangeListBrowser(final Project project, final List<? extends ChangeList> changeLists, final List<Change> changes,
                                   final ChangeList initialListSelection,
                                   final boolean capableOfExcludingChanges,
                                   final boolean highlightProblems, final Runnable rebuildListListener, @Nullable final Runnable inclusionListener) {
    super(project, changeLists, changes, initialListSelection, capableOfExcludingChanges, highlightProblems, inclusionListener, MyUseCase.LOCAL_CHANGES);
    myRebuildListListener = rebuildListListener;

    myChangeListChooser = new ChangeListChooser(changeLists);
    myHeaderPanel.add(myChangeListChooser, BorderLayout.EAST);
    myShowingAllChangeLists = Comparing.haveEqualElements((List<LocalChangeList>) changeLists, ChangeListManager.getInstance(project).getChangeLists());
    ChangeListManager.getInstance(myProject).addChangeListListener(myChangeListListener);

    myExtender = new Extender(project, this);
  }

  @Override
  protected void setInitialSelection(final List<? extends ChangeList> changeLists, final List<Change> changes, final ChangeList initialListSelection) {
    myAllChanges = new ArrayList<Change>();
    mySelectedChangeList = initialListSelection;

    for (ChangeList list : changeLists) {
      if (list instanceof LocalChangeList) {
        myAllChanges.addAll(list.getChanges());
        if (initialListSelection == null) {
          for(Change c: list.getChanges()) {
            if (changes.contains(c)) {
              mySelectedChangeList = list;
              break;
            }
          }
        }
      }
    }

    if (mySelectedChangeList == null) {
      for(ChangeList list: changeLists) {
        if (list instanceof LocalChangeList && ((LocalChangeList) list).isDefault()) {
          mySelectedChangeList = list;
          break;
        }
      }
      if (mySelectedChangeList == null && !changeLists.isEmpty()) {
        mySelectedChangeList = changeLists.get(0);
      }
    }
  }

  @Override
  public void dispose() {
    ChangeListManager.getInstance(myProject).removeChangeListListener(myChangeListListener);
  }

  public Collection<Change> getAllChanges() {
    return myAllChanges;
  }

  public ChangesBrowserExtender getExtender() {
    return myExtender;
  }

  public void addSelectedListChangeListener(SelectedListChangeListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeSelectedListChangeListener(SelectedListChangeListener listener) {
    myDispatcher.removeListener(listener);
  }

  private void setSelectedList(final ChangeList list) {
    mySelectedChangeList = list;
    rebuildList();
    myDispatcher.getMulticaster().selectedListChanged();
  }

  private boolean myInRebuildList;

  @Override
  public void rebuildList() {
    if (myInRebuildList) return;
    try {
      myInRebuildList = true;
      if (myChangesToDisplay == null) {
        // changes set not fixed === local changes
        final ChangeListManager manager = ChangeListManager.getInstance(myProject);
        myChangeListsMap = new HashMap<Change, LocalChangeList>();
        final List<LocalChangeList> lists = manager.getChangeListsCopy();
        Collection<Change> allChanges = new ArrayList<Change>();
        for (LocalChangeList list : lists) {
          final Collection<Change> changes = list.getChanges();
          allChanges.addAll(changes);
          for (Change change : changes) {
            myChangeListsMap.put(change, list);
          }
        }
        myAllChanges = allChanges;
        // refresh selected list also
        updateListsInChooser();
      }

      super.rebuildList();
      if (myRebuildListListener != null) {
        myRebuildListListener.run();
      }
    } finally {
      myInRebuildList = false;
    }
  }

  @Override
  public List<Change> getCurrentDisplayedChanges() {
    if (myChangesToDisplay == null) {
      return sortChanges(filterBySelectedChangeList(myAllChanges));
    }
    return super.getCurrentDisplayedChanges();
  }

  @NotNull
  public List<Change> getCurrentIncludedChanges() {
    return filterBySelectedChangeList(myViewer.getIncludedChanges());
  }

  private List<Change> filterBySelectedChangeList(final Collection<Change> changes) {
    List<Change> filtered = new ArrayList<Change>();
    for (Change change : changes) {
      if (Comparing.equal(getList(change), mySelectedChangeList)) {
        filtered.add(change);
      }
    }
    return filtered;
  }

  private ChangeList getList(final Change change) {
    return myChangeListsMap.get(change);
  }

  @Override
  protected void buildToolBar(final DefaultActionGroup toolBarGroup) {
    super.buildToolBar(toolBarGroup);

    final MoveChangesToAnotherListAction moveAction = new MoveChangesToAnotherListAction() {
      public void actionPerformed(AnActionEvent e) {
        super.actionPerformed(e);
        rebuildList();
      }
    };

    moveAction.registerCustomShortcutSet(CommonShortcuts.getMove(), myViewer);
    toolBarGroup.add(moveAction);
  }

  @Override
  protected List<AnAction> createDiffActions(final Change change) {
    List<AnAction> actions = super.createDiffActions(change);
    actions.add(new MoveAction(change));
    return actions;
  }

  private class ChangeListChooser extends JPanel {
    private final JComboBox myChooser;
    private final static int MAX_LEN = 35;

    public ChangeListChooser(List<? extends ChangeList> lists) {
      super(new BorderLayout());
      myChooser = new JComboBox() {
        public Dimension getMinimumSize() {
          return new Dimension(0, 0);
        }
      };
      myChooser.setRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          final LocalChangeList l = ((LocalChangeList)value);
          if (l != null) {
            String name = l.getName().trim();
            if (name.length() > MAX_LEN) {
              name = name.substring(0, MAX_LEN - 3) + "...";
            }
            append(name, l.isDefault() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
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
      label.setDisplayedMnemonic('l');
      label.setLabelFor(myChooser);
      add(label, BorderLayout.WEST);
    }

    public void updateLists(List<? extends ChangeList> lists) {
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

  private void updateListsInChooser() {
    Runnable runnable = new Runnable() {
      public void run() {
        if (myChangeListChooser != null && myShowingAllChangeLists) {
          myChangeListChooser.updateLists(ChangeListManager.getInstance(myProject).getChangeListsCopy());
        }
      }
    };
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable, ModalityState.stateForComponent(MultipleChangeListBrowser.this));
    }
  }

  private class MoveAction extends MoveChangesToAnotherListAction {
    private final Change myChange;

    public MoveAction(final Change change) {
      myChange = change;
    }

    public void actionPerformed(AnActionEvent e) {
      askAndMove(myProject, new Change[]{myChange}, null);
    }
  }

  private static class Extender implements ChangesBrowserExtender {
    private final Project myProject;
    private final MultipleChangeListBrowser myBrowser;

    private Extender(final Project project, final MultipleChangeListBrowser browser) {
      myProject = project;
      myBrowser = browser;
    }

    public void addToolbarActions(final DialogWrapper dialogWrapper) {
      final Icon icon = IconLoader.getIcon("/vcs/refresh.png");
      if (myBrowser.myChangesToDisplay == null) {
        myBrowser.addToolbarAction(new AnAction("Refresh Changes") {
          @Override
          public void actionPerformed(AnActionEvent e) {
            myBrowser.rebuildList();
          }

          @Override
          public void update(AnActionEvent e) {
            e.getPresentation().setIcon(icon);
          }
        });
      }
      myBrowser.addToolbarAction(new RollbackDialogAction());
      final EditSourceInCommitAction editSourceAction = new EditSourceInCommitAction(dialogWrapper);
      editSourceAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), myBrowser);
      myBrowser.addToolbarAction(editSourceAction);

      myBrowser.addToolbarAction(ActionManager.getInstance().getAction("Vcs.CheckinProjectToolbar"));

      final List<AnAction> actions = AdditionalLocalChangeActionsInstaller.calculateActions(myProject, myBrowser.getAllChanges());
      if (actions != null) {
        for (AnAction action : actions) {
          myBrowser.addToolbarAction(action);
        }
      }
    }

    public void addSelectedListChangeListener(final SelectedListChangeListener listener) {
      myBrowser.addSelectedListChangeListener(listener);
    }

    public List<AbstractVcs> getAffectedVcses() {
      Set<AbstractVcs> result = new HashSet<AbstractVcs>();
      for (Change change : myBrowser.myAllChanges) {
        final AbstractVcs vcs = ChangesUtil.getVcsForChange(change, myBrowser.myProject);
        if (vcs != null) {
          result.add(vcs);
        }
      }
      return new ArrayList<AbstractVcs>(result);
    }

    public List<Change> getCurrentIncludedChanges() {
      return myBrowser.getCurrentIncludedChanges();
    }
  }
}
