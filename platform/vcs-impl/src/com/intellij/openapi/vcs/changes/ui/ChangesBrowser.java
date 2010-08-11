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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.ShowDiffUIContext;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class ChangesBrowser extends JPanel implements TypeSafeDataProvider {
  protected final ChangesTreeList<Change> myViewer;
  protected ChangeList mySelectedChangeList;
  protected Collection<Change> myChangesToDisplay;
  protected final Project myProject;
  private final boolean myCapableOfExcludingChanges;
  protected final JPanel myHeaderPanel;
  private DefaultActionGroup myToolBarGroup;
  private ShowDiffAction.DiffExtendUIFactory myDiffExtendUIFactory = new DiffToolbarActionsFactory();
  private String myToggleActionTitle = VcsBundle.message("commit.dialog.include.action.name");

  public static DataKey<ChangesBrowser> DATA_KEY = DataKey.create("com.intellij.openapi.vcs.changes.ui.ChangesBrowser");
  private ShowDiffAction myDiffAction;

  public void setChangesToDisplay(final List<Change> changes) {
    myChangesToDisplay = changes;
    myViewer.setChangesToDisplay(changes);
  }

  public void setDecorator(final ChangeNodeDecorator decorator) {
    myViewer.setChangeDecorator(decorator);
  }

  public ChangesBrowser(final Project project, List<? extends ChangeList> changeLists, final List<Change> changes,
                        ChangeList initialListSelection, final boolean capableOfExcludingChanges, final boolean highlightProblems,
                        @Nullable final Runnable inclusionListener, final MyUseCase useCase) {
    super(new BorderLayout());

    myProject = project;
    myCapableOfExcludingChanges = capableOfExcludingChanges;

    final ChangeNodeDecorator decorator = MyUseCase.LOCAL_CHANGES.equals(useCase) ?
                                          RemoteRevisionsCache.getInstance(myProject).getChangesNodeDecorator() : null;

    myViewer = new ChangesTreeList<Change>(myProject, changes, capableOfExcludingChanges, highlightProblems, inclusionListener, decorator) {
      protected DefaultTreeModel buildTreeModel(final List<Change> changes, ChangeNodeDecorator changeNodeDecorator) {
        TreeModelBuilder builder = new TreeModelBuilder(myProject, false);
        return builder.buildModel(changes, changeNodeDecorator);
      }

      protected List<Change> getSelectedObjects(final ChangesBrowserNode<Change> node) {
        return node.getAllChangesUnder();
      }

      @Nullable
      protected Change getLeadSelectedObject(final ChangesBrowserNode node) {
        final Object o = node.getUserObject();
        if (o instanceof Change) {
          return (Change) o;
        }
        return null;
      }
    };

    myViewer.setDoubleClickHandler(new Runnable() {
      public void run() {
        showDiff();
      }
    });

    setInitialSelection(changeLists, changes, initialListSelection);
    rebuildList();

    add(myViewer, BorderLayout.CENTER);

    myHeaderPanel = new JPanel(new BorderLayout());
    myHeaderPanel.add(createToolbar(), BorderLayout.CENTER);
    add(myHeaderPanel, BorderLayout.NORTH);

    myViewer.installPopupHandler(myToolBarGroup);
  }

  protected void setInitialSelection(final List<? extends ChangeList> changeLists, final List<Change> changes, final ChangeList initialListSelection) {
    mySelectedChangeList = initialListSelection;
  }

  public void dispose() {
  }

  public void addToolbarAction(AnAction action) {
    myToolBarGroup.add(action);
  }

  public void addToolbarActions(ActionGroup group) {
    myToolBarGroup.addSeparator();
    myToolBarGroup.add(group);
  }

  public ShowDiffAction.DiffExtendUIFactory getDiffExtendUIFactory() {
    return myDiffExtendUIFactory;
  }

  public void setDiffExtendUIFactory(final ShowDiffAction.DiffExtendUIFactory diffExtendUIFactory) {
    myDiffExtendUIFactory = diffExtendUIFactory;
  }

  public void setToggleActionTitle(final String toggleActionTitle) {
    myToggleActionTitle = toggleActionTitle;
  }

  public JPanel getHeaderPanel() {
    return myHeaderPanel;
  }

  public ChangesTreeList<Change> getViewer() {
    return myViewer;
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key == VcsDataKeys.CHANGES) {
      final List<Change> list = myViewer.getSelectedChanges();
      sink.put(VcsDataKeys.CHANGES, list.toArray(new Change [list.size()]));
    }
    else if (key == VcsDataKeys.CHANGE_LISTS) {
      sink.put(VcsDataKeys.CHANGE_LISTS, getSelectedChangeLists());
    }
    else if (key == VcsDataKeys.CHANGE_LEAD_SELECTION) {
      final Change highestSelection = myViewer.getHighestLeadSelection();
      sink.put(VcsDataKeys.CHANGE_LEAD_SELECTION, (highestSelection == null) ? new Change[]{} : new Change[] {highestSelection});
    }    else if (key == PlatformDataKeys.VIRTUAL_FILE_ARRAY) {
      sink.put(PlatformDataKeys.VIRTUAL_FILE_ARRAY, getSelectedFiles());
    }
    else if (key == PlatformDataKeys.NAVIGATABLE_ARRAY) {
      sink.put(PlatformDataKeys.NAVIGATABLE_ARRAY, ChangesUtil.getNavigatableArray(myProject, getSelectedFiles()));
    } else if (VcsDataKeys.IO_FILE_ARRAY.equals(key)) {
      sink.put(VcsDataKeys.IO_FILE_ARRAY, getSelectedIoFiles());
    }
    else if (key == DATA_KEY) {
      sink.put(DATA_KEY, this);
    } else if (VcsDataKeys.SELECTED_CHANGES_IN_DETAILS.equals(key)) {
      final List<Change> selectedChanges = getSelectedChanges();
      sink.put(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS, selectedChanges.toArray(new Change[selectedChanges.size()]));
    }
  }

  private class ToggleChangeAction extends CheckboxAction {
    private final Change myChange;

    public ToggleChangeAction(final Change change) {
      super(myToggleActionTitle);
      myChange = change;
    }

    public boolean isSelected(AnActionEvent e) {
      return myViewer.isIncluded(myChange);
    }

    public void setSelected(AnActionEvent e, boolean state) {
      if (state) {
        myViewer.includeChange(myChange);
      }
      else {
        myViewer.excludeChange(myChange);
      }
    }
  }

  protected void showDiffForChanges(Change[] changesArray, final int indexInSelection) {
    final ShowDiffUIContext context = new ShowDiffUIContext(isInFrame());
    context.setActionsFactory(myDiffExtendUIFactory);
    ShowDiffAction.showDiffForChange(changesArray, indexInSelection, myProject, context);
  }

  private void showDiff() {
    final Change leadSelection = myViewer.getLeadSelection();
    List<Change> changes = myViewer.getSelectedChanges();

    if (changes.size() < 2) {
      final Collection<Change> displayedChanges = getCurrentDisplayedChanges();
      changes = new ArrayList<Change>(displayedChanges);
    }

    int indexInSelection = changes.indexOf(leadSelection);
    if (indexInSelection >= 0) {
      Change[] changesArray = changes.toArray(new Change[changes.size()]);
      showDiffForChanges(changesArray, indexInSelection);
    }
    else if ((leadSelection == null && changes.size() > 0)) {
      Change[] changesArray = changes.toArray(new Change[changes.size()]);
      showDiffForChanges(changesArray, 0);
    }
    else if (leadSelection != null) {
      showDiffForChanges(new Change[]{leadSelection}, 0);
    }
  }

  private static boolean isInFrame() {
    return ModalityState.current().equals(ModalityState.NON_MODAL);
  }

  private class DiffToolbarActionsFactory implements ShowDiffAction.DiffExtendUIFactory {
    public List<? extends AnAction> createActions(Change change) {
      return createDiffActions(change);
    }

    @Nullable
    public JComponent createBottomComponent() {
      return null;
    }
  }

  protected List<AnAction> createDiffActions(final Change change) {
    List<AnAction> actions = new ArrayList<AnAction>();
    if (myCapableOfExcludingChanges) {
      actions.add(new ToggleChangeAction(change));
    }
    return actions;
  }

  public void rebuildList() {
    myViewer.setChangesToDisplay(getCurrentDisplayedChanges());
  }

  private JComponent createToolbar() {
    DefaultActionGroup toolbarGroups = new DefaultActionGroup();
    myToolBarGroup = new DefaultActionGroup();
    toolbarGroups.add(myToolBarGroup);
    buildToolBar(myToolBarGroup);

    toolbarGroups.addSeparator();
    DefaultActionGroup treeActionsGroup = new DefaultActionGroup();
    toolbarGroups.add(treeActionsGroup);
    for(AnAction action: myViewer.getTreeActions()) {
      treeActionsGroup.add(action);
    }

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarGroups, true).getComponent();
  }

  protected void buildToolBar(final DefaultActionGroup toolBarGroup) {
    myDiffAction = new ShowDiffAction() {
      public void actionPerformed(AnActionEvent e) {
        showDiff();
      }
    };
    myDiffAction.registerCustomShortcutSet(CommonShortcuts.getDiff(), myViewer);
    toolBarGroup.add(myDiffAction);
  }

  public List<Change> getCurrentDisplayedChanges() {
    final List<Change> list;
    if (myChangesToDisplay != null) {
      list = new ArrayList<Change>(myChangesToDisplay);
    }
    else if (mySelectedChangeList != null) {
      list = new ArrayList<Change>(mySelectedChangeList.getChanges());
    }
    else {
      list = Collections.emptyList();
    }
    return sortChanges(list);
  }

  protected static List<Change> sortChanges(final List<Change> list) {
    Collections.sort(list, ChangesComparator.getInstance());
    return list;
  }

  public ChangeList getSelectedChangeList() {
    return mySelectedChangeList;
  }

  public JComponent getPrefferedFocusComponent() {
    return myViewer;
  }

  private ChangeList[] getSelectedChangeLists() {
    if (mySelectedChangeList != null) {
      return new ChangeList[]{mySelectedChangeList};
    }
    return null;
  }

  private File[] getSelectedIoFiles() {
    final List<Change> changes = myViewer.getSelectedChanges();
    final List<File> files = new ArrayList<File>();
    for (Change change : changes) {
      final ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        final FilePath file = afterRevision.getFile();
        final File ioFile = file.getIOFile();
        files.add(ioFile);
      }
    }
    return files.toArray(new File[files.size()]);
  }

  public List<Change> getSelectedChanges() {
    return myViewer.getSelectedChanges();
  }

  private VirtualFile[] getSelectedFiles() {
    final List<Change> changes = myViewer.getSelectedChanges();
    return ChangesUtil.getFilesFromChanges(changes);
  }

  public ShowDiffAction getDiffAction() {
    return myDiffAction;
  }

  protected static enum MyUseCase {
    LOCAL_CHANGES,
    COMMITTED_CHANGES
  }

}
