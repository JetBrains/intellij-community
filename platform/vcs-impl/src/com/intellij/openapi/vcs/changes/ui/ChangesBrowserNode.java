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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListOwner;
import com.intellij.openapi.vcs.changes.LocallyDeletedChange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.intellij.util.FontUtil.spaceAndThinSpace;

public class ChangesBrowserNode<T> extends DefaultMutableTreeNode {

  public static final Convertor<TreePath, String> TO_TEXT_CONVERTER =
    path -> ((ChangesBrowserNode)path.getLastPathComponent()).getTextPresentation();

  private SimpleTextAttributes myAttributes;

  protected int myCount = -1;
  protected int myDirectoryCount = -1;
  public static final Object IGNORED_FILES_TAG = new Object() {
    public String toString() {
      return VcsBundle.message("changes.nodetitle.ignored.files");
    }
  };
  public static final Object LOCKED_FOLDERS_TAG = new Object() {
    public String toString() {
      return VcsBundle.message("changes.nodetitle.locked.folders");
    }
  };
  public static final Object LOGICALLY_LOCKED_TAG = VcsBundle.message("changes.nodetitle.logicallt.locked.folders");

  public static final Object UNVERSIONED_FILES_TAG = new Object() {
    public String toString() {
      return VcsBundle.message("changes.nodetitle.unversioned.files");
    }
  };
  public static final Object MODIFIED_WITHOUT_EDITING_TAG = new Object() {
    public String toString() {
      return VcsBundle.message("changes.nodetitle.modified.without.editing");
    }
  };
  public static final Object SWITCHED_FILES_TAG = new Object() {
    public String toString() {
      return VcsBundle.message("changes.nodetitle.switched.files");
    }
  };
  public static final Object SWITCHED_ROOTS_TAG = new Object() {
    public String toString() {
      return VcsBundle.message("changes.nodetitle.switched.roots");
    }
  };

  protected ChangesBrowserNode(Object userObject) {
    super(userObject);
    myAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  @NotNull
  public static ChangesBrowserNode create(@NotNull LocallyDeletedChange change) {
    return new ChangesBrowserLocallyDeletedNode(change);
  }

  @NotNull
  public static ChangesBrowserNode create(@NotNull Project project, @NotNull Object userObject) {
    if (userObject instanceof Change) {
      return new ChangesBrowserChangeNode(project, (Change) userObject, null);
    }
    if (userObject instanceof VirtualFile) {
      return new ChangesBrowserFileNode(project, (VirtualFile) userObject);
    }
    if (userObject instanceof FilePath) {
      return new ChangesBrowserFilePathNode((FilePath) userObject);
    }
/*    if (userObject instanceof ChangeList) {
      return new ChangesBrowserChangeListNode(project, (ChangeList) userObject);
    }*/
    if (userObject == IGNORED_FILES_TAG) {
      return new ChangesBrowserIgnoredFilesNode(userObject);
    }
    if (userObject == LOCKED_FOLDERS_TAG) {
      return new ChangesBrowserLockedFoldersNode(project, userObject);
    }
    if (userObject instanceof ChangesBrowserLogicallyLockedFile) {
      return (ChangesBrowserNode) userObject;
    }
    return new ChangesBrowserNode(userObject);
  }

  @Override
  public void insert(MutableTreeNode newChild, int childIndex) {
    super.insert(newChild, childIndex);
    myCount = -1;
    myDirectoryCount = -1;
  }

  public int getCount() {
    if (myCount == -1) {
      myCount = toStream(children()).mapToInt(ChangesBrowserNode::getCount).sum();
    }
    return myCount;
  }

  public int getDirectoryCount() {
    if (myDirectoryCount == -1) {
      myDirectoryCount = (isDirectory() ? 1 : 0) + toStream(children()).mapToInt(ChangesBrowserNode::getDirectoryCount).sum();
    }
    return myDirectoryCount;
  }

  protected boolean isDirectory() {
    return false;
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
      .filter(userObject -> clazz.isAssignableFrom(userObject.getClass()))
      .map(clazz::cast);
  }

  @NotNull
  public List<VirtualFile> getAllFilesUnder() {
    return getFilesUnderStream().collect(Collectors.toList());
  }

  @NotNull
  public Stream<VirtualFile> getFilesUnderStream() {
    return toStream(breadthFirstEnumeration())
      .map(ChangesBrowserNode::getUserObject)
      .filter(userObject -> userObject instanceof VirtualFile)
      .map(VirtualFile.class::cast)
      .filter(VirtualFile::isValid);
  }

  @NotNull
  public List<FilePath> getAllFilePathsUnder() {
    return getFilePathsUnderStream().collect(Collectors.toList());
  }

  @NotNull
  public Stream<FilePath> getFilePathsUnderStream() {
    return toStream(breadthFirstEnumeration())
      .filter(ChangesBrowserNode::isLeaf)
      .map(ChangesBrowserNode::getUserObject)
      .filter(userObject -> userObject instanceof FilePath)
      .map(FilePath.class::cast);
  }

  @NotNull
  private static Stream<ChangesBrowserNode> toStream(@NotNull Enumeration enumeration) {
    //noinspection unchecked
    Iterator<ChangesBrowserNode> iterator = ContainerUtil.iterate((Enumeration<ChangesBrowserNode>)enumeration);
    Spliterator<ChangesBrowserNode> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL);

    return StreamSupport.stream(spliterator, false);
  }

  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    renderer.append(userObject.toString(), myAttributes);
    appendCount(renderer);
  }

  @NotNull
  protected String getCountText() {
    int count = getCount();
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

  public int getSortWeight() {
    return 9;
  }

  public int compareUserObjects(final Object o2) {
    return 0;
  }

  public void setAttributes(@NotNull SimpleTextAttributes attributes) {
    myAttributes = attributes;
  }
}
