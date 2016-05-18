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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.dnd.DnDAware;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SmartExpander;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author max
 */
// TODO: Check if we could extend DnDAwareTree here instead of directly implementing DnDAware
public class ChangesListView extends Tree implements TypeSafeDataProvider, DnDAware {
  private final Project myProject;
  private boolean myShowFlatten = false;
  private final CopyProvider myCopyProvider;

  @NonNls public static final String HELP_ID_KEY = "helpId";
  @NonNls public static final String ourHelpId = "ideaInterface.changes";
  @NonNls public static final DataKey<List<VirtualFile>> UNVERSIONED_FILES_DATA_KEY = DataKey.create("ChangeListView.UnversionedFiles");
  @NonNls public static final DataKey<List<FilePath>> MISSING_FILES_DATA_KEY = DataKey.create("ChangeListView.MissingFiles");
  @NonNls public static final DataKey<List<LocallyDeletedChange>> LOCALLY_DELETED_CHANGES = DataKey.create("ChangeListView.LocallyDeletedChanges");
  @NonNls public static final DataKey<String> HELP_ID_DATA_KEY = DataKey.create(HELP_ID_KEY);

  private ActionGroup myMenuGroup;

  public ChangesListView(@NotNull Project project) {
    myProject = project;

    getModel().setRoot(ChangesBrowserNode.create(myProject, TreeModelBuilder.ROOT_NODE_VALUE));

    setShowsRootHandles(true);
    setRootVisible(false);
    setDragEnabled(true);

    new TreeSpeedSearch(this, new NodeToTextConvertor());
    SmartExpander.installOn(this);
    myCopyProvider = new ChangesBrowserNodeCopyProvider(this);
    new TreeLinkMouseListener(new ChangesBrowserNodeRenderer(myProject, BooleanGetter.FALSE, false)).installOn(this);

    setCellRenderer(new ChangesBrowserNodeRenderer(project, () -> myShowFlatten, true));
  }

  @Override
  public DefaultTreeModel getModel() {
    return (DefaultTreeModel)super.getModel();
  }

  public boolean isShowFlatten() {
    return myShowFlatten;
  }

  public void setShowFlatten(final boolean showFlatten) {
    myShowFlatten = showFlatten;
  }

  public void updateModel(@NotNull DefaultTreeModel model) {
    TreeState state = TreeState.createOn(this, (ChangesBrowserNode)getModel().getRoot());
    state.setScrollToSelection(false);
    DefaultTreeModel oldModel = getModel();
    setModel(model);
    ChangesBrowserNode root = (ChangesBrowserNode)model.getRoot();
    expandPath(new TreePath(root.getPath()));
    state.applyTo(this, (ChangesBrowserNode)getModel().getRoot());
    expandDefaultChangeList(oldModel, root);
  }

