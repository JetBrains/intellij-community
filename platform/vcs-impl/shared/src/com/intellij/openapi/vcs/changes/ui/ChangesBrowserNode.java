// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.util.treeView.FileNameComparator;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListOwner;
import com.intellij.openapi.vcs.changes.ChangesTreeCompatibilityProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

import static com.intellij.util.FontUtil.spaceAndThinSpace;

public abstract class ChangesBrowserNode<T> extends DefaultMutableTreeNode implements UserDataHolderEx {
  public static final Tag IGNORED_FILES_TAG = new VcsBundleTag("changes.nodetitle.ignored.files");
  public static final Tag LOCKED_FOLDERS_TAG = new VcsBundleTag("changes.nodetitle.locked.folders");
  public static final Tag LOGICALLY_LOCKED_TAG = new VcsBundleTag("changes.nodetitle.logicallt.locked.folders");
  public static final Tag UNVERSIONED_FILES_TAG = new VcsBundleTag("changes.nodetitle.unversioned.files");
  public static final Tag MODIFIED_WITHOUT_EDITING_TAG = new VcsBundleTag("changes.nodetitle.modified.without.editing");
  public static final Tag SWITCHED_FILES_TAG = new VcsBundleTag("changes.nodetitle.switched.files");
  public static final Tag SWITCHED_ROOTS_TAG = new VcsBundleTag("changes.nodetitle.switched.roots");
  public static final Tag LOCALLY_DELETED_NODE_TAG = new VcsBundleTag("changes.nodetitle.locally.deleted.files");

  protected static final int CONFLICTS_SORT_WEIGHT = 0;
  protected static final int RESOLVED_CONFLICTS_SORT_WEIGHT = 0;
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
  protected static final int DELETED_SORT_WEIGHT = 12;

  private static final Color UNKNOWN_COLOR = JBColor.marker("ChangesBrowserNode::UNKNOWN_COLOR");

  public static final Convertor<TreePath, String> TO_TEXT_CONVERTER =
    path -> ((ChangesBrowserNode<?>)path.getLastPathComponent()).getTextPresentation();

  private int myFileCount = -1;
  private int myDirectoryCount = -1;
  private boolean myHelper;
  private @Nullable Color myBackgroundColor = UNKNOWN_COLOR;
  private UserDataHolderBase myUserDataHolder;

  protected ChangesBrowserNode(T userObject) {
    super(userObject);
  }

  @ApiStatus.Internal
  public final @Nullable Color getBackgroundColorCached() {
    Color backgroundColor = myBackgroundColor;
    return backgroundColor == UNKNOWN_COLOR ? null : backgroundColor;
  }

  @ApiStatus.Internal
  public final boolean hasBackgroundColorCached() {
    Color backgroundColor = myBackgroundColor;
    return backgroundColor != UNKNOWN_COLOR;
  }

