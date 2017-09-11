// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class VcsTreeModelData {
  @NotNull
  public static VcsTreeModelData all(@NotNull JTree tree) {
    assert tree.getModel().getRoot() instanceof ChangesBrowserNode;
    ChangesBrowserNode<?> root = (ChangesBrowserNode<?>)tree.getModel().getRoot();
    return new AllNodesUnder(root);
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
    return new IncludedData(tree);
  }

  @NotNull
  public static VcsTreeModelData children(@NotNull ChangesBrowserNode<?> node) {
    return new AllNodesUnder(node);
  }


  @NotNull
  protected abstract Stream<ChangesBrowserNode> rawNodesStream();

  @NotNull
  public Stream<ChangesBrowserNode> nodesStream() {
    return rawNodesStream().filter(ChangesBrowserNode::isMeaningfulNode);
  }


  @NotNull
  public Stream<Object> userObjectsStream() {
    return nodesStream().map(ChangesBrowserNode::getUserObject).filter(Objects::nonNull);
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


  private static class AllNodesUnder extends VcsTreeModelData {
    @NotNull private final ChangesBrowserNode<?> myNode;

    public AllNodesUnder(@NotNull ChangesBrowserNode<?> node) {
      myNode = node;
    }

    @NotNull
    @Override
    public Stream<ChangesBrowserNode> rawNodesStream() {
      return myNode.getNodesUnderStream();
    }
  }

  private static class SelectedData extends VcsTreeModelData {
    @NotNull private final JTree myTree;

    public SelectedData(@NotNull JTree tree) {
      myTree = tree;
    }

    @NotNull
    @Override
    public Stream<ChangesBrowserNode> rawNodesStream() {
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) return Stream.empty();

      return Stream.of(paths)
        .map(path -> (ChangesBrowserNode)path.getLastPathComponent())
        .<ChangesBrowserNode>flatMap(ChangesBrowserNode::getNodesUnderStream)
        .distinct(); // filter out nodes that already were processed (because their parent selected too)
    }
  }

  private static class ExactlySelectedData extends VcsTreeModelData {
    @NotNull private final JTree myTree;

    public ExactlySelectedData(@NotNull JTree tree) {
      myTree = tree;
    }

    @NotNull
    @Override
    public Stream<ChangesBrowserNode> rawNodesStream() {
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) return Stream.empty();

      return Stream.of(paths).map(path -> (ChangesBrowserNode)path.getLastPathComponent());
    }
  }

  private static class IncludedData extends VcsTreeModelData {
    @NotNull private final ChangesTree myTree;

    public IncludedData(@NotNull ChangesTree tree) {
      myTree = tree;
    }

    @NotNull
    @Override
    public Stream<ChangesBrowserNode> rawNodesStream() {
      Set<Object> included = myTree.getIncludedSet();
      ChangesBrowserNode<?> root = (ChangesBrowserNode<?>)myTree.getModel().getRoot();
      return root.getNodesUnderStream().filter(node -> included.contains(node.getUserObject()));
    }
  }


  @NotNull
  public static ListSelection<Object> getListSelection(@NotNull JTree tree) {
    List<Object> entries = selected(tree).userObjects();
    Object selection = ContainerUtil.getFirstItem(entries);

    if (entries.size() < 2) {
      List<Object> allEntries = all(tree).userObjects();
      if (allEntries.size() > 1 || entries.isEmpty()) {
        entries = allEntries;
      }
    }

    return ListSelection.create(entries, selection);
  }


  @Nullable
  public static Object getData(@Nullable Project project, @NotNull JTree tree, String dataId) {
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
      return getListSelection(tree).map(entry -> ObjectUtils.tryCast(entry, Change.class));
    }
    else if (VcsDataKeys.CHANGE_LEAD_SELECTION.is(dataId)) {
      return mapToChange(exactlySelected(tree)).limit(1).toArray(Change[]::new);
    }
    else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      return mapToVirtualFile(selected(tree)).toArray(VirtualFile[]::new);
    }
    else if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      if (project == null) return null;
      return ChangesUtil.getNavigatableArray(project, mapToVirtualFile(selected(tree)));
    }
    else if (VcsDataKeys.IO_FILE_ARRAY.is(dataId)) {
      return mapToIoFile(selected(tree)).toArray(File[]::new);
    }
    return null;
  }


  @NotNull
  private static Stream<Change> mapToChange(@NotNull VcsTreeModelData data) {
    return data.userObjectsStream().filter(it -> it instanceof Change)
      .map(entry -> {
        if (entry instanceof Change) {
          return (Change)entry;
        }
        return null;
      })
      .filter(Objects::nonNull);
  }

  @NotNull
  private static Stream<VirtualFile> mapToVirtualFile(@NotNull VcsTreeModelData data) {
    return data.userObjectsStream()
      .flatMap(entry -> {
        if (entry instanceof Change) {
          return ChangesUtil.getPathsCaseSensitive((Change)entry)
            .map(FilePath::getVirtualFile);
        }
        else if (entry instanceof VirtualFile) {
          return Stream.of((VirtualFile)entry);
        }
        return Stream.empty();
      })
      .filter(Objects::nonNull);
  }

  @NotNull
  private static Stream<File> mapToIoFile(@NotNull VcsTreeModelData data) {
    return data.userObjectsStream()
      .map(entry -> {
        if (entry instanceof Change) {
          ContentRevision afterRevision = ((Change)entry).getAfterRevision();
          if (afterRevision == null) return null;
          return afterRevision.getFile().getIOFile();
        }
        return null;
      })
      .filter(Objects::nonNull);
  }
}

