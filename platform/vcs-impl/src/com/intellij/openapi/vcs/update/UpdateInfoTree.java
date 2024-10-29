// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.history.Label;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.PanelWithActionsAndCloseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.committed.RefreshIncomingChangesAction;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.search.scope.packageSet.PackageSetBase;
import com.intellij.ui.*;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.*;

public class UpdateInfoTree extends PanelWithActionsAndCloseButton {
  public static final DataKey<FilePath> UPDATE_VIEW_SELECTED_PATH =
    DataKey.create("AbstractCommonUpdateAction.UpdateViewSelectedPath");
  public static final DataKey<Iterable<Pair<FilePath, FileStatus>>> UPDATE_VIEW_FILES_ITERABLE =
    DataKey.create("AbstractCommonUpdateAction.UpdatedFilesIterable");
  public static final DataKey<Label> LABEL_BEFORE = DataKey.create("LABEL_BEFORE");
  public static final DataKey<Label> LABEL_AFTER = DataKey.create("LABEL_AFTER");

  private final Tree myTree = new Tree();
  @NotNull private final Project myProject;
  private final UpdatedFiles myUpdatedFiles;
  private final VcsConfiguration myVcsConfiguration;
  private UpdateRootNode myRoot;
  private DefaultTreeModel myTreeModel;
  private FileStatusListener myFileStatusListener;
  private final FileStatusManager myFileStatusManager;
  private final @Nls String myRootName;
  private final ActionInfo myActionInfo;
  private boolean myCanGroupByChangeList = false;
  private boolean myGroupByChangeList = false;
  private JLabel myLoadingChangeListsLabel;
  private List<? extends CommittedChangeList> myCommittedChangeLists;
  private final JPanel myCenterPanel = new JPanel(new CardLayout());
  @NonNls private static final String CARD_STATUS = "Status";
  @NonNls private static final String CARD_CHANGES = "Changes";
  private CommittedChangesTreeBrowser myTreeBrowser;
  private final TreeExpander myTreeExpander;
  private final MyTreeIterable myTreeIterable;

  private Label myBefore;
  private Label myAfter;

  public UpdateInfoTree(@NotNull ContentManager contentManager,
                        @NotNull Project project,
                        UpdatedFiles updatedFiles,
                        @Nls String rootName,
                        ActionInfo actionInfo) {
    super(contentManager, "reference.versionControl.toolwindow.update");
    myActionInfo = actionInfo;

    myFileStatusListener = new FileStatusListener() {
      @Override
      public void fileStatusesChanged() {
        myTree.repaint();
      }

      @Override
      public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
        myTree.repaint();
      }
    };

    myProject = project;
    myUpdatedFiles = updatedFiles;
    myRootName = rootName;