  @ApiStatus.Internal
  public final boolean cacheBackgroundColor(@NotNull Project project) {
    Color backgroundColor = myBackgroundColor;
    if (backgroundColor != UNKNOWN_COLOR) return false;

    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-318216, EA-829418")) {
      backgroundColor = getBackgroundColor(project);
    }
    myBackgroundColor = backgroundColor;
    return backgroundColor != null;
  }

  public static @NotNull ChangesBrowserNode<?> createRoot() {
    ChangesBrowserNode<?> root = new ChangesBrowserRootNode();
    root.markAsHelperNode();
    return root;
  }

  public static @NotNull ChangesBrowserNode<?> createChange(@Nullable Project project, @NotNull Change userObject) {
    return new ChangesBrowserChangeNode(project, userObject, null);
  }

  public static @NotNull ChangesBrowserNode<?> createFile(@Nullable Project project, @NotNull VirtualFile userObject) {
    return new ChangesBrowserFileNode(project, userObject);
  }

  public static @NotNull ChangesBrowserNode<?> createFilePath(@NotNull FilePath userObject, @Nullable FileStatus status) {
    return new ChangesBrowserFilePathNode(userObject, status);
  }

  public static @NotNull ChangesBrowserNode<?> createFilePath(@NotNull FilePath userObject) {
    return createFilePath(userObject, null);
  }

  @Override
  public ChangesBrowserNode<?> getParent() {
    return (ChangesBrowserNode<?>)super.getParent();
  }

  @Override
  public @Nullable <V> V getUserData(@NotNull Key<V> key) {
    UserDataHolderBase holder = myUserDataHolder;
    return holder == null ? null : holder.getUserData(key);
  }

  @Override
  public <V> void putUserData(@NotNull Key<V> key, @Nullable V value) {
    UserDataHolderBase holder = myUserDataHolder;
    if (holder == null) {
      myUserDataHolder = holder = new UserDataHolderBase();
    }
    holder.putUserData(key, value);
  }

  @Override
  public @NotNull <V> V putUserDataIfAbsent(@NotNull Key<V> key, @NotNull V value) {
    UserDataHolderBase holder = myUserDataHolder;
    if (holder == null) {
      myUserDataHolder = holder = new UserDataHolderBase();
    }
    return holder.putUserDataIfAbsent(key, value);
  }

  @Override
  public <V> boolean replace(@NotNull Key<V> key, @Nullable V oldValue, @Nullable V newValue) {
    UserDataHolderBase holder = myUserDataHolder;
    if (holder == null) {
      myUserDataHolder = holder = new UserDataHolderBase();
    }
    return holder.replace(key, oldValue, newValue);
  }

  @Override
  public void insert(MutableTreeNode newChild, int childIndex) {
    super.insert(newChild, childIndex);
    resetCounters();
  }

  @Override
  public void remove(int childIndex) {
    super.remove(childIndex);
    resetCounters();
  }

  protected boolean isFile() {
    return false;
  }

  protected boolean isDirectory() {
    return false;
  }

  public final void markAsHelperNode() {
    myHelper = true;
  }

  public boolean isMeaningfulNode() {
    return !myHelper;
  }

  public int getFileCount() {
    if (myFileCount == -1) {
      myFileCount = (isFile() ? 1 : 0) + sumForChildren(ChangesBrowserNode::getFileCount);
    }
    return myFileCount;
  }

  public int getDirectoryCount() {
    if (myDirectoryCount == -1) {
      myDirectoryCount = (isDirectory() ? 1 : 0) + sumForChildren(ChangesBrowserNode::getDirectoryCount);
    }
    return myDirectoryCount;
  }

  protected void resetCounters() {
    myFileCount = -1;
    myDirectoryCount = -1;
  }

  private int sumForChildren(@NotNull ToIntFunction<? super ChangesBrowserNode<?>> counter) {
    int sum = 0;
    for (int i = 0; i < getChildCount(); i++) {
      ChangesBrowserNode<?> child = (ChangesBrowserNode<?>)getChildAt(i);
      sum += counter.applyAsInt(child);
    }
    return sum;
  }

  public @NotNull List<Change> getAllChangesUnder() {
    return getAllObjectsUnder(Change.class);
  }

  public @NotNull <U> List<U> getAllObjectsUnder(@NotNull Class<U> clazz) {
    return traverseObjectsUnder().filter(clazz).toList();
  }

  public @NotNull JBIterable<?> traverseObjectsUnder() {
    return traverse().map(TreeUtil::getUserObject);
  }

  public @NotNull JBIterable<ChangesBrowserNode<?>> traverse() {
    JBIterable<?> iterable = TreeUtil.treeNodeTraverser(this).preOrderDfsTraversal();
    //noinspection unchecked
    return (JBIterable<ChangesBrowserNode<?>>)iterable;
  }

  public @NotNull JBIterable<ChangesBrowserNode<?>> iterateNodeChildren() {
    JBIterable<?> iterable = TreeUtil.nodeChildren(this);
    //noinspection unchecked
    return (JBIterable<ChangesBrowserNode<?>>)iterable;
  }

  public @NotNull JBIterable<VirtualFile> iterateFilesUnder() {
    return traverseObjectsUnder().filter(VirtualFile.class).filter(VirtualFile::isValid);
  }

  public @NotNull JBIterable<FilePath> iterateFilePathsUnder() {
    return traverse()
      .filter(ChangesBrowserNode::isLeaf)
      .map(ChangesBrowserNode::getUserObject)
      .filter(FilePath.class);
  }

  public void render(@NotNull JTree tree, @NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    render(renderer, selected, expanded, hasFocus);
  }

  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    renderer.append(getTextPresentation());
    appendCount(renderer);
  }

  protected @Nls @NotNull String getCountText() {
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

  @Override
  public String toString() {
    return getTextPresentation();
  }

  @Override
  public void setUserObject(Object userObject) {
    if (userObject != getUserObject()) {
      Logger.getInstance(ChangesBrowserNode.class).error("Should not replace UserObject for ChangesBrowserNode");
    }
    super.setUserObject(userObject);
  }

  /**
   * Used by speedsearch, copy-to-clipboard and default renderer.
   */
  public @Nls String getTextPresentation() {
    PluginException.reportDeprecatedDefault(getClass(), "getTextPresentation", "A proper implementation required");
    return userObject == null ? "" : userObject.toString(); //NON-NLS
  }

  @Override
  public T getUserObject() {
    //noinspection unchecked
    return (T)userObject;
  }

  @ApiStatus.Internal
  public boolean canAcceptDrop(final ChangeListDragBean dragBean) {
    return false;
  }

  @ApiStatus.Internal
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
    return FileNameComparator.getInstance().compare(name1, name2);
  }

  public static int compareFilePaths(@NotNull FilePath path1, @NotNull FilePath path2) {
    return ChangesComparator.getFilePathComparator(true).compare(path1, path2);
  }

  protected void appendParentPath(@NotNull ChangesBrowserNodeRenderer renderer, @Nullable FilePath parentPath) {
    if (parentPath != null) {
      String presentablePath = ChangesTreeCompatibilityProvider.getInstance().getPresentablePath(renderer.getProject(), parentPath, true, true);
      if (presentablePath.isEmpty()) return;
      renderer.append(spaceAndThinSpace() + presentablePath, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  protected void appendParentPath(@NotNull ChangesBrowserNodeRenderer renderer, @Nullable VirtualFile parentPath) {
    if (parentPath != null) {
      String presentablePath = ChangesTreeCompatibilityProvider.getInstance().getPresentablePath(renderer.getProject(), parentPath, true, true);
      if (presentablePath.isEmpty()) return;
      renderer.append(spaceAndThinSpace() + presentablePath, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  protected void appendUpdatingState(@NotNull ChangesBrowserNodeRenderer renderer) {
    renderer.append((getCountText().isEmpty() ? spaceAndThinSpace() : ", ") + VcsBundle.message("changes.nodetitle.updating"),
                    SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  public @Nullable Color getBackgroundColor(@NotNull Project project) {
    return getBackgroundColorFor(project, getUserObject());
  }

  protected static @Nullable Color getBackgroundColorFor(@NotNull Project project, @Nullable Object object) {
    return ChangesTreeCompatibilityProvider.getInstance().getBackgroundColorFor(project, object);
  }

  public boolean shouldExpandByDefault() {
    return true;
  }

  public interface Tag {
    @Nls
    @Override
    String toString();
  }

  public static class TagImpl implements Tag {
    private final @NotNull @Nls String myValue;

    public TagImpl(@NotNull @Nls String value) {
      myValue = value;
    }

    @Override
    public @Nls String toString() {
      return myValue;
    }
  }

  public static class WrapperTag extends ValueTag<Object> {
    public static Tag wrap(@Nullable Object object) {
      if (object == null) return null;
      if (object instanceof Tag) return (Tag)object;
      return new WrapperTag(object);
    }

    private WrapperTag(@NotNull Object value) {
      super(value);
    }

    @Override
    public @Nls String toString() {
      return value.toString(); //NON-NLS
    }
  }

  public static class VcsBundleTag implements Tag {
    private final @PropertyKey(resourceBundle = VcsBundle.BUNDLE) @NotNull String myKey;

    public VcsBundleTag(@PropertyKey(resourceBundle = VcsBundle.BUNDLE) @NotNull String key) {
      myKey = key;
    }

    @Override
    public @Nls String toString() {
      return VcsBundle.message(myKey);
    }
  }

  public abstract static class ValueTag<T> implements ChangesBrowserNode.Tag {
    public final T value;

    public ValueTag(@NotNull T value) {
      this.value = value;
    }

    protected @NotNull T getValue() {
      return value;
    }

    @Override
    public final boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ValueTag<?> tag = (ValueTag<?>)o;
      return Objects.equals(value, tag.value);
    }

    @Override
    public final int hashCode() {
      return Objects.hash(value);
    }
  }

  public interface NodeWithFilePath {
    @NotNull
    FilePath getNodeFilePath();
  }

  @ApiStatus.Internal
  public interface NodeWithCollapsedParents {
    @ApiStatus.Internal
    void addCollapsedParent(@NotNull ChangesBrowserNode<?> parentNode);
  }
}
