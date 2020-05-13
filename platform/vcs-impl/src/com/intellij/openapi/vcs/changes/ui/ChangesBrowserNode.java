// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.util.treeView.FileNameComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.Convertor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.util.FontUtil.spaceAndThinSpace;

public class ChangesBrowserNode<T> extends DefaultMutableTreeNode implements UserDataHolderEx {
  @NonNls private static final String ROOT_NODE_VALUE = "root";

  public static final Object IGNORED_FILES_TAG = new Tag("changes.nodetitle.ignored.files");
  public static final Object LOCKED_FOLDERS_TAG = new Tag("changes.nodetitle.locked.folders");
  public static final Object LOGICALLY_LOCKED_TAG = new Tag("changes.nodetitle.logicallt.locked.folders");
  public static final Object UNVERSIONED_FILES_TAG = new Tag("changes.nodetitle.unversioned.files");
  public static final Object MODIFIED_WITHOUT_EDITING_TAG = new Tag("changes.nodetitle.modified.without.editing");
  public static final Object SWITCHED_FILES_TAG = new Tag("changes.nodetitle.switched.files");
  public static final Object SWITCHED_ROOTS_TAG = new Tag("changes.nodetitle.switched.roots");
  public static final Object LOCALLY_DELETED_NODE_TAG = new Tag("changes.nodetitle.locally.deleted.files");

  protected static final int CONFLICTS_SORT_WEIGHT = 0;
  protected static final int DEFAULT_CHANGE_LIST_SORT_WEIGHT = 1;
  protected static final int CHANGE_LIST_SORT_WEIGHT = 2;
  protected static final int REPOSITORY_SORT_WEIGHT = 3;
  protected static final int MODULE_SORT_WEIGHT = 4;
  protected static final int DIRECTORY_PATH_SORT_WEIGHT = 5;
  protected static final int FILE_PATH_SORT_WEIGHT = 6;
  protected static final int CHANGE_SORT_WEIGHT = 7;
  protected static final int VIRTUAL_FILE_SORT_WEIGHT = 8;
  protected static final int UNVERSIONED_SORT_WEIGHT = 9;
  protected static final int DEFAULT_SORT_WEIGHT = 10;
  protected static final int IGNORED_SORT_WEIGHT = 11;

  public static final Convertor<TreePath, String> TO_TEXT_CONVERTER =
    path -> ((ChangesBrowserNode)path.getLastPathComponent()).getTextPresentation();

  private SimpleTextAttributes myAttributes;

  private int myFileCount = -1;
  private int myDirectoryCount = -1;
  private boolean myHelper;
  @NotNull private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();

