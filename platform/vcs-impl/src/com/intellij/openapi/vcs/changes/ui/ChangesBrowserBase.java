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

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.openapi.vcs.changes.ChangesUtil.getFiles;
import static com.intellij.openapi.vcs.changes.ChangesUtil.getNavigatableArray;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG;
import static com.intellij.openapi.vcs.changes.ui.ChangesListView.*;

public abstract class ChangesBrowserBase<T> extends JPanel implements TypeSafeDataProvider, Disposable {
  private static final Logger LOG = Logger.getInstance(ChangesBrowserBase.class);

  // for backgroundable rollback to mark
  private boolean myDataIsDirty;
  protected final Class<T> myClass;
  protected final ChangesTreeList<T> myViewer;
  protected final JScrollPane myViewerScrollPane;
  protected ChangeList mySelectedChangeList;
  protected List<T> myChangesToDisplay;
  protected final Project myProject;
  private final boolean myCapableOfExcludingChanges;
  protected final JPanel myHeaderPanel;
  private JComponent myBottomPanel;
  private DefaultActionGroup myToolBarGroup;
  private String myToggleActionTitle = VcsBundle.message("commit.dialog.include.action.name");

  private JComponent myDiffBottomComponent;

  public static DataKey<ChangesBrowserBase> DATA_KEY = DataKey.create("com.intellij.openapi.vcs.changes.ui.ChangesBrowser");
  private AnAction myDiffAction;
  private final VirtualFile myToSelect;
  @NotNull private final DeleteProvider myDeleteProvider = new VirtualFileDeleteProvider();

  public void setChangesToDisplay(final List<T> changes) {
    myChangesToDisplay = changes;
    myViewer.setChangesToDisplay(changes);
  }

  public void setDecorator(final ChangeNodeDecorator decorator) {
    myViewer.setChangeDecorator(decorator);
  }

  protected ChangesBrowserBase(@NotNull final Project project,
                               @NotNull List<T> changes,
                               final boolean capableOfExcludingChanges,
                               final boolean highlightProblems,
                               @Nullable final Runnable inclusionListener,
                               @NotNull ChangesBrowser.MyUseCase useCase,
                               @Nullable VirtualFile toSelect,
                               @NotNull Class<T> clazz) {
    super(new BorderLayout());
    setFocusable(false);

    myClass = clazz;
    myDataIsDirty = false;
    myProject = project;
    myCapableOfExcludingChanges = capableOfExcludingChanges;
    myToSelect = toSelect;

    ChangeNodeDecorator decorator =
      ChangesBrowser.MyUseCase.LOCAL_CHANGES.equals(useCase) ? RemoteRevisionsCache.getInstance(myProject).getChangesNodeDecorator() : null;

    myViewer = new ChangesTreeList<T>(myProject, changes, capableOfExcludingChanges, highlightProblems, inclusionListener, decorator) {
      protected DefaultTreeModel buildTreeModel(final List<T> changes, ChangeNodeDecorator changeNodeDecorator) {
        return ChangesBrowserBase.this.buildTreeModel(changes, changeNodeDecorator, isShowFlatten());
      }

      protected List<T> getSelectedObjects(final ChangesBrowserNode<?> node) {
        return ChangesBrowserBase.this.getSelectedObjects(node);
      }

      @Nullable
      protected T getLeadSelectedObject(final ChangesBrowserNode<?> node) {
        return ChangesBrowserBase.this.getLeadSelectedObject(node);
      }

      @Override
      public void setScrollPaneBorder(Border border) {
        myViewerScrollPane.setBorder(border);
      }
    };
    myViewerScrollPane = ScrollPaneFactory.createScrollPane(myViewer);
    myHeaderPanel = new JPanel(new BorderLayout());
  }

  protected void init() {
    add(myViewerScrollPane, BorderLayout.CENTER);

    myHeaderPanel.add(createToolbar(), BorderLayout.CENTER);
    add(myHeaderPanel, BorderLayout.NORTH);

    myBottomPanel = new JPanel(new BorderLayout());
    add(myBottomPanel, BorderLayout.SOUTH);

    myViewer.installPopupHandler(myToolBarGroup);
    myViewer.setDoubleClickHandler(getDoubleClickHandler());
  }

