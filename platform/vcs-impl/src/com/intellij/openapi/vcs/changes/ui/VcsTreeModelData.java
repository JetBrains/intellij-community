// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.FileSelectInContext;
import com.intellij.ide.SelectInContext;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.CompositeDataProvider;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public abstract class VcsTreeModelData {
  @NotNull
  public static VcsTreeModelData all(@NotNull JTree tree) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;
    return new AllUnderData(getRoot(tree));
  }

  @NotNull
  public static VcsTreeModelData selected(@NotNull JTree tree) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;
    return new SelectedData(tree);
  }

  @NotNull
  public static VcsTreeModelData exactlySelected(@NotNull JTree tree) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;
    return new ExactlySelectedData(tree);
  }

  @NotNull
  public static VcsTreeModelData included(@NotNull ChangesTree tree) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;
    return new IncludedUnderData(tree, getRoot(tree));
  }

  @NotNull
  public static VcsTreeModelData allUnderTag(@NotNull JTree tree, @NotNull Object tag) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;

    ChangesBrowserNode<?> tagNode = findTagNode(tree, tag);
    if (tagNode == null) return new EmptyData();
    return new AllUnderData(tagNode);
  }

  @NotNull
  public static VcsTreeModelData allUnder(@NotNull ChangesBrowserNode<?> node) {
    return new AllUnderData(node);
  }

  @NotNull
  public static VcsTreeModelData selectedUnderTag(@NotNull JTree tree, @NotNull Object tag) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;
    return new SelectedTagData(tree, tag);
  }

  @NotNull
  public static VcsTreeModelData includedUnderTag(@NotNull ChangesTree tree, @NotNull Object tag) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;

    ChangesBrowserNode<?> tagNode = findTagNode(tree, tag);
    if (tagNode == null) return new EmptyData();

    return new IncludedUnderData(tree, tagNode);
  }


  /**
   * @deprecated use {@link #iterateNodes()}
   */
  @NotNull
  @Deprecated
  public final Stream<ChangesBrowserNode<?>> nodesStream() {
    return iterateNodes().toStream();
  }

  /**
   * @deprecated use {@link #iterateUserObjects(Class)}
   */
  @NotNull
  @Deprecated
  public final <U> Stream<U> userObjectsStream(@NotNull Class<U> clazz) {
    return iterateUserObjects(clazz).toStream();
  }


  public abstract @NotNull JBIterable<ChangesBrowserNode<?>> iterateRawNodes();

  public final @NotNull JBIterable<ChangesBrowserNode<?>> iterateNodes() {
    return iterateRawNodes().filter(ChangesBrowserNode::isMeaningfulNode);
  }

  public final @NotNull JBIterable<Object> iterateRawUserObjects() {
    return iterateRawNodes().map(ChangesBrowserNode::getUserObject);
  }

  public final <U> @NotNull JBIterable<U> iterateRawUserObjects(@NotNull Class<U> clazz) {
    return iterateRawNodes().map(ChangesBrowserNode::getUserObject).filter(clazz);
  }

  public final @NotNull JBIterable<Object> iterateUserObjects() {
    return iterateNodes().map(ChangesBrowserNode::getUserObject);
  }

  public final <U> @NotNull JBIterable<U> iterateUserObjects(@NotNull Class<U> clazz) {
    return iterateNodes().map(ChangesBrowserNode::getUserObject).filter(clazz);
  }


  @NotNull
  public final List<Object> userObjects() {
    return iterateUserObjects().toList();
  }

  @NotNull
  public final <U> List<U> userObjects(@NotNull Class<U> clazz) {
    return iterateUserObjects(clazz).toList();
  }


  private static class EmptyData extends VcsTreeModelData {
    EmptyData() {
    }

    @NotNull
    @Override
    public JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
      return JBIterable.empty();
    }
  }

  private static class AllUnderData extends VcsTreeModelData {
    @NotNull private final ChangesBrowserNode<?> myNode;

    AllUnderData(@NotNull ChangesBrowserNode<?> node) {
      myNode = node;
    }

    @NotNull
    @Override
    public JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
      return myNode.traverse();
    }
  }

  private static class SelectedData extends ExactlySelectedData {

    SelectedData(@NotNull JTree tree) {
      super(tree);
    }

    @NotNull
    @Override
    public JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
      return super.iterateRawNodes()
        .flatMap(ChangesBrowserNode::traverse)
        .unique(); // filter out nodes that already were processed (because their parent selected too)
    }
  }

  private static class ExactlySelectedData extends VcsTreeModelData {
    private final TreePath[] myPaths;

    ExactlySelectedData(@NotNull JTree tree) {
      myPaths = tree.getSelectionPaths();
    }

    @NotNull
    @Override
    public JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
      TreePath[] paths = myPaths;
      if (paths == null) return JBIterable.empty();

      return JBIterable.of(paths).map(path -> (ChangesBrowserNode<?>)path.getLastPathComponent());
    }
  }

  private static class ExactlySelectedTagData extends VcsTreeModelData {
    private final TreePath[] myPaths;
    private final ChangesBrowserNode<?> myTagNode;

    ExactlySelectedTagData(@NotNull JTree tree, @NotNull Object tag) {
      myPaths = tree.getSelectionPaths();
      myTagNode = findTagNode(tree, tag);
    }

    @NotNull
    @Override
    public JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
      ChangesBrowserNode<?> tagNode = myTagNode;
      if (tagNode == null) return JBIterable.empty();

      TreePath[] paths = myPaths;
      if (paths == null) return JBIterable.empty();

      return JBIterable.of(paths)
        .filter(path -> (path.getPathCount() <= 1 ||
                         path.getPathComponent(1) == tagNode))
        .map(path -> (ChangesBrowserNode<?>)path.getLastPathComponent());
    }
  }

  private static class SelectedTagData extends ExactlySelectedTagData {

    SelectedTagData(@NotNull JTree tree, @NotNull Object tag) {
      super(tree, tag);
    }

    @NotNull
    @Override
    public JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
      return super.iterateRawNodes()
        .flatMap(ChangesBrowserNode::traverse)
        .unique(); // filter out nodes that already were processed (because their parent selected too)
    }
  }

  private static class IncludedUnderData extends VcsTreeModelData {
    private final ChangesBrowserNode<?> myNode;
    private final Set<Object> myIncluded;

    IncludedUnderData(@NotNull ChangesTree tree, @NotNull ChangesBrowserNode<?> node) {
      myNode = node;
      myIncluded = tree.getIncludedSet();
    }

    @NotNull
    @Override
    public JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
      return myNode.traverse().filter(node -> myIncluded.contains(node.getUserObject()));
    }
  }

  private static class AllExpandedByDefaultData extends VcsTreeModelData {
    @NotNull private final ChangesBrowserNode<?> myNode;

    AllExpandedByDefaultData(@NotNull ChangesBrowserNode<?> node) {
      myNode = node;
    }

    @NotNull
    @Override
    public JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
      JBTreeTraverser<ChangesBrowserNode<?>> traverser = JBTreeTraverser.from(node -> {
        if (node.shouldExpandByDefault()) {
          return node.iterateNodeChildren();
        }
        else {
          return JBIterable.empty();
        }
      });
      return traverser.withRoot(myNode).preOrderDfsTraversal();
    }
  }

  @NotNull
  public static ListSelection<Object> getListSelectionOrAll(@NotNull JTree tree) {
    List<Object> entries = selected(tree).userObjects();
    if (entries.size() > 1) {
      return ListSelection.createAt(entries, 0)
        .asExplicitSelection();
    }

    ChangesBrowserNode<?> selected = selected(tree).iterateNodes().first();

    List<Object> allEntries;
    if (underExpandByDefault(selected)) {
      allEntries = new AllExpandedByDefaultData(getRoot(tree)).userObjects();
    }
    else {
      allEntries = all(tree).userObjects();
    }

    if (allEntries.size() <= entries.size()) {
      return ListSelection.createAt(entries, 0)
        .asExplicitSelection();
    }
    else {
      int index = selected != null ? ContainerUtil.indexOfIdentity(allEntries, selected.getUserObject()) : 0;
      return ListSelection.createAt(allEntries, index);
    }
  }


  @Nullable
  public static Object getData(@Nullable Project project, @NotNull JTree tree, @NotNull String dataId) {
    return getDataOrSuper(project, tree, dataId, null);
  }

  @Nullable
  public static Object getDataOrSuper(@Nullable Project project, @NotNull JTree tree, @NotNull String dataId,
                                      @Nullable Object superProviderData) {
    if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
      VcsTreeModelData treeSelection = selected(tree);
      VcsTreeModelData exactSelection = exactlySelected(tree);
      return CompositeDataProvider.compose(slowId -> getSlowData(project, treeSelection, exactSelection, slowId),
                                           (DataProvider)superProviderData);
    }

    Object data = getFastData(project, tree, dataId);
    if (data != null) {
      return data;
    }

    return superProviderData;
  }

  @Nullable
  private static Object getFastData(@Nullable Project project, @NotNull JTree tree, @NotNull String dataId) {
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return project;
    }
    else if (VcsDataKeys.CHANGES.is(dataId)) {
      Change[] changes = mapToChange(selected(tree)).toArray(Change.EMPTY_CHANGE_ARRAY);
      if (changes.length != 0) return changes;
      return mapToChange(all(tree)).toArray(Change.EMPTY_CHANGE_ARRAY);
    }
    else if (VcsDataKeys.SELECTED_CHANGES.is(dataId) ||
             VcsDataKeys.SELECTED_CHANGES_IN_DETAILS.is(dataId)) {
      return mapToChange(selected(tree)).toArray(Change.EMPTY_CHANGE_ARRAY);
    }
    else if (VcsDataKeys.CHANGES_SELECTION.is(dataId)) {
      return getListSelectionOrAll(tree).map(entry -> ObjectUtils.tryCast(entry, Change.class));
    }
    else if (VcsDataKeys.CHANGE_LEAD_SELECTION.is(dataId)) {
      return mapToChange(exactlySelected(tree)).toArray(Change.EMPTY_CHANGE_ARRAY);
    }
    else if (VcsDataKeys.FILE_PATHS.is(dataId)) {
      return mapToFilePath(selected(tree));
    }
    return null;
  }

  @Nullable
  private static Object getSlowData(@Nullable Project project,
                                    @NotNull VcsTreeModelData treeSelection,
                                    @NotNull VcsTreeModelData exactSelection,
                                    @NotNull String slowId) {
    if (SelectInContext.DATA_KEY.is(slowId)) {
      if (project == null) return null;
      VirtualFile file = mapObjectToVirtualFile(exactSelection.iterateRawUserObjects()).first();
      if (file == null) return null;
      return new FileSelectInContext(project, file, null);
    }
    else if (VcsDataKeys.VIRTUAL_FILES.is(slowId)) {
      return mapToVirtualFile(treeSelection);
    }
    else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(slowId)) {
      return mapToVirtualFile(treeSelection).toArray(VirtualFile.EMPTY_ARRAY);
    }
    else if (CommonDataKeys.NAVIGATABLE_ARRAY.is(slowId)) {
      if (project == null) return null;
      return ChangesUtil.getNavigatableArray(project, mapToNavigatableFile(treeSelection));
    }
    return null;
  }

  @NotNull
  private static JBIterable<Change> mapToChange(@NotNull VcsTreeModelData data) {
    return data.iterateUserObjects()
      .filter(Change.class);
  }

  @NotNull
  static JBIterable<VirtualFile> mapToNavigatableFile(@NotNull VcsTreeModelData data) {
    return data.iterateUserObjects()
      .flatMap(entry -> {
        if (entry instanceof Change) {
          return ChangesUtil.iteratePathsCaseSensitive((Change)entry)
            .map(FilePath::getVirtualFile);
        }
        else if (entry instanceof VirtualFile) {
          return JBIterable.of((VirtualFile)entry);
        }
        else if (entry instanceof FilePath) {
          return JBIterable.of(((FilePath)entry).getVirtualFile());
        }
        return JBIterable.empty();
      })
      .filterNotNull()
      .filter(VirtualFile::isValid);
  }

  @NotNull
  static JBIterable<VirtualFile> mapToVirtualFile(@NotNull VcsTreeModelData data) {
    return mapObjectToVirtualFile(data.iterateUserObjects());
  }

  @NotNull
  static JBIterable<VirtualFile> mapObjectToVirtualFile(@NotNull JBIterable<Object> userObjects) {
    return userObjects.map(entry -> {
        if (entry instanceof Change) {
          FilePath path = ChangesUtil.getAfterPath((Change)entry);
          return path != null ? path.getVirtualFile() : null;
        }
        else if (entry instanceof VirtualFile) {
          return (VirtualFile)entry;
        }
        else if (entry instanceof FilePath) {
          return ((FilePath)entry).getVirtualFile();
        }
        return null;
      })
      .filterNotNull()
      .filter(VirtualFile::isValid);
  }

  @NotNull
  static JBIterable<FilePath> mapToFilePath(@NotNull VcsTreeModelData data) {
    return data.iterateUserObjects()
      .map(VcsTreeModelData::mapUserObjectToFilePath)
      .filterNotNull();
  }

  @Nullable
  public static FilePath mapUserObjectToFilePath(@Nullable Object userObject) {
    if (userObject instanceof Change change) {
      return ChangesUtil.getFilePath(change);
    }
    else if (userObject instanceof VirtualFile file) {
      return VcsUtil.getFilePath(file);
    }
    else if (userObject instanceof FilePath filePath) {
      return filePath;
    }
    return null;
  }

  /**
   * @see ChangesListView#EXACTLY_SELECTED_FILES_DATA_KEY
   */
  @NotNull
  public static JBIterable<VirtualFile> mapToExactVirtualFile(@NotNull VcsTreeModelData data) {
    return data.iterateUserObjects()
      .map(object -> {
        if (object instanceof VirtualFile) return (VirtualFile)object;
        if (object instanceof FilePath) return ((FilePath)object).getVirtualFile();
        return null;
      })
      .filterNotNull()
      .filter(VirtualFile::isValid);
  }


  @Nullable
  public static ChangesBrowserNode<?> findTagNode(@NotNull JTree tree, @NotNull Object tag) {
    ChangesBrowserNode<?> root = (ChangesBrowserNode<?>)tree.getModel().getRoot();
    return root.iterateNodeChildren().find(node -> tag.equals(node.getUserObject()));
  }

  private static boolean underExpandByDefault(@Nullable ChangesBrowserNode<?> node) {
    while (node != null) {
      if (!node.shouldExpandByDefault()) return false;
      node = node.getParent();
    }
    return true;
  }

  @NotNull
  private static ChangesBrowserNode<?> getRoot(@NotNull JTree tree) {
    return (ChangesBrowserNode<?>)tree.getModel().getRoot();
  }
}