  protected ChangesBrowserNode(T userObject) {
    super(userObject);
    myAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  @NotNull
  public static ChangesBrowserNode createRoot() {
    ChangesBrowserNode root = createObject(ROOT_NODE_VALUE);
    root.markAsHelperNode();
    return root;
  }

  @NotNull
  public static ChangesBrowserNode createChange(@Nullable Project project, @NotNull Change userObject) {
    return new ChangesBrowserChangeNode(project, userObject, null);
  }

  @NotNull
  public static ChangesBrowserNode createFile(@Nullable Project project, @NotNull VirtualFile userObject) {
    return new ChangesBrowserFileNode(project, userObject);
  }

  @NotNull
  public static ChangesBrowserNode createFilePath(@NotNull FilePath userObject, @Nullable FileStatus status) {
    return new ChangesBrowserFilePathNode(userObject, status);
  }

  @NotNull
  public static ChangesBrowserNode createFilePath(@NotNull FilePath userObject) {
    return createFilePath(userObject, null);
  }

  @NotNull
  public static ChangesBrowserNode createLogicallyLocked(@Nullable Project project, @NotNull VirtualFile file, @NotNull LogicalLock lock) {
    return new ChangesBrowserLogicallyLockedFile(project, file, lock);
  }

  @NotNull
  public static ChangesBrowserNode createLockedFolders(@NotNull Project project) {
    return new ChangesBrowserLockedFoldersNode(project, LOCKED_FOLDERS_TAG);
  }

  @NotNull
  public static ChangesBrowserNode createLocallyDeleted(@NotNull LocallyDeletedChange change) {
    return new ChangesBrowserLocallyDeletedNode(change);
  }

  @NotNull
  public static ChangesBrowserNode createObject(@NotNull Object userObject) {
    return new ChangesBrowserNode<>(userObject);
  }

  @Deprecated
  @NotNull
  public static ChangesBrowserNode create(@NotNull Project project, @NotNull Object userObject) {
    if (userObject instanceof Change) {
      return new ChangesBrowserChangeNode(project, (Change)userObject, null);
    }
    if (userObject instanceof VirtualFile) {
      return new ChangesBrowserFileNode(project, (VirtualFile) userObject);
    }
    if (userObject instanceof FilePath) {
      return new ChangesBrowserFilePathNode((FilePath) userObject);
    }
    if (userObject == LOCKED_FOLDERS_TAG) {
      return new ChangesBrowserLockedFoldersNode(project, userObject);
    }
    if (userObject instanceof ChangesBrowserLogicallyLockedFile) {
      return (ChangesBrowserNode) userObject;
    }
    return new ChangesBrowserNode<>(userObject);
  }

  @Override
  public ChangesBrowserNode<?> getParent() {
    return (ChangesBrowserNode<?>)super.getParent();
  }

  @Nullable
  @Override
  public <V> V getUserData(@NotNull Key<V> key) {
    return myUserDataHolder.getUserData(key);
  }

  @Override
  public <V> void putUserData(@NotNull Key<V> key, @Nullable V value) {
    myUserDataHolder.putUserData(key, value);
  }

  @NotNull
  @Override
  public <V> V putUserDataIfAbsent(@NotNull Key<V> key, @NotNull V value) {
    return myUserDataHolder.putUserDataIfAbsent(key, value);
  }

  @Override
  public <V> boolean replace(@NotNull Key<V> key, @Nullable V oldValue, @Nullable V newValue) {
    return myUserDataHolder.replace(key, oldValue, newValue);
  }

  @Override
  public void insert(MutableTreeNode newChild, int childIndex) {
    super.insert(newChild, childIndex);
    resetFileCounters();
  }

  @Override
  public void remove(int childIndex) {
    super.remove(childIndex);
    resetFileCounters();
  }

  protected boolean isFile() {
    return false;
  }

  protected boolean isDirectory() {
    return false;
  }

  public void markAsHelperNode() {
    myHelper = true;
  }

  public boolean isMeaningfulNode() {
    return !myHelper;
  }

  public int getFileCount() {
    if (myFileCount == -1) {
      myFileCount = (isFile() ? 1 : 0) + toStream(children()).mapToInt(ChangesBrowserNode::getFileCount).sum();
    }
    return myFileCount;
  }

  public int getDirectoryCount() {
    if (myDirectoryCount == -1) {
      myDirectoryCount = (isDirectory() ? 1 : 0) + toStream(children()).mapToInt(ChangesBrowserNode::getDirectoryCount).sum();
    }
    return myDirectoryCount;
  }

  private void resetFileCounters() {
    myFileCount = -1;
    myDirectoryCount = -1;
  }

  @NotNull
  public Stream<ChangesBrowserNode<?>> getNodesUnderStream() {
    return toStream(preorderEnumeration());
  }

  @NotNull
  public List<Change> getAllChangesUnder() {
    return getAllObjectsUnder(Change.class);
  }

  @NotNull
  public <U> List<U> getAllObjectsUnder(@NotNull Class<U> clazz) {
    return getObjectsUnderStream(clazz).collect(Collectors.toList());
  }

  @NotNull
  public <U> Stream<U> getObjectsUnderStream(@NotNull Class<U> clazz) {
    return toStream(preorderEnumeration())
      .map(ChangesBrowserNode::getUserObject)
      .select(clazz);
  }

  @NotNull
  public List<VirtualFile> getAllFilesUnder() {
    return getFilesUnderStream().collect(Collectors.toList());
  }

  @NotNull
  public Stream<VirtualFile> getFilesUnderStream() {
    return toStream(preorderEnumeration())
      .map(ChangesBrowserNode::getUserObject)
      .select(VirtualFile.class)
      .filter(VirtualFile::isValid);
  }

  @NotNull
  public List<FilePath> getAllFilePathsUnder() {
    return getFilePathsUnderStream().collect(Collectors.toList());
  }

  @NotNull
  public Stream<FilePath> getFilePathsUnderStream() {
    return toStream(preorderEnumeration())
      .filter(ChangesBrowserNode::isLeaf)
      .map(ChangesBrowserNode::getUserObject)
      .select(FilePath.class);
  }

  @NotNull
  private static StreamEx<ChangesBrowserNode<?>> toStream(@NotNull Enumeration enumeration) {
    //noinspection unchecked
    return StreamEx.<ChangesBrowserNode>of(enumeration);
  }

  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    renderer.append(getTextPresentation(), myAttributes);
    appendCount(renderer);
  }