    myVcsConfiguration = VcsConfiguration.getInstance(myProject);
    myFileStatusManager = FileStatusManager.getInstance(myProject);
    myFileStatusManager.addFileStatusListener(myFileStatusListener, this);
    createTree();
    init();
    myTreeExpander = new DefaultTreeExpander(myTree);
    myTreeIterable = new MyTreeIterable();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myRoot);
  }

  public void setCanGroupByChangeList(final boolean canGroupByChangeList) {
    myCanGroupByChangeList = canGroupByChangeList;
    if (myCanGroupByChangeList) {
      myLoadingChangeListsLabel = new JLabel(VcsBundle.message("update.info.loading.changelists"));
      add(myLoadingChangeListsLabel, BorderLayout.SOUTH);
      myGroupByChangeList = myVcsConfiguration.UPDATE_GROUP_BY_CHANGELIST;
      if (myGroupByChangeList) {
        final CardLayout cardLayout = (CardLayout)myCenterPanel.getLayout();
        cardLayout.show(myCenterPanel, CARD_CHANGES);
      }
    }
  }

  @Override
  protected void addActionsTo(DefaultActionGroup group) {
    group.add(new MyGroupByPackagesAction());
    group.add(new GroupByChangeListAction());
    group.add(new FilterAction());
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EXPAND_ALL));
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COLLAPSE_ALL));
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_DIFF_COMMON));
  }

  @Override
  protected JComponent createCenterPanel() {
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));
    myCenterPanel.add(CARD_STATUS, scrollPane);
    myTreeBrowser = new CommittedChangesTreeBrowser(myProject, Collections.emptyList());
    Disposer.register(this, myTreeBrowser);
    myTreeBrowser.setHelpId(getHelpId());
    myCenterPanel.add(CARD_CHANGES, myTreeBrowser);
    return myCenterPanel;
  }

  private void createTree() {
    SmartExpander.installOn(myTree);
    SelectionSaver.installOn(myTree);
    createTreeModel();

    myTree.setCellRenderer(new UpdateTreeCellRenderer());
    TreeUtil.installActions(myTree);
    TreeSpeedSearch.installOn(myTree, true, path -> {
      Object last = path.getLastPathComponent();
      if (last instanceof AbstractTreeNode) {
        return ((AbstractTreeNode)last).getText();
      }
      return TreeSpeedSearch.NODE_PRESENTATION_FUNCTION.apply(path);
    });

    PopupHandler.installPopupMenu(myTree, "UpdateActionGroup", ActionPlaces.UPDATE_POPUP);
    EditSourceOnDoubleClickHandler.install(myTree);
    EditSourceOnEnterKeyHandler.install(myTree);

    myTree.setSelectionRow(0);
  }

  private void createTreeModel() {
    myRoot = new UpdateRootNode(myUpdatedFiles, myProject, myRootName, myActionInfo);
    updateTreeModel();
    myTreeModel = new DefaultTreeModel(myRoot);
    myRoot.setTreeModel(myTreeModel);
    myTree.setModel(myTreeModel);
    myRoot.setTree(myTree);
  }

  private void updateTreeModel() {
    if (Disposer.isDisposed(this)) return;
    myRoot.rebuild(myVcsConfiguration.UPDATE_GROUP_BY_PACKAGES, getScopeFilter(), myVcsConfiguration.UPDATE_FILTER_BY_SCOPE);
    if (myTreeModel != null) {
      myTreeModel.reload();
    }
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    if (myTreeBrowser != null && myTreeBrowser.isVisible()) {
      return;
    }
    VirtualFilePointer pointer = getSelectedFilePointer();
    sink.set(VcsDataKeys.FILE_PATHS, getFilePathIterable());
    sink.set(PlatformDataKeys.TREE_EXPANDER,
             myGroupByChangeList ? myTreeBrowser != null ? myTreeBrowser.getTreeExpander() : null : myTreeExpander);
    sink.set(UPDATE_VIEW_SELECTED_PATH,
             pointer != null ? getFilePath(pointer) : null);
    sink.set(UPDATE_VIEW_FILES_ITERABLE, myTreeIterable);
    sink.set(LABEL_BEFORE, myBefore);
    sink.set(LABEL_AFTER, myAfter);

    sink.lazy(CommonDataKeys.NAVIGATABLE, () -> {
      if (pointer == null || !pointer.isValid()) return null;
      VirtualFile selectedFile = pointer.getFile();
      return selectedFile != null ? new OpenFileDescriptor(myProject, selectedFile) : null;
    });
    sink.lazy(CommonDataKeys.VIRTUAL_FILE_ARRAY, () -> {
      return getVirtualFileArray();
    });
  }

  private final class MyTreeIterator implements Iterator<Pair<FilePath, FileStatus>> {
    private final Enumeration myEnum;
    private FilePath myNext;
    private FileStatus myStatus;

    private MyTreeIterator() {
      myEnum = myRoot.depthFirstEnumeration();
      step();
    }

    @Override
    public boolean hasNext() {
      return myNext != null;
    }

    @Override
    public Pair<FilePath, FileStatus> next() {
      final FilePath result = myNext;
      final FileStatus status = myStatus;
      step();
      return Pair.create(result, status);
    }

    private void step() {
      myNext = null;
      while (myEnum.hasMoreElements()) {
        final Object o = myEnum.nextElement();
        if (o instanceof FileTreeNode treeNode) {
          VirtualFilePointer filePointer = treeNode.getFilePointer();

          FilePath filePath = getFilePath(filePointer);
          if (filePath == null) continue;

          myNext = filePath;
          myStatus = FileStatus.MODIFIED;

          final GroupTreeNode parent = findParentGroupTreeNode(treeNode.getParent());
          if (parent != null) {
            final String id = parent.getFileGroupId();
            if (FileGroup.CREATED_ID.equals(id)) {
              myStatus = FileStatus.ADDED;
            }
            else if (FileGroup.REMOVED_FROM_REPOSITORY_ID.equals(id)) {
              myStatus = FileStatus.DELETED;
            }
          }
          break;
        }
      }
    }

    @Nullable
    private static GroupTreeNode findParentGroupTreeNode(@NotNull TreeNode treeNode) {
      TreeNode currentNode = treeNode;
      while (currentNode != null && !(currentNode instanceof GroupTreeNode)) {
        currentNode = currentNode.getParent();
      }
      return (GroupTreeNode)currentNode;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private class MyTreeIterable implements Iterable<Pair<FilePath, FileStatus>> {
    @Override
    public Iterator<Pair<FilePath, FileStatus>> iterator() {
      return new MyTreeIterator();
    }
  }

  @Nullable
  private VirtualFilePointer getSelectedFilePointer() {
    TreePath path = myTree.getSelectionPath();
    if (path == null) return null;
    AbstractTreeNode treeNode = (AbstractTreeNode)path.getLastPathComponent();
    return treeNode instanceof FileTreeNode ? ((FileTreeNode)treeNode).getFilePointer() : null;
  }

  private VirtualFile[] getVirtualFileArray() {
    ArrayList<VirtualFile> result = new ArrayList<>();
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths != null) {
      for (TreePath selectionPath : selectionPaths) {
        AbstractTreeNode treeNode = (AbstractTreeNode)selectionPath.getLastPathComponent();
        result.addAll(treeNode.getVirtualFiles());
      }
    }
    return VfsUtil.toVirtualFileArray(result);
  }

  private @Nullable Iterable<FilePath> getFilePathIterable() {
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths == null) return null;

    List<File> result = new ArrayList<>();
    for (TreePath selectionPath : selectionPaths) {
      AbstractTreeNode treeNode = (AbstractTreeNode)selectionPath.getLastPathComponent();
      result.addAll(treeNode.getFiles());
    }
    if (result.isEmpty()) return null;
    return ContainerUtil.map(result, ioFile -> VcsUtil.getFilePath(ioFile));
  }

  int getFilteredFilesCount() {
    Pair<PackageSetBase, NamedScopesHolder> scopeFilter = getScopeFilter();
    int[] result = new int[1];
    TreeUtil.traverse(myRoot, node -> {
      if (node instanceof FileTreeNode) {
        if (((FileTreeNode)node).acceptFilter(scopeFilter, true)) {
          result[0]++;
        }
      }
      return true;
    });
    return result[0];
  }

  public void expandRootChildren() {
    TreeNode root = (TreeNode)myTreeModel.getRoot();

    if (root.getChildCount() == 1) {
      myTree.expandPath(new TreePath(new Object[]{root, root.getChildAt(0)}));
    }
  }

  public void setChangeLists(final List<? extends CommittedChangeList> receivedChanges) {
    final boolean hasEmptyCaches = CommittedChangesCache.getInstance(myProject).hasEmptyCaches();

    ApplicationManager.getApplication().invokeLater(() -> {
      if (Disposer.isDisposed(this)) return;
      if (myLoadingChangeListsLabel != null) {
        remove(myLoadingChangeListsLabel);
        myLoadingChangeListsLabel = null;
      }
      myCommittedChangeLists = receivedChanges;
      myTreeBrowser.setItems(myCommittedChangeLists, CommittedChangesBrowserUseCase.UPDATE);
      if (hasEmptyCaches) {
        final StatusText statusText = myTreeBrowser.getEmptyText();
        statusText.clear();
        //noinspection DialogTitleCapitalization
        statusText.appendText(VcsBundle.message("update.info.click.status.text.prefix")).appendText(" ")
          .appendText(VcsBundle.message("update.info.refresh.link.status.text"), SimpleTextAttributes.LINK_ATTRIBUTES, new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
              RefreshIncomingChangesAction.doRefresh(myProject);
            }
          })
          .appendText(" ").appendText(VcsBundle.message("update.info.to.initialize.status.text.suffix"));
      }
    }, myProject.getDisposed());
  }

  private class MyGroupByPackagesAction extends ToggleAction implements DumbAware {
    MyGroupByPackagesAction() {
      super(VcsBundle.messagePointer("action.name.group.by.packages"), PlatformIcons.GROUP_BY_PACKAGES);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myVcsConfiguration.UPDATE_GROUP_BY_PACKAGES;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myVcsConfiguration.UPDATE_GROUP_BY_PACKAGES = state;
      updateTreeModel();
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(!myGroupByChangeList);
    }
  }

  private class GroupByChangeListAction extends ToggleAction implements DumbAware {
    GroupByChangeListAction() {
      super(VcsBundle.messagePointer("update.info.group.by.changelist"), AllIcons.Actions.ShowAsTree);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myGroupByChangeList;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myGroupByChangeList = state;
      myVcsConfiguration.UPDATE_GROUP_BY_CHANGELIST = myGroupByChangeList;
      final CardLayout cardLayout = (CardLayout)myCenterPanel.getLayout();
      if (!myGroupByChangeList) {
        cardLayout.show(myCenterPanel, CARD_STATUS);
      }
      else {
        cardLayout.show(myCenterPanel, CARD_CHANGES);
      }
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(myCanGroupByChangeList);
    }
  }

  public void setBefore(Label before) {
    myBefore = before;
  }

  public void setAfter(Label after) {
    myAfter = after;
  }

  @Nullable
  private Pair<PackageSetBase, NamedScopesHolder> getScopeFilter() {
    String scopeName = getFilterScopeName();
    if (scopeName != null) {
      for (NamedScopesHolder holder : NamedScopesHolder.getAllNamedScopeHolders(myProject)) {
        NamedScope scope = holder.getScope(scopeName);
        if (scope != null) {
          PackageSet packageSet = scope.getValue();
          if (packageSet instanceof PackageSetBase) {
            return Pair.create((PackageSetBase)packageSet, holder);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private String getFilterScopeName() {
    return myVcsConfiguration.UPDATE_FILTER_SCOPE_NAME;
  }

  @Nullable
  NamedScope getFilterScope() {
    Pair<PackageSetBase, NamedScopesHolder> filter = getScopeFilter();
    return filter == null ? null : filter.second.getScope(getFilterScopeName());
  }

  private class FilterAction extends ToggleAction implements DumbAware {
    FilterAction() {
      super(VcsBundle.messagePointer("action.ToggleAction.text.scope.filter"),
            VcsBundle.messagePointer("settings.filter.update.project.info.by.scope"), AllIcons.General.Filter);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myVcsConfiguration.UPDATE_FILTER_BY_SCOPE;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myVcsConfiguration.UPDATE_FILTER_BY_SCOPE = state;
      updateTreeModel();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(!myGroupByChangeList && getFilterScopeName() != null);
    }
  }

  @Nullable
  private static FilePath getFilePath(@NotNull VirtualFilePointer filePointer) {
    String path = VirtualFileManager.extractPath(filePointer.getUrl());
    if (StringUtil.isEmpty(path)) return null; // pointer disposed
    return VcsUtil.getFilePath(path, false);
  }
}