  @NotNull
  protected abstract DefaultTreeModel buildTreeModel(final List<T> changes, ChangeNodeDecorator changeNodeDecorator, boolean showFlatten);

  @NotNull
  protected abstract List<T> getSelectedObjects(@NotNull ChangesBrowserNode<?> node);

  @Nullable
  protected abstract T getLeadSelectedObject(@NotNull ChangesBrowserNode<?> node);

  @NotNull
  protected Runnable getDoubleClickHandler() {
    return () -> showDiff();
  }

  protected void setInitialSelection(final List<? extends ChangeList> changeLists,
                                     @NotNull List<T> changes,
                                     final ChangeList initialListSelection) {
    mySelectedChangeList = initialListSelection;
  }

  public void dispose() {
  }

  public void addToolbarAction(AnAction action) {
    myToolBarGroup.add(action);
  }

  public void setDiffBottomComponent(JComponent diffBottomComponent) {
    myDiffBottomComponent = diffBottomComponent;
  }

  public void setToggleActionTitle(final String toggleActionTitle) {
    myToggleActionTitle = toggleActionTitle;
  }

  public JPanel getHeaderPanel() {
    return myHeaderPanel;
  }

  public ChangesTreeList<T> getViewer() {
    return myViewer;
  }

  @NotNull
  public JScrollPane getViewerScrollPane() {
    return myViewerScrollPane;
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key == VcsDataKeys.CHANGES) {
      List<Change> list = getSelectedChanges();
      if (list.isEmpty()) list = getAllChanges();
      sink.put(VcsDataKeys.CHANGES, list.toArray(new Change[list.size()]));
    }
    else if (key == VcsDataKeys.CHANGES_SELECTION) {
      sink.put(VcsDataKeys.CHANGES_SELECTION, getChangesSelection());
    }
    else if (key == VcsDataKeys.CHANGE_LISTS) {
      sink.put(VcsDataKeys.CHANGE_LISTS, getSelectedChangeLists());
    }
    else if (key == VcsDataKeys.CHANGE_LEAD_SELECTION) {
      final Change highestSelection = ObjectUtils.tryCast(myViewer.getHighestLeadSelection(), Change.class);
      sink.put(VcsDataKeys.CHANGE_LEAD_SELECTION, (highestSelection == null) ? new Change[]{} : new Change[]{highestSelection});
    }
    else if (key == CommonDataKeys.VIRTUAL_FILE_ARRAY) {
      sink.put(CommonDataKeys.VIRTUAL_FILE_ARRAY, getSelectedFiles().toArray(VirtualFile[]::new));
    }
    else if (key == CommonDataKeys.NAVIGATABLE_ARRAY) {
      sink.put(CommonDataKeys.NAVIGATABLE_ARRAY, getNavigatableArray(myProject, getSelectedFiles()));
    }
    else if (VcsDataKeys.IO_FILE_ARRAY.equals(key)) {
      sink.put(VcsDataKeys.IO_FILE_ARRAY, getSelectedIoFiles());
    }
    else if (key == DATA_KEY) {
      sink.put(DATA_KEY, this);
    }
    else if (VcsDataKeys.SELECTED_CHANGES_IN_DETAILS.equals(key)) {
      final List<Change> selectedChanges = getSelectedChanges();
      sink.put(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS, selectedChanges.toArray(new Change[selectedChanges.size()]));
    }
    else if (UNVERSIONED_FILES_DATA_KEY.equals(key)) {
      sink.put(UNVERSIONED_FILES_DATA_KEY, getVirtualFiles(myViewer.getSelectionPaths(), UNVERSIONED_FILES_TAG));
    }
    else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.equals(key)) {
      sink.put(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, myDeleteProvider);
    }
  }

  public void select(List<T> changes) {
    myViewer.select(changes);
  }

  public JComponent getBottomPanel() {
    return myBottomPanel;
  }

  private class ToggleChangeAction extends CheckboxAction {
    public ToggleChangeAction() {
      super(myToggleActionTitle);
    }

    public boolean isSelected(AnActionEvent e) {
      T change = ObjectUtils.tryCast(e.getData(VcsDataKeys.CURRENT_CHANGE), myClass);
      if (change == null) return false;

      return myViewer.isIncluded(change);
    }

    public void setSelected(AnActionEvent e, boolean state) {
      T change = ObjectUtils.tryCast(e.getData(VcsDataKeys.CURRENT_CHANGE), myClass);
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
    if (myDiffBottomComponent != null) {
      context.putChainContext(DiffUserDataKeysEx.BOTTOM_PANEL, myDiffBottomComponent);
    }

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

    Change[] changesArray = changes.toArray(new Change[changes.size()]);
    showDiffForChanges(changesArray, selection.getSelectedIndex());

    afterDiffRefresh();
  }

  @NotNull
  protected ListSelection<Change> getChangesSelection() {
    final Change leadSelection = ObjectUtils.tryCast(myViewer.getLeadSelection(), Change.class);
    List<Change> changes = getSelectedChanges();

    if (changes.size() < 2) {
      List<Change> allChanges = getAllChanges();
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

  protected List<AnAction> createDiffActions() {
    List<AnAction> actions = new ArrayList<>();
    if (myCapableOfExcludingChanges) {
      actions.add(new ToggleChangeAction());
    }
    return actions;
  }

  public void rebuildList() {
    myViewer.setChangesToDisplay(getCurrentDisplayedObjects(), myToSelect);
  }

  public void setAlwayExpandList(final boolean value) {
    myViewer.setAlwaysExpandList(value);
  }

  @NotNull
  protected JComponent createToolbar() {
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
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(canShowDiff() || e.getInputEvent() instanceof KeyEvent);
      }

      public void actionPerformed(AnActionEvent e) {
        showDiff();
      }
    };
    ActionUtil.copyFrom(myDiffAction, IdeActions.ACTION_SHOW_DIFF_COMMON);
    myDiffAction.registerCustomShortcutSet(myViewer, null);
    toolBarGroup.add(myDiffAction);
  }

  @NotNull
  public abstract List<Change> getCurrentIncludedChanges();

  @NotNull
  public List<Change> getCurrentDisplayedChanges() {
    return mySelectedChangeList != null ? ContainerUtil.newArrayList(mySelectedChangeList.getChanges()) : Collections.emptyList();
  }

  @NotNull
  public abstract List<T> getCurrentDisplayedObjects();

  @NotNull
  public List<VirtualFile> getIncludedUnversionedFiles() {
    return Collections.emptyList();
  }

  public int getUnversionedFilesCount() {
    return 0;
  }

  public ChangeList getSelectedChangeList() {
    return mySelectedChangeList;
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
    return files.toArray(new File[files.size()]);
  }

  @NotNull
  public abstract List<Change> getSelectedChanges();

  @NotNull
  public abstract List<Change> getAllChanges();

  @NotNull
  protected Stream<VirtualFile> getSelectedFiles() {
    return Stream.concat(
      getFiles(getSelectedChanges().stream()),
      getVirtualFiles(myViewer.getSelectionPaths(), null)
    ).distinct();
  }

  public AnAction getDiffAction() {
    return myDiffAction;
  }

  public boolean isDataIsDirty() {
    return myDataIsDirty;
  }

  public void setDataIsDirty(boolean dataIsDirty) {
    myDataIsDirty = dataIsDirty;
  }

  public void setSelectionMode(@JdkConstants.TreeSelectionMode int mode) {
    myViewer.setSelectionMode(mode);
  }

  @Contract(pure = true)
  @NotNull
  protected static <T> List<Change> findChanges(@NotNull Collection<T> items) {
    return ContainerUtil.findAll(items, Change.class);
  }

  static boolean isUnderUnversioned(@NotNull ChangesBrowserNode node) {
    return isUnderTag(new TreePath(node.getPath()), UNVERSIONED_FILES_TAG);
  }
}