  @NotNull
  protected String getCountText() {
    int count = getFileCount();
    int dirCount = getDirectoryCount();
    String result = "";

    if (dirCount != 0 || count != 0) {
      result = spaceAndThinSpace() +
               (dirCount == 0
                ? VcsBundle.message("changes.nodetitle.changecount", count)
                : count == 0
                  ? VcsBundle.message("changes.nodetitle.directory.changecount", dirCount)
                  : VcsBundle.message("changes.nodetitle.directory.file.changecount", dirCount, count));
    }

    return result;
  }

  protected void appendCount(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.append(getCountText(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  public String toString() {
    return getTextPresentation();
  }

  public String getTextPresentation() {
    return userObject == null ? "" : userObject.toString();
  }

  @Override
  public T getUserObject() {
    //noinspection unchecked
    return (T) userObject;
  }

  public boolean canAcceptDrop(final ChangeListDragBean dragBean) {
    return false;
  }

  public void acceptDrop(final ChangeListOwner dragOwner, final ChangeListDragBean dragBean) {
  }

  /**
   * Nodes with the same sort weight should share {@link #compareUserObjects} implementation
   */
  public int getSortWeight() {
    return DEFAULT_SORT_WEIGHT;
  }

  public int compareUserObjects(final T o2) {
    return 0;
  }

  protected static int compareFileNames(@NotNull String name1, @NotNull String name2) {
    return FileNameComparator.INSTANCE.compare(name1, name2);
  }

  public static int compareFilePaths(@NotNull FilePath path1, @NotNull FilePath path2) {
    return ChangesComparator.getFilePathComparator(true).compare(path1, path2);
  }

  public void setAttributes(@NotNull SimpleTextAttributes attributes) {
    myAttributes = attributes;
  }

  protected void appendParentPath(@NotNull ChangesBrowserNodeRenderer renderer, @Nullable FilePath parentPath) {
    if (parentPath != null) {
      appendParentPath(renderer, parentPath.getPresentableUrl());
    }
  }

  protected void appendParentPath(@NotNull ChangesBrowserNodeRenderer renderer, @Nullable VirtualFile parentPath) {
    if (parentPath != null) {
      appendParentPath(renderer, parentPath.getPresentableUrl());
    }
  }

  private static void appendParentPath(@NotNull ChangesBrowserNodeRenderer renderer, @NotNull String parentPath) {
    renderer.append(spaceAndThinSpace() + FileUtil.getLocationRelativeToUserHome(parentPath),
                    SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  protected void appendUpdatingState(@NotNull ChangesBrowserNodeRenderer renderer) {
    renderer.append((getCountText().isEmpty() ? spaceAndThinSpace() : ", ") + VcsBundle.message("changes.nodetitle.updating"),
                    SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  @Nullable
  public Color getBackgroundColor(@NotNull Project project) {
    return getBackgroundColorFor(project, getUserObject());
  }

  @Nullable
  protected static Color getBackgroundColorFor(@NotNull Project project, @Nullable Object object) {
    VirtualFile file;
    if (object instanceof FilePath) {
      file = getScopeVirtualFileFor((FilePath)object);
    }
    else if (object instanceof Change) {
      file = getScopeVirtualFileFor(ChangesUtil.getFilePath((Change)object));
    }
    else {
      file = ObjectUtils.tryCast(object, VirtualFile.class);
    }

    if (file != null) {
      return VfsPresentationUtil.getFileBackgroundColor(project, file);
    }
    return null;
  }

  @Nullable
  private static VirtualFile getScopeVirtualFileFor(@NotNull FilePath filePath) {
    if (filePath.isNonLocal()) return null;
    return ChangesUtil.findValidParentAccurately(filePath);
  }

  @Deprecated
  public final int getCount() {
    return getFileCount();
  }

  public boolean shouldExpandByDefault() {
    return true;
  }

  private static class Tag {
    @PropertyKey(resourceBundle = VcsBundle.BUNDLE) @NotNull private final String myKey;

    Tag(@PropertyKey(resourceBundle = VcsBundle.BUNDLE) @NotNull String key) {
      myKey = key;
    }

    @Override
    public String toString() {
      return VcsBundle.message(myKey);
    }
  }
}
