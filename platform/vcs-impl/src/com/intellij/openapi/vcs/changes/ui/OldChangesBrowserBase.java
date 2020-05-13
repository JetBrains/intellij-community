// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.DiffDialogHints;
import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ListSelection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.openapi.vcs.changes.ChangesUtil.*;
import static com.intellij.openapi.vcs.changes.ui.ChangesListView.getVirtualFiles;

/**
 * @deprecated Use {@link ChangesBrowserBase}
 */
@Deprecated
public abstract class OldChangesBrowserBase extends JPanel implements TypeSafeDataProvider, Disposable {
  protected final ChangesTreeList<Change> myViewer;
  protected final JScrollPane myViewerScrollPane;
  protected ChangeList mySelectedChangeList;
  protected List<Change> myChangesToDisplay;
  protected final Project myProject;
  private final boolean myCapableOfExcludingChanges;
  private DefaultActionGroup myToolBarGroup;

  private AnAction myDiffAction;
  private final VirtualFile myToSelect;
  @NotNull private final DeleteProvider myDeleteProvider = new VirtualFileDeleteProvider();

  public void setChangesToDisplay(final List<Change> changes) {
    myChangesToDisplay = changes;
    myViewer.setChangesToDisplay(changes);
  }

  public void setDecorator(final ChangeNodeDecorator decorator) {
    myViewer.setChangeDecorator(decorator);
  }

  protected OldChangesBrowserBase(@NotNull final Project project,
                                  @NotNull List<Change> changes,
                                  final boolean capableOfExcludingChanges,
                                  final boolean highlightProblems,
                                  @Nullable final Runnable inclusionListener,
                                  @NotNull ChangesBrowser.MyUseCase useCase,
                                  @Nullable VirtualFile toSelect) {
    super(new BorderLayout());
    setFocusable(false);

    myProject = project;
    myCapableOfExcludingChanges = capableOfExcludingChanges;
    myToSelect = toSelect;

    ChangeNodeDecorator decorator =
      ChangesBrowser.MyUseCase.LOCAL_CHANGES.equals(useCase) ? RemoteRevisionsCache.getInstance(myProject).getChangesNodeDecorator() : null;

    myViewer = new ChangesTreeList<Change>(myProject, changes, capableOfExcludingChanges, highlightProblems, inclusionListener, decorator) {
      @Override
      protected DefaultTreeModel buildTreeModel(final List<Change> changes, ChangeNodeDecorator changeNodeDecorator) {
        return OldChangesBrowserBase.this.buildTreeModel(changes, changeNodeDecorator, isShowFlatten());
      }

      @Override
      protected List<Change> getSelectedObjects(final ChangesBrowserNode<?> node) {
        return OldChangesBrowserBase.this.getSelectedObjects(node);
      }

      @Override
      @Nullable
      protected Change getLeadSelectedObject(final ChangesBrowserNode<?> node) {
        return OldChangesBrowserBase.this.getLeadSelectedObject(node);
      }

      @Override
      public void setScrollPaneBorder(Border border) {
        myViewerScrollPane.setBorder(border);
      }
    };
    myViewerScrollPane = ScrollPaneFactory.createScrollPane(myViewer);
  }

  protected void init() {
    add(myViewerScrollPane, BorderLayout.CENTER);

    add(createToolbar(), BorderLayout.NORTH);

    myViewer.installPopupHandler(myToolBarGroup);
    myViewer.setDoubleClickAndEnterKeyHandler(() -> showDiff());
  }

  @NotNull
  protected DefaultTreeModel buildTreeModel(final List<Change> changes, ChangeNodeDecorator changeNodeDecorator, boolean showFlatten) {
    return TreeModelBuilder.buildFromChanges(myProject, myViewer.getGrouping(), changes, changeNodeDecorator);
  }

  @NotNull
  protected List<Change> getSelectedObjects(@NotNull final ChangesBrowserNode<?> node) {
    return node.getAllChangesUnder();
  }

  @Nullable
  protected Change getLeadSelectedObject(@NotNull final ChangesBrowserNode<?> node) {
    final Object o = node.getUserObject();
    if (o instanceof Change) {
      return (Change)o;
    }
    return null;
  }

  @Override
  public void dispose() {
  }

  public void addToolbarAction(AnAction action) {
    myToolBarGroup.add(action);
  }

  public ChangesTreeList<Change> getViewer() {
    return myViewer;
  }

  @NotNull
  public JScrollPane getViewerScrollPane() {
    return myViewerScrollPane;
  }

  @Override
  public void calcData(@NotNull DataKey key, @NotNull DataSink sink) {
    if (key == VcsDataKeys.CHANGES) {
      List<Change> list = getSelectedChanges();
      if (list.isEmpty()) list = myViewer.getChanges();
      sink.put(VcsDataKeys.CHANGES, list.toArray(new Change[0]));
    }
    else if (key == VcsDataKeys.CHANGES_SELECTION) {
      sink.put(VcsDataKeys.CHANGES_SELECTION, getChangesSelection());
    }
    else if (key == VcsDataKeys.CHANGE_LISTS) {
      sink.put(VcsDataKeys.CHANGE_LISTS, getSelectedChangeLists());
    }
    else if (key == VcsDataKeys.CHANGE_LEAD_SELECTION) {
      final Change highestSelection = myViewer.getHighestLeadSelection();
      sink.put(VcsDataKeys.CHANGE_LEAD_SELECTION, (highestSelection == null) ? new Change[]{} : new Change[]{highestSelection});
    }
    else if (key == CommonDataKeys.VIRTUAL_FILE_ARRAY) {
      sink.put(CommonDataKeys.VIRTUAL_FILE_ARRAY, getSelectedFiles().toArray(VirtualFile[]::new));
    }
    else if (key == CommonDataKeys.NAVIGATABLE_ARRAY) {
      sink.put(CommonDataKeys.NAVIGATABLE_ARRAY, getNavigatableArray(myProject, getNavigatableFiles()));
    }
    else if (VcsDataKeys.IO_FILE_ARRAY.equals(key)) {
      sink.put(VcsDataKeys.IO_FILE_ARRAY, getSelectedIoFiles());
    }
    else if (VcsDataKeys.SELECTED_CHANGES_IN_DETAILS.equals(key)) {
      final List<Change> selectedChanges = getSelectedChanges();
      sink.put(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS, selectedChanges.toArray(new Change[0]));
    }
    else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.equals(key)) {
      sink.put(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, myDeleteProvider);
    }
    else {
      //noinspection unchecked
      sink.put(key, myViewer.getData(key.getName()));
    }
  }

  public void select(List<Change> changes) {
    myViewer.select(changes);
  }

  private class ToggleChangeAction extends CheckboxAction {
    ToggleChangeAction() {
      super(VcsBundle.messagePointer("commit.dialog.include.action.name"));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      Change change = e.getData(VcsDataKeys.CURRENT_CHANGE);
      if (change == null) return false;

      return myViewer.isIncluded(change);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
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

  protected void showDiffForChanges(Change[] changesArray, final int indexInSelection) {
    final ShowDiffContext context = new ShowDiffContext(isInFrame() ? DiffDialogHints.FRAME : DiffDialogHints.MODAL);

    context.addActions(createDiffActions());

    updateDiffContext(context);

    ShowDiffAction.showDiffForChange(myProject, Arrays.asList(changesArray), indexInSelection, context);
  }

  protected void updateDiffContext(@NotNull ShowDiffContext context) {
  }

  private boolean canShowDiff() {
    return ShowDiffAction.canShowDiff(myProject, getChangesSelection().getList());
  }

  private void showDiff() {
    ListSelection<Change> selection = getChangesSelection();
    List<Change> changes = selection.getList();

    Change[] changesArray = changes.toArray(new Change[0]);
    showDiffForChanges(changesArray, selection.getSelectedIndex());

    afterDiffRefresh();
  }

  @NotNull
  private ListSelection<Change> getChangesSelection() {
    final Change leadSelection = myViewer.getLeadSelection();
    List<Change> changes = getSelectedChanges();

    if (changes.size() < 2) {
      List<Change> allChanges = myViewer.getChanges();
      if (allChanges.size() > 1 || changes.isEmpty()) {
        changes = allChanges;
      }
    }

    if (leadSelection != null && !changes.contains(leadSelection)) {
      return ListSelection.createSingleton(leadSelection);
    }

    return ListSelection.create(changes, leadSelection);
  }

  protected void afterDiffRefresh() {
  }

  private static boolean isInFrame() {
    return ModalityState.current().equals(ModalityState.NON_MODAL);
  }

  private List<AnAction> createDiffActions() {
    List<AnAction> actions = new ArrayList<>();
    if (myCapableOfExcludingChanges) {
      actions.add(new ToggleChangeAction());
    }
    return actions;
  }

  public void rebuildList() {
    myViewer.setChangesToDisplay(getCurrentDisplayedChanges(), myToSelect);
  }

  public void setAlwayExpandList(final boolean value) {
    myViewer.setAlwaysExpandList(value);
  }

  @NotNull
  private JComponent createToolbar() {
    DefaultActionGroup toolbarGroups = new DefaultActionGroup();
    myToolBarGroup = new DefaultActionGroup();
    toolbarGroups.add(myToolBarGroup);
    buildToolBar(myToolBarGroup);

    toolbarGroups.addSeparator();
    DefaultActionGroup treeActionsGroup = new DefaultActionGroup();
    toolbarGroups.add(treeActionsGroup);
    for (AnAction action : myViewer.getTreeActions()) {
      treeActionsGroup.add(action);
    }

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("ChangesBrowser", toolbarGroups, true);
    toolbar.setTargetComponent(this);
    return toolbar.getComponent();
  }

  protected void buildToolBar(final DefaultActionGroup toolBarGroup) {
    myDiffAction = new DumbAwareAction() {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(canShowDiff() || e.getInputEvent() instanceof KeyEvent);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        showDiff();
      }
    };
    ActionUtil.copyFrom(myDiffAction, IdeActions.ACTION_SHOW_DIFF_COMMON);
    myDiffAction.registerCustomShortcutSet(myViewer, null);
    toolBarGroup.add(myDiffAction);
  }


  @NotNull
  private List<Change> getCurrentDisplayedChanges() {
    if (myChangesToDisplay != null) return myChangesToDisplay;
    return mySelectedChangeList != null ? new ArrayList<>(mySelectedChangeList.getChanges()) : Collections.emptyList();
  }

  public JComponent getPreferredFocusedComponent() {
    return myViewer.getPreferredFocusedComponent();
  }

  private ChangeList[] getSelectedChangeLists() {
    if (mySelectedChangeList != null) {
      return new ChangeList[]{mySelectedChangeList};
    }
    return null;
  }

  private File[] getSelectedIoFiles() {
    final List<Change> changes = getSelectedChanges();
    final List<File> files = new ArrayList<>();
    for (Change change : changes) {
      final ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        final FilePath file = afterRevision.getFile();
        final File ioFile = file.getIOFile();
        files.add(ioFile);
      }
    }
    return files.toArray(new File[0]);
  }

  @NotNull
  public List<Change> getSelectedChanges() {
    return myViewer.getSelectedChanges();
  }

  @NotNull
  private Stream<VirtualFile> getSelectedFiles() {
    return Stream.concat(
      getAfterRevisionsFiles(getSelectedChanges().stream()),
      getVirtualFiles(myViewer.getSelectionPaths(), null)
    ).distinct();
  }

  @NotNull
  private Stream<VirtualFile> getNavigatableFiles() {
    return Stream.concat(
      getFiles(getSelectedChanges().stream()),
      getVirtualFiles(myViewer.getSelectionPaths(), null)
    ).distinct();
  }

  public AnAction getDiffAction() {
    return myDiffAction;
  }
}
