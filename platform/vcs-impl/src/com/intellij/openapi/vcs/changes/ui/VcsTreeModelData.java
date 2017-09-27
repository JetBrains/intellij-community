// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreePath;
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
}