  private void expandDefaultChangeList(DefaultTreeModel oldModel, ChangesBrowserNode root) {
    if (((ChangesBrowserNode)oldModel.getRoot()).getCount() == 0 && TreeUtil.collectExpandedPaths(this).size() == 1) {
      TreeNode toExpand = null;
      for (int i = 0; i < root.getChildCount(); i++) {
        TreeNode node = root.getChildAt(i);
        if (node instanceof ChangesBrowserChangeListNode && node.getChildCount() > 0) {
          ChangeList object = ((ChangesBrowserChangeListNode)node).getUserObject();
          if (object instanceof LocalChangeList) {
            if (((LocalChangeList)object).isDefault()) {
              toExpand = node;
              break;
            }
          }
        }
      }

      if (toExpand != null) {
        expandPath(new TreePath(new Object[] {root, toExpand}));
      }
    }
  }

  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (key == VcsDataKeys.CHANGES) {
      sink.put(VcsDataKeys.CHANGES, getSelectedChanges());
    }
    else if (key == VcsDataKeys.CHANGE_LEAD_SELECTION) {
      sink.put(VcsDataKeys.CHANGE_LEAD_SELECTION, getLeadSelection());
    }
    else if (key == VcsDataKeys.CHANGE_LISTS) {
      sink.put(VcsDataKeys.CHANGE_LISTS, getSelectedChangeLists());
    }
    else if (key == CommonDataKeys.VIRTUAL_FILE_ARRAY) {
      sink.put(CommonDataKeys.VIRTUAL_FILE_ARRAY, getSelectedFiles());
    }
    else if (key == CommonDataKeys.NAVIGATABLE) {
      final VirtualFile[] files = getSelectedFiles();
      if (files.length == 1 && !files [0].isDirectory()) {
        sink.put(CommonDataKeys.NAVIGATABLE, new OpenFileDescriptor(myProject, files[0], 0));
      }
    }
    else if (key == CommonDataKeys.NAVIGATABLE_ARRAY) {
      sink.put(CommonDataKeys.NAVIGATABLE_ARRAY, ChangesUtil.getNavigatableArray(myProject, getSelectedFiles()));
    }
    else if (key == PlatformDataKeys.DELETE_ELEMENT_PROVIDER) {
      final TreePath[] paths = getSelectionPaths();
      if (paths != null) {
        for(TreePath path: paths) {
          ChangesBrowserNode node = (ChangesBrowserNode) path.getLastPathComponent();
          if (!(node.getUserObject() instanceof ChangeList)) {
            sink.put(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, new VirtualFileDeleteProvider());
            break;
          }
        }
      }
    }
    else if (key == PlatformDataKeys.COPY_PROVIDER) {
      sink.put(PlatformDataKeys.COPY_PROVIDER, myCopyProvider);
    }
    else if (key == UNVERSIONED_FILES_DATA_KEY) {
      sink.put(UNVERSIONED_FILES_DATA_KEY, getSelectedUnversionedFiles());
    }
    else if (key == VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY) {
      sink.put(VcsDataKeys.MODIFIED_WITHOUT_EDITING_DATA_KEY, getSelectedModifiedWithoutEditing());
    } else if (key == LOCALLY_DELETED_CHANGES) {
      sink.put(LOCALLY_DELETED_CHANGES, getSelectedLocallyDeletedChanges());
    } else if (key == MISSING_FILES_DATA_KEY) {
      sink.put(MISSING_FILES_DATA_KEY, getSelectedMissingFiles());
    } else if (VcsDataKeys.HAVE_LOCALLY_DELETED == key) {
      sink.put(VcsDataKeys.HAVE_LOCALLY_DELETED, !getSelectedMissingFiles().isEmpty());
    } else if (VcsDataKeys.HAVE_MODIFIED_WITHOUT_EDITING == key) {
      sink.put(VcsDataKeys.HAVE_MODIFIED_WITHOUT_EDITING, !getSelectedModifiedWithoutEditing().isEmpty());
    } else if (VcsDataKeys.HAVE_SELECTED_CHANGES == key) {
      sink.put(VcsDataKeys.HAVE_SELECTED_CHANGES, haveSelectedChanges());
    } else if (key == HELP_ID_DATA_KEY) {
      sink.put(HELP_ID_DATA_KEY, ourHelpId);
    }
    else if (key == VcsDataKeys.CHANGES_IN_LIST_KEY) {
      final TreePath selectionPath = getSelectionPath();
      if (selectionPath != null && selectionPath.getPathCount() > 1) {
        ChangesBrowserNode<?> firstNode = (ChangesBrowserNode)selectionPath.getPathComponent(1);
        if (firstNode instanceof ChangesBrowserChangeListNode) {
          final List<Change> list = firstNode.getAllChangesUnder();
          sink.put(VcsDataKeys.CHANGES_IN_LIST_KEY, list);
        }
      }
    }
  }

  private List<VirtualFile> getSelectedUnversionedFiles() {
    return getSelectedVirtualFiles(ChangesBrowserNode.UNVERSIONED_FILES_TAG);
  }

  @NotNull
  private List<VirtualFile> getSelectedModifiedWithoutEditing() {
    return getSelectedVirtualFiles(ChangesBrowserNode.MODIFIED_WITHOUT_EDITING_TAG);
  }

  private List<VirtualFile> getSelectedIgnoredFiles() {
    return getSelectedVirtualFiles(ChangesBrowserNode.IGNORED_FILES_TAG);
  }

  @NotNull
  private List<VirtualFile> getSelectedVirtualFiles(@Nullable Object tag) {
    return getVirtualFiles(getSelectionPaths(), tag);
  }

  @NotNull
  static List<VirtualFile> getVirtualFiles(@Nullable TreePath[] paths, @Nullable Object tag) {
    return paths == null
           ? Collections.emptyList()
           : Stream.of(paths)
             .filter(path -> isUnderTag(path, tag))
             .flatMap(path -> ((ChangesBrowserNode<?>)path.getLastPathComponent()).getAllFilesUnder().stream())
             .distinct()
             .collect(Collectors.toList());
  }

  static boolean isUnderTag(@NotNull TreePath path, @Nullable Object tag) {
    boolean result = false;

    if (path.getPathCount() > 1) {
      ChangesBrowserNode firstNode = (ChangesBrowserNode)path.getPathComponent(1);
      result = tag == null || firstNode.getUserObject() == tag;
    }

    return result;
  }

  @NotNull
  static Change[] getChanges(@NotNull Project project, @Nullable TreePath[] paths) {
    Set<Change> changes = new LinkedHashSet<Change>();

    if (paths == null) {
      return new Change[0];
    }

    for (TreePath path : paths) {
      ChangesBrowserNode<?> node = (ChangesBrowserNode)path.getLastPathComponent();
      changes.addAll(node.getAllChangesUnder());
    }

    if (changes.isEmpty()) {
      final List<VirtualFile> selectedModifiedWithoutEditing = getVirtualFiles(paths, ChangesBrowserNode.MODIFIED_WITHOUT_EDITING_TAG);
      if (selectedModifiedWithoutEditing != null && !selectedModifiedWithoutEditing.isEmpty()) {
        for (VirtualFile file : selectedModifiedWithoutEditing) {
          VcsCurrentRevisionProxy before = VcsCurrentRevisionProxy.create(file, project);

          if (before != null) {
            ContentRevision afterRevision = new CurrentContentRevision(VcsUtil.getFilePath(file));
            changes.add(new Change(before, afterRevision, FileStatus.HIJACKED));
          }
        }
      }
    }

    return changes.toArray(new Change[changes.size()]);
  }

  private List<LocallyDeletedChange> getSelectedLocallyDeletedChanges() {
    Set<LocallyDeletedChange> files = new HashSet<LocallyDeletedChange>();
    final TreePath[] paths = getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        if (isUnderTag(path, TreeModelBuilder.LOCALLY_DELETED_NODE)) {
          ChangesBrowserNode<?> node = (ChangesBrowserNode)path.getLastPathComponent();
          files.addAll(node.getAllObjectsUnder(LocallyDeletedChange.class));
        }
      }
    }
    return new ArrayList<LocallyDeletedChange>(files);
  }

  @NotNull
  private List<FilePath> getSelectedMissingFiles() {
    return getSelectedLocallyDeletedChanges().stream().map(LocallyDeletedChange::getPath).collect(Collectors.toList());
  }

  protected VirtualFile[] getSelectedFiles() {
    final Change[] changes = getSelectedChanges();
    Collection<VirtualFile> files = new HashSet<VirtualFile>();
    for (Change change : changes) {
      final ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        final VirtualFile file = afterRevision.getFile().getVirtualFile();
        if (file != null && file.isValid()) {
          files.add(file);
        }
      }
    }

    files.addAll(getSelectedVirtualFiles(null));

    return VfsUtilCore.toVirtualFileArray(files);
  }

  private Boolean haveSelectedChanges() {
    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return false;

    for (TreePath path : paths) {
      ChangesBrowserNode node = (ChangesBrowserNode) path.getLastPathComponent();
      if (node instanceof ChangesBrowserChangeNode) {
        return true;
      } else if (node instanceof ChangesBrowserChangeListNode) {
        final ChangesBrowserChangeListNode changeListNode = (ChangesBrowserChangeListNode)node;
        if (changeListNode.getChildCount() > 0) {
          return true;
        }
      }
    }
    return false;
  }

  private Change[] getLeadSelection() {
    final Set<Change> changes = new LinkedHashSet<Change>();

    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return new Change[0];

    for (TreePath path : paths) {
      ChangesBrowserNode node = (ChangesBrowserNode) path.getLastPathComponent();
      if (node instanceof ChangesBrowserChangeNode) {
        changes.add(((ChangesBrowserChangeNode) node).getUserObject());
      }
    }

    return changes.toArray(new Change[changes.size()]);
  }

  @NotNull
  public Change[] getChanges() {
    final Set<Change> changes = new LinkedHashSet<Change>();

    TreeUtil.traverse((ChangesBrowserNode)getModel().getRoot(), new TreeUtil.Traverse() {
      @Override
      public boolean accept(Object node) {
        changes.addAll(((ChangesBrowserNode)node).getAllChangesUnder());
        return true;
      }
    });

    return changes.toArray(new Change[changes.size()]);
  }

  @NotNull
  public Change[] getSelectedChanges() {
    return getChanges(myProject, getSelectionPaths());
  }

  @NotNull
  private ChangeList[] getSelectedChangeLists() {
    Set<ChangeList> lists = new HashSet<ChangeList>();

    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return new ChangeList[0];

    for (TreePath path : paths) {
      ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
      final Object userObject = node.getUserObject();
      if (userObject instanceof ChangeList) {
        lists.add((ChangeList)userObject);
      }
    }

    return lists.toArray(new ChangeList[lists.size()]);
  }

  public void setMenuActions(final ActionGroup menuGroup) {
    myMenuGroup = menuGroup;
    updateMenu();
    editSourceRegistration();
  }

  protected void editSourceRegistration() {
    EditSourceOnDoubleClickHandler.install(this);
    EditSourceOnEnterKeyHandler.install(this);
  }

  private void updateMenu() {
    PopupHandler.installPopupHandler(this, myMenuGroup, ActionPlaces.CHANGES_VIEW_POPUP, ActionManager.getInstance());
  }

  private static class NodeToTextConvertor implements Convertor<TreePath, String> {
    @Override
    public String convert(final TreePath path) {
      ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
      return node.getTextPresentation();
    }
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return this;
  }

  @Override
  public void processMouseEvent(final MouseEvent e) {
    if (MouseEvent.MOUSE_RELEASED == e.getID() && !isSelectionEmpty() && !e.isShiftDown() && !e.isControlDown()  &&
        !e.isMetaDown() && !e.isPopupTrigger()) {
      if (isOverSelection(e.getPoint())) {
        clearSelection();
        final TreePath path = getPathForLocation(e.getPoint().x, e.getPoint().y);
        if (path != null) {
          setSelectionPath(path);
        }
      }
    }


    super.processMouseEvent(e);
  }

  @Override
  public boolean isOverSelection(final Point point) {
    return TreeUtil.isOverSelection(this, point);
  }

  @Override
  public void dropSelectionButUnderPoint(final Point point) {
    TreeUtil.dropSelectionButUnderPoint(this, point);
  }
}