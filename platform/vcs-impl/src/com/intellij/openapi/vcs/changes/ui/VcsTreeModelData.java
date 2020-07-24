// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ListSelection;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.containers.TreeTraversal;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
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
  public static VcsTreeModelData children(@NotNull ChangesBrowserNode<?> node) {
    return new AllUnderData(node);
  }


  @NotNull
  public static VcsTreeModelData allUnderTag(@NotNull JTree tree, @NotNull Object tag) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;

    ChangesBrowserNode<?> tagNode = findTagNode(tree, tag);
    if (tagNode == null) return new EmptyData();
    return new AllUnderData(tagNode);
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


  @NotNull
  public abstract Stream<ChangesBrowserNode<?>> rawNodesStream();

  @NotNull
  public Stream<ChangesBrowserNode<?>> nodesStream() {
    return rawNodesStream().filter(ChangesBrowserNode::isMeaningfulNode);
  }

  @NotNull
  public Stream<Object> rawUserObjectsStream() {
    return rawUserObjectsStream(Object.class);
  }

  @NotNull
  public <U> Stream<U> rawUserObjectsStream(@NotNull Class<U> clazz) {
    //noinspection unchecked
    return (Stream<U>)rawNodesStream().map(ChangesBrowserNode::getUserObject).filter(clazz::isInstance);
  }


  @NotNull
  public Stream<Object> userObjectsStream() {
    return userObjectsStream(Object.class);
  }

  @NotNull
  public <U> Stream<U> userObjectsStream(@NotNull Class<U> clazz) {
    //noinspection unchecked
    return (Stream<U>)nodesStream().map(ChangesBrowserNode::getUserObject).filter(clazz::isInstance);
  }


  @NotNull
  public List<Object> userObjects() {
    return userObjectsStream().collect(Collectors.toList());
  }

  @NotNull
  public <U> List<U> userObjects(@NotNull Class<U> clazz) {
    return userObjectsStream(clazz).collect(Collectors.toList());
  }


  private static class EmptyData extends VcsTreeModelData {
    EmptyData() {
    }

    @NotNull
    @Override
    public Stream<ChangesBrowserNode<?>> rawNodesStream() {
      return Stream.empty();
    }
  }

  private static class AllUnderData extends VcsTreeModelData {
    @NotNull private final ChangesBrowserNode<?> myNode;

    AllUnderData(@NotNull ChangesBrowserNode<?> node) {
      myNode = node;
    }

    @NotNull
    @Override
    public Stream<ChangesBrowserNode<?>> rawNodesStream() {
      return myNode.getNodesUnderStream();
    }
  }

  private static class SelectedData extends ExactlySelectedData {

    SelectedData(@NotNull JTree tree) {
      super(tree);
    }

    @NotNull
    @Override
    public Stream<ChangesBrowserNode<?>> rawNodesStream() {
      return super.rawNodesStream()
        .flatMap(ChangesBrowserNode::getNodesUnderStream)
        .distinct(); // filter out nodes that already were processed (because their parent selected too)
    }
  }

  private static class ExactlySelectedData extends VcsTreeModelData {
    @NotNull private final JTree myTree;

    ExactlySelectedData(@NotNull JTree tree) {
      myTree = tree;
    }

    @NotNull
    @Override
    public Stream<ChangesBrowserNode<?>> rawNodesStream() {
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) return Stream.empty();

      return Stream.of(paths).map(path -> (ChangesBrowserNode<?>)path.getLastPathComponent());
    }
  }

  private static class ExactlySelectedTagData extends VcsTreeModelData {
    @NotNull private final JTree myTree;
    @NotNull private final Object myTag;

    ExactlySelectedTagData(@NotNull JTree tree, @NotNull Object tag) {
      myTree = tree;
      myTag = tag;
    }

    @NotNull
    @Override
    public Stream<ChangesBrowserNode<?>> rawNodesStream() {
      ChangesBrowserNode<?> tagNode = findTagNode(myTree, myTag);
      if (tagNode == null) return Stream.empty();

      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) return Stream.empty();

      return Stream.of(paths)
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
    public Stream<ChangesBrowserNode<?>> rawNodesStream() {
      return super.rawNodesStream()
        .flatMap(ChangesBrowserNode::getNodesUnderStream)
        .distinct(); // filter out nodes that already were processed (because their parent selected too)
    }
  }

  private static class IncludedUnderData extends VcsTreeModelData {
    @NotNull private final ChangesTree myTree;
    @NotNull private final ChangesBrowserNode<?> myNode;

    IncludedUnderData(@NotNull ChangesTree tree, @NotNull ChangesBrowserNode<?> node) {
      myTree = tree;
      myNode = node;
    }

    @NotNull
    @Override
    public Stream<ChangesBrowserNode<?>> rawNodesStream() {
      Set<Object> included = myTree.getIncludedSet();
      return myNode.getNodesUnderStream().filter(node -> included.contains(node.getUserObject()));
    }
  }

  private static class AllExpandedByDefaultData extends VcsTreeModelData {
    @NotNull private final ChangesBrowserNode<?> myNode;

    AllExpandedByDefaultData(@NotNull ChangesBrowserNode<?> node) {
      myNode = node;
    }

    @NotNull
    @Override
    public Stream<ChangesBrowserNode<?>> rawNodesStream() {
      JBTreeTraverser<ChangesBrowserNode<?>> traverser = JBTreeTraverser.from(node -> {
        if (node.shouldExpandByDefault()) {
          return () -> {
            //noinspection unchecked
            Enumeration<ChangesBrowserNode<?>> children = (Enumeration)node.children();
            return ContainerUtil.iterate(children);
          };
        }
        else {
          return Collections.emptyList();
        }
      });
      return StreamEx.of(traverser.withRoot(myNode).traverse(TreeTraversal.PRE_ORDER_DFS).iterator());
    }
  }

  @NotNull
  public static ListSelection<Object> getListSelectionOrAll(@NotNull JTree tree) {
    List<Object> entries = selected(tree).userObjects();
    if (entries.size() > 1) {
      return ListSelection.createAt(entries, 0);
    }

    ChangesBrowserNode<?> selected = selected(tree).nodesStream().findFirst().orElse(null);

    List<Object> allEntries;
    if (underExpandByDefault(selected)) {
      allEntries = new AllExpandedByDefaultData(getRoot(tree)).userObjects();
    }
    else {
      allEntries = all(tree).userObjects();
    }

    if (allEntries.size() <= entries.size()) {
      return ListSelection.createAt(entries, 0);
    }
    else {
      int index = selected != null ? ContainerUtil.indexOfIdentity(allEntries, selected.getUserObject()) : 0;
      return ListSelection.createAt(allEntries, index);
    }
  }


  @Nullable
  public static Object getData(@Nullable Project project, @NotNull JTree tree, @NotNull String dataId) {
    if (VcsDataKeys.CHANGES.is(dataId)) {
      Change[] changes = mapToChange(selected(tree)).toArray(Change[]::new);
      if (changes.length != 0) return changes;
      return mapToChange(all(tree)).toArray(Change[]::new);
    }
    else if (VcsDataKeys.SELECTED_CHANGES.is(dataId) ||
             VcsDataKeys.SELECTED_CHANGES_IN_DETAILS.is(dataId)) {
      return mapToChange(selected(tree)).toArray(Change[]::new);
    }
    else if (VcsDataKeys.CHANGES_SELECTION.is(dataId)) {
      return getListSelectionOrAll(tree).map(entry -> ObjectUtils.tryCast(entry, Change.class));
    }
    else if (VcsDataKeys.CHANGE_LEAD_SELECTION.is(dataId)) {
      return mapToChange(exactlySelected(tree)).limit(1).toArray(Change[]::new);
    }
    else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      return mapToVirtualFile(selected(tree)).toArray(VirtualFile[]::new);
    }
    else if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      if (project == null) return null;
      return ChangesUtil.getNavigatableArray(project, mapToNavigatableFile(selected(tree)));
    }
    else if (VcsDataKeys.IO_FILE_ARRAY.is(dataId)) {
      return mapToIoFile(selected(tree)).toArray(File[]::new);
    }
    return null;
  }


  @NotNull
  private static Stream<Change> mapToChange(@NotNull VcsTreeModelData data) {
    return data.userObjectsStream()
      .filter(it -> it instanceof Change)
      .map(entry -> (Change)entry);
  }

  @NotNull
  private static Stream<VirtualFile> mapToNavigatableFile(@NotNull VcsTreeModelData data) {
    return data.userObjectsStream()
      .flatMap(entry -> {
        if (entry instanceof Change) {
          return ChangesUtil.getPathsCaseSensitive((Change)entry)
            .map(FilePath::getVirtualFile);
        }
        else if (entry instanceof VirtualFile) {
          return Stream.of((VirtualFile)entry);
        }
        else if (entry instanceof FilePath) {
          return Stream.of(((FilePath)entry).getVirtualFile());
        }
        return Stream.empty();
      })
      .filter(Objects::nonNull);
  }

  @NotNull
  private static Stream<VirtualFile> mapToVirtualFile(@NotNull VcsTreeModelData data) {
    return data.userObjectsStream()
      .map(entry -> {
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
      .filter(Objects::nonNull);
  }

  @NotNull
  private static Stream<File> mapToIoFile(@NotNull VcsTreeModelData data) {
    return data.userObjectsStream()
      .map(entry -> {
        if (entry instanceof Change) {
          FilePath path = ChangesUtil.getAfterPath((Change)entry);
          return path != null ? path.getIOFile() : null;
        }
        return null;
      })
      .filter(Objects::nonNull);
  }


  @Nullable
  private static ChangesBrowserNode<?> findTagNode(@NotNull JTree tree, @NotNull Object tag) {
    ChangesBrowserNode<?> root = (ChangesBrowserNode<?>)tree.getModel().getRoot();
    @SuppressWarnings({"unchecked", "rawtypes"})
    Enumeration<ChangesBrowserNode<?>> children = (Enumeration)root.children();
    Iterator<ChangesBrowserNode<?>> iterator = ContainerUtil.iterate(children);
    return ContainerUtil.find(iterator, node -> tag.equals(node.getUserObject()));
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

