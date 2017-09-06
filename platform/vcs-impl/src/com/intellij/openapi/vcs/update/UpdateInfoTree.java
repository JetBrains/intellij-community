/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.committed.RefreshIncomingChangesAction;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
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
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.List;

public class UpdateInfoTree extends PanelWithActionsAndCloseButton {
  private VirtualFile mySelectedFile;
  private FilePath mySelectedUrl;
  private final Tree myTree = new Tree();
  @NotNull private final Project myProject;
  private final UpdatedFiles myUpdatedFiles;
  private final VcsConfiguration myVcsConfiguration;
  private UpdateRootNode myRoot;
  private DefaultTreeModel myTreeModel;
  private FileStatusListener myFileStatusListener;
  private final FileStatusManager myFileStatusManager;
  private final String myRootName;
  private final ActionInfo myActionInfo;
  private boolean myCanGroupByChangeList = false;
  private boolean myGroupByChangeList = false;
  private JLabel myLoadingChangeListsLabel;
  private List<CommittedChangeList> myCommittedChangeLists;
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
                        String rootName,
                        ActionInfo actionInfo) {
    super(contentManager, "reference.versionControl.toolwindow.update");
    myActionInfo = actionInfo;

    myFileStatusListener = new FileStatusListener() {
      public void fileStatusesChanged() {
        myTree.repaint();
      }

      public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
        myTree.repaint();
      }
    };

    myProject = project;
    myUpdatedFiles = updatedFiles;
    myRootName = rootName;

    myVcsConfiguration = VcsConfiguration.getInstance(myProject);
    myFileStatusManager = FileStatusManager.getInstance(myProject);
    myFileStatusManager.addFileStatusListener(myFileStatusListener);
    createTree();
    init();
    myTreeExpander = new DefaultTreeExpander(myTree);
    myTreeIterable = new MyTreeIterable();
  }

  public void dispose() {
    Disposer.dispose(myRoot);
    if (myFileStatusListener != null) {
      myFileStatusManager.removeFileStatusListener(myFileStatusListener);
      myFileStatusListener = null;
    }
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

  protected void addActionsTo(DefaultActionGroup group) {
    group.add(new MyGroupByPackagesAction());
    group.add(new GroupByChangeListAction());
    group.add(new FilterAction());
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EXPAND_ALL));
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COLLAPSE_ALL));
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_DIFF_COMMON));
  }

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

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        AbstractTreeNode treeNode = (AbstractTreeNode)e.getPath().getLastPathComponent();
        VirtualFilePointer pointer = null;
        if (treeNode instanceof FileTreeNode) {
          pointer = ((FileTreeNode)treeNode).getFilePointer();
        }
        if (pointer != null) {
          mySelectedUrl = getFilePath(pointer);
          mySelectedFile = pointer.getFile();
        }
        else {
          mySelectedUrl = null;
          mySelectedFile = null;
        }
      }
    });
    myTree.setCellRenderer(new UpdateTreeCellRenderer());
    TreeUtil.installActions(myTree);
    new TreeSpeedSearch(myTree, path -> {
      Object last = path.getLastPathComponent();
      if (last instanceof AbstractTreeNode) {
        return ((AbstractTreeNode)last).getText();
      }
      return TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING.convert(path);
    }, true);

    myTree.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        final DefaultActionGroup group = (DefaultActionGroup)ActionManager.getInstance().getAction("UpdateActionGroup");
        if (group != null) { //if no UpdateActionGroup was configured
          ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UPDATE_POPUP,
                                                                                        group);
          popupMenu.getComponent().show(comp, x, y);
        }
      }
    });
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
    myRoot.rebuild(myVcsConfiguration.UPDATE_GROUP_BY_PACKAGES, getScopeFilter(), myVcsConfiguration.UPDATE_FILTER_BY_SCOPE);
    if (myTreeModel != null) {
      myTreeModel.reload();
    }
  }

  public Object getData(String dataId) {
    if (myTreeBrowser != null && myTreeBrowser.isVisible()) {
      return null;
    }
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      if (mySelectedFile == null || !mySelectedFile.isValid()) return null;
      return new OpenFileDescriptor(myProject, mySelectedFile);
    }
    else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      return getVirtualFileArray();
    }
    else if (VcsDataKeys.IO_FILE_ARRAY.is(dataId)) {
      return getFileArray();
    } else if (PlatformDataKeys.TREE_EXPANDER.is(dataId)) {
      if (myGroupByChangeList) {
        return myTreeBrowser != null ? myTreeBrowser.getTreeExpander() : null;
      }
      else {
        return myTreeExpander;
      }
    } else if (VcsDataKeys.UPDATE_VIEW_SELECTED_PATH.is(dataId)) {
      return mySelectedUrl;
    } else if (VcsDataKeys.UPDATE_VIEW_FILES_ITERABLE.is(dataId)) {
      return myTreeIterable;
    } else if (VcsDataKeys.LABEL_BEFORE.is(dataId)) {
      return myBefore;
    }  else if (VcsDataKeys.LABEL_AFTER.is(dataId)) {
      return myAfter;
    }

    return super.getData(dataId);
  }

  private class MyTreeIterator implements Iterator<Pair<FilePath, FileStatus>> {
    private final Enumeration myEnum;
    private FilePath myNext;
    private FileStatus myStatus;

    private MyTreeIterator() {
      myEnum = myRoot.depthFirstEnumeration();
      step();
    }

    public boolean hasNext() {
      return myNext != null;
    }

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
        if (o instanceof FileTreeNode) {
          final FileTreeNode treeNode = (FileTreeNode)o;
          VirtualFilePointer filePointer = treeNode.getFilePointer();

          myNext = getFilePath(filePointer);
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
    private GroupTreeNode findParentGroupTreeNode(@NotNull TreeNode treeNode) {
      TreeNode currentNode = treeNode;
      while (currentNode != null && !(currentNode instanceof GroupTreeNode)) {
        currentNode = currentNode.getParent();
      }
      return (GroupTreeNode)currentNode;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private class MyTreeIterable implements Iterable<Pair<FilePath, FileStatus>> {
    public Iterator<Pair<FilePath, FileStatus>> iterator() {
      return new MyTreeIterator();
    }
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

  @Nullable
  private File[] getFileArray() {
    ArrayList<File> result = new ArrayList<>();
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths != null) {
      for (TreePath selectionPath : selectionPaths) {
        AbstractTreeNode treeNode = (AbstractTreeNode)selectionPath.getLastPathComponent();
        result.addAll(treeNode.getFiles());
      }
    }
    if (result.isEmpty()) return null;
    return result.toArray(new File[result.size()]);
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

  public void setChangeLists(final List<CommittedChangeList> receivedChanges) {
    final boolean hasEmptyCaches = CommittedChangesCache.getInstance(myProject).hasEmptyCaches();

    ApplicationManager.getApplication().invokeLater(() -> {
      if (myLoadingChangeListsLabel != null) {
        remove(myLoadingChangeListsLabel);
        myLoadingChangeListsLabel = null;
      }
      myCommittedChangeLists = receivedChanges;
      myTreeBrowser.setItems(myCommittedChangeLists, CommittedChangesBrowserUseCase.UPDATE);
      if (hasEmptyCaches) {
        final StatusText statusText = myTreeBrowser.getEmptyText();
        statusText.clear();
        statusText.appendText("Click ")
          .appendText("Refresh", SimpleTextAttributes.LINK_ATTRIBUTES, new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
              RefreshIncomingChangesAction.doRefresh(myProject);
            }
          })
          .appendText(" to initialize repository changes cache");
      }
    }, myProject.getDisposed());
  }

  private class MyGroupByPackagesAction extends ToggleAction implements DumbAware {
    public MyGroupByPackagesAction() {
      super(VcsBundle.message("action.name.group.by.packages"), null, PlatformIcons.GROUP_BY_PACKAGES);
    }

    public boolean isSelected(AnActionEvent e) {
      return myVcsConfiguration.UPDATE_GROUP_BY_PACKAGES;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myVcsConfiguration.UPDATE_GROUP_BY_PACKAGES = state;
      updateTreeModel();
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(!myGroupByChangeList);
    }
  }

  private class GroupByChangeListAction extends ToggleAction implements DumbAware {
    public GroupByChangeListAction() {
      super(VcsBundle.message("update.info.group.by.changelist"), null, AllIcons.Actions.ShowAsTree);
    }

    public boolean isSelected(AnActionEvent e) {
      return myGroupByChangeList;
    }

    public void setSelected(AnActionEvent e, boolean state) {
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

    public void update(final AnActionEvent e) {
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
    public FilterAction() {
      super("Scope Filter", VcsBundle.getString("settings.filter.update.project.info.by.scope"), AllIcons.General.Filter);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myVcsConfiguration.UPDATE_FILTER_BY_SCOPE;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myVcsConfiguration.UPDATE_FILTER_BY_SCOPE = state;
      updateTreeModel();
    }

    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(!myGroupByChangeList && getFilterScopeName() != null);
    }
  }

  @NotNull
  private static FilePath getFilePath(@NotNull VirtualFilePointer filePointer) {
    return VcsUtil.getFilePath(filePointer.getPresentableUrl(), false);
  }
}
