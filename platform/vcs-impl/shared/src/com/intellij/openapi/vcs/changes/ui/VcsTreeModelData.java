// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.FileSelectInContext;
import com.intellij.ide.SelectInContext;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.vcs.VcsUtil;
import com.intellij.platform.vcs.changes.ChangesDataKeys;
import com.intellij.platform.vcs.changes.ChangesUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public abstract class VcsTreeModelData {
  public static @NotNull VcsTreeModelData all(@NotNull JTree tree) {
    return all(tree.getModel());
  }

  @NotNull
  public static VcsTreeModelData all(@NotNull TreeModel model) {
    assert model.getRoot() instanceof ChangesBrowserNode;
    return new AllUnderData((ChangesBrowserNode<?>)model.getRoot());
  }

  public static @NotNull VcsTreeModelData selected(@NotNull JTree tree) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;
    return new SelectedData(tree);
  }

  public static @NotNull VcsTreeModelData exactlySelected(@NotNull JTree tree) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;
    return new ExactlySelectedData(tree);
  }

  public static @NotNull VcsTreeModelData included(@NotNull ChangesTree tree) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;

    Set<Object> includedSet = tree.getIncludedSet();
    return new IncludedUnderData(includedSet, getRoot(tree));
  }

  public static @NotNull VcsTreeModelData allUnderTag(@NotNull JTree tree, @NotNull Object tag) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;

    ChangesBrowserNode<?> tagNode = findTagNode(tree, tag);
    if (tagNode == null) return new EmptyData();
    return new AllUnderData(tagNode);
  }

  public static @NotNull VcsTreeModelData allUnder(@NotNull ChangesBrowserNode<?> node) {
    return new AllUnderData(node);
  }

  public static @NotNull VcsTreeModelData selectedUnderTag(@NotNull JTree tree, @NotNull Object tag) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;
    return new SelectedTagData(tree, tag);
  }

  public static @NotNull VcsTreeModelData includedUnderTag(@NotNull ChangesTree tree, @NotNull Object tag) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;

    ChangesBrowserNode<?> tagNode = findTagNode(tree, tag);
    if (tagNode == null) return new EmptyData();

    Set<Object> includedSet = tree.getIncludedSet();
    return new IncludedUnderData(includedSet, tagNode);
  }


  /**
   * @deprecated use {@link #iterateNodes()}
   */
  @Deprecated(forRemoval = true)
  public final @NotNull Stream<ChangesBrowserNode<?>> nodesStream() {
    return iterateNodes().toStream();
  }

  /**
   * @deprecated use {@link #iterateUserObjects(Class)}
   */
  @Deprecated(forRemoval = true)
  public final @NotNull <U> Stream<U> userObjectsStream(@NotNull Class<U> clazz) {
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


  public final @NotNull List<Object> userObjects() {
    return iterateUserObjects().toList();
  }

  public final @NotNull <U> List<U> userObjects(@NotNull Class<U> clazz) {
    return iterateUserObjects(clazz).toList();
  }


  private static class EmptyData extends VcsTreeModelData {
    EmptyData() {
    }

    @Override
    public @NotNull JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
      return JBIterable.empty();
    }
  }

  private static class AllUnderData extends VcsTreeModelData {
    private final @NotNull ChangesBrowserNode<?> myNode;

    AllUnderData(@NotNull ChangesBrowserNode<?> node) {
      myNode = node;
    }

    @Override
    public @NotNull JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
      return myNode.traverse();
    }
  }

  private static class SelectedData extends ExactlySelectedData {

    SelectedData(@NotNull JTree tree) {
      super(tree);
    }

    @Override
    public @NotNull JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
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

    @Override
    public @NotNull JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
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

    @Override
    public @NotNull JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
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

    @Override
    public @NotNull JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
      return super.iterateRawNodes()
        .flatMap(ChangesBrowserNode::traverse)
        .unique(); // filter out nodes that already were processed (because their parent selected too)
    }
  }

  private static class IncludedUnderData extends VcsTreeModelData {
    private final ChangesBrowserNode<?> myNode;
    private final Set<Object> myIncluded;

    IncludedUnderData(@NotNull Set<Object> includedSet, @NotNull ChangesBrowserNode<?> node) {
      myNode = node;
      myIncluded = includedSet;
    }

    @Override
    public @NotNull JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
      return myNode.traverse().filter(node -> myIncluded.contains(node.getUserObject()));
    }
  }

  private static class AllExpandedByDefaultData extends VcsTreeModelData {
    private final @NotNull ChangesBrowserNode<?> myNode;

    AllExpandedByDefaultData(@NotNull ChangesBrowserNode<?> node) {
      myNode = node;
    }

    @Override
    public @NotNull JBIterable<ChangesBrowserNode<?>> iterateRawNodes() {
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

  public static @NotNull ListSelection<Object> getListSelectionOrAll(@NotNull JTree tree) {
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

  public static void uiDataSnapshot(@NotNull DataSink sink, @Nullable Project project, @NotNull JTree tree) {
    sink.set(CommonDataKeys.PROJECT, project);

    Change[] changes = mapToChange(selected(tree)).toArray(Change.EMPTY_CHANGE_ARRAY);
    sink.set(ChangesDataKeys.CHANGES,
             changes.length != 0 ? changes : mapToChange(all(tree)).toArray(Change.EMPTY_CHANGE_ARRAY));
    sink.set(ChangesDataKeys.SELECTED_CHANGES, changes);
    sink.set(ChangesDataKeys.SELECTED_CHANGES_IN_DETAILS, changes);
    sink.set(ChangesDataKeys.CHANGES_SELECTION,
             getListSelectionOrAll(tree).map(entry -> ObjectUtils.tryCast(entry, Change.class)));
    sink.set(ChangesDataKeys.CHANGE_LEAD_SELECTION,
             mapToChange(exactlySelected(tree)).toArray(Change.EMPTY_CHANGE_ARRAY));
    sink.set(ChangesDataKeys.FILE_PATHS, mapToFilePath(selected(tree)));

    VcsTreeModelData treeSelection = selected(tree);
    VcsTreeModelData exactSelection = exactlySelected(tree);
    sink.lazy(SelectInContext.DATA_KEY, () -> {
      if (project == null) return null;
      VirtualFile file = mapObjectToVirtualFile(exactSelection.iterateRawUserObjects()).first();
      if (file == null) return null;
      return new FileSelectInContext(project, file, null);
    });
    sink.lazy(ChangesDataKeys.VIRTUAL_FILES, () -> {
      return mapToVirtualFile(treeSelection);
    });
    sink.lazy(CommonDataKeys.VIRTUAL_FILE, () -> {
      return findSelectedVirtualFile(tree);
    });
    sink.lazy(CommonDataKeys.VIRTUAL_FILE_ARRAY, () -> {
      return mapToVirtualFile(treeSelection).toArray(VirtualFile.EMPTY_ARRAY);
    });
    if (project != null) {
      sink.lazy(CommonDataKeys.NAVIGATABLE_ARRAY, () -> {
        return ChangesUtil.getNavigatableArray(project, mapToNavigatableFile(treeSelection));
      });
    }
  }

  private static @NotNull JBIterable<Change> mapToChange(@NotNull VcsTreeModelData data) {
    return data.iterateUserObjects()
      .filter(Change.class);
  }

  @ApiStatus.Internal
  public static @NotNull JBIterable<VirtualFile> mapToNavigatableFile(@NotNull VcsTreeModelData data) {
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

  @ApiStatus.Internal
  public static @Nullable VirtualFile findSelectedVirtualFile(@NotNull JTree tree) {
    TreePath leadSelectionPath = tree.getLeadSelectionPath();
    if (leadSelectionPath == null) return null;
    return treePathToVirtualFile(leadSelectionPath);
  }

  private static @Nullable VirtualFile treePathToVirtualFile(@NotNull TreePath path) {
    VirtualFile virtualFile = objectToVirtualFile(((ChangesBrowserNode<?>)path.getLastPathComponent()).getUserObject());
    if (virtualFile == null || !virtualFile.isValid()) return null;
    return virtualFile;
  }

  @ApiStatus.Internal
  public static @NotNull JBIterable<VirtualFile> mapToVirtualFile(@NotNull VcsTreeModelData data) {
    return mapObjectToVirtualFile(data.iterateUserObjects());
  }

  @ApiStatus.Internal
  public static @NotNull JBIterable<VirtualFile> mapObjectToVirtualFile(@NotNull JBIterable<Object> userObjects) {
    return userObjects
      .map(VcsTreeModelData::objectToVirtualFile)
      .filterNotNull()
      .filter(VirtualFile::isValid);
  }

  private static @Nullable VirtualFile objectToVirtualFile(@Nullable Object userObject) {
    if (userObject instanceof Change) {
      FilePath path = ChangesUtil.getAfterPath((Change)userObject);
      return path != null ? path.getVirtualFile() : null;
    }
    else if (userObject instanceof VirtualFile) {
      return (VirtualFile)userObject;
    }
    else if (userObject instanceof FilePath) {
      return ((FilePath)userObject).getVirtualFile();
    }
    return null;
  }

  @ApiStatus.Internal
  public static @NotNull JBIterable<FilePath> mapToFilePath(@NotNull VcsTreeModelData data) {
    return data.iterateUserObjects()
      .map(VcsTreeModelData::mapUserObjectToFilePath)
      .filterNotNull();
  }

  public static @Nullable FilePath mapUserObjectToFilePath(@Nullable Object userObject) {
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
  public static @NotNull JBIterable<VirtualFile> mapToExactVirtualFile(@NotNull VcsTreeModelData data) {
    return data.iterateUserObjects()
      .map(object -> {
        if (object instanceof VirtualFile) return (VirtualFile)object;
        if (object instanceof FilePath) return ((FilePath)object).getVirtualFile();
        return null;
      })
      .filterNotNull()
      .filter(VirtualFile::isValid);
  }


  public static @Nullable ChangesBrowserNode<?> findTagNode(@NotNull JTree tree, @NotNull Object tag) {
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

  private static @NotNull ChangesBrowserNode<?> getRoot(@NotNull JTree tree) {
    return (ChangesBrowserNode<?>)tree.getModel().getRoot();
  }
}

