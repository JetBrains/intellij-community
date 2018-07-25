// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.project;

import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.ui.tree.BaseTreeModel;
import com.intellij.ui.tree.TreeCollector;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static com.intellij.ui.tree.TreePathUtil.pathToCustomNode;
import static com.intellij.ui.tree.project.ProjectFileNode.findArea;
import static java.util.Collections.emptyList;

public final class ProjectFileTreeModel extends BaseTreeModel<ProjectFileNode> implements InvokerSupplier {
  private final Invoker invoker = new Invoker.BackgroundThread(this);
  private final ProjectFileNodeUpdater updater;
  private final ProjectNode root;

  public ProjectFileTreeModel(@NotNull Project project) {
    root = new ProjectNode(project);
    updater = new ProjectFileNodeUpdater(project, invoker) {
      @Override
      protected void update(boolean fromRoot, @NotNull Set<VirtualFile> updatedFiles) {
        boolean filtered = root.filter != null;
        SmartHashSet<Node> nodes = fromRoot || filtered ? null : new SmartHashSet<>();
        root.children.forEach(root -> root.invalidateChildren(node -> {
          if (!updatedFiles.contains(node.file)) return true;
          if (filtered) node.resetParentVisibility();
          if (nodes == null) return false;
          Node n = node.file.isDirectory() ? node : node.parent;
          if (n == null) return false;
          for (Node p = n.parent; p != null; p = p.parent) {
            if (nodes.contains(p)) return false;
          }
          nodes.add(n);
          return false;
        }));
        if (nodes != null) {
          nodes.forEach(node -> {
            TreePath path = pathToCustomNode(node, child -> child.parent);
            if (path != null) pathChanged(path);
          });
        }
        else {
          if (fromRoot) root.valid = false; // need to reload content roots
          pathChanged(null);
        }
      }
    };
  }

  @NotNull
  @Override
  public Invoker getInvoker() {
    return invoker;
  }

  public boolean isValidThread() {
    return invoker.isValidThread();
  }

  public void onValidThread(@NotNull Runnable task) {
    invoker.invokeLaterIfNeeded(task);
  }

  @Override
  public Object getRoot() {
    return root;
  }

  @Override
  public boolean isLeaf(Object object) {
    return root != object && super.isLeaf(object);
  }

  @Override
  public int getIndexOfChild(Object parent, Object object) {
    Node node = object instanceof Node ? (Node)object : null;
    return node == null || node.parent != parent ? -1 : super.getIndexOfChild(parent, object);
  }

  @NotNull
  @Override
  public List<ProjectFileNode> getChildren(Object object) {
    Node node = object instanceof Node && isValidThread() ? (Node)object : null;
    if (node == null) return emptyList();
    List<?> children = node.getChildren();
    if (children.isEmpty()) return emptyList();
    List<ProjectFileNode> result = new SmartList<>();
    VirtualFileFilter filter = root.filter;
    for (Object child : children) {
      if (child instanceof FileNode && isVisible((FileNode)child, filter)) {
        result.add((FileNode)child);
      }
    }
    return result;
  }

  private static boolean isVisible(@NotNull FileNode node, @Nullable VirtualFileFilter filter) {
    if (!node.getVirtualFile().isValid()) return false;
    if (filter == null) return true;
    ThreeState visibility = node.visibility;
    if (visibility == ThreeState.NO) return false;
    if (visibility == ThreeState.YES) return true;
    boolean visible = filter.accept(node.file);
    if (!visible && node.file.isDirectory()) {
      List<FileNode> children = node.getChildren();
      visible = !children.stream().allMatch(child -> child.visibility == ThreeState.NO) &&
                (children.stream().anyMatch(child -> child.visibility == ThreeState.YES) ||
                 children.stream().anyMatch(child -> isVisible(child, filter)));
    }
    node.visibility = ThreeState.fromBoolean(visible);
    return visible;
  }

  @NotNull
  private static Module[] getModules(@NotNull Project project) {
    ModuleManager manager = ModuleManager.getInstance(project);
    return manager == null ? Module.EMPTY_ARRAY : manager.getModules();
  }

  @NotNull
  private static VirtualFile[] getContentRoots(@NotNull Module module) {
    ModuleRootManager manager = module.isDisposed() ? null : ModuleRootManager.getInstance(module);
    return manager == null ? VirtualFile.EMPTY_ARRAY : manager.getContentRoots();
  }

  public void setFilter(@Nullable VirtualFileFilter filter) {
    onValidThread(() -> {
      if (root.filter == null && filter == null) return;
      root.filter = filter;
      root.resetVisibility();
      pathChanged(null);
    });
  }

  public void setShowModules(boolean showModules) {
    onValidThread(() -> {
      if (root.showModules == showModules) return;
      root.showModules = showModules;
      updater.updateFromRoot();
    });
  }

  private void pathChanged(@Nullable TreePath path) {
    onValidThread(() -> treeStructureChanged(path, null, null));
  }


  private static final class Mapper<N extends FileNode> implements BiFunction<VirtualFile, Object, N> {
    private final HashMap<VirtualFile, N> map = new HashMap<>();
    private final BiFunction<? super VirtualFile, ? super Object, ? extends N> function;

    Mapper(@NotNull List<N> list, @NotNull BiFunction<? super VirtualFile, ? super Object, ? extends N> function) {
      list.forEach(node -> map.put(node.file, node));
      this.function = function;
    }

    @NotNull
    @Override
    public final N apply(VirtualFile file, Object id) {
      N node = map.isEmpty() ? null : map.remove(file);
      return node != null && node.id.equals(id) ? node : function.apply(file, id);
    }
  }


  private static abstract class Node<FN extends FileNode> {
    volatile Node parent;
    volatile ThreeState visibility;
    volatile List<FN> children = emptyList();
    volatile boolean valid;

    @NotNull
    abstract List<FN> getChildren(@NotNull List<FN> oldList);

    final List<FN> getChildren() {
      List<FN> oldList = children;
      if (valid) return oldList;
      List<FN> newList = getChildren(oldList);
      oldList.forEach(node -> node.parent = null);
      newList.forEach(node -> node.parent = this);
      children = newList;
      valid = true;
      return newList;
    }

    final void resetVisibility() {
      visibility = null;
      children.forEach(Node::resetVisibility);
    }

    final void resetParentVisibility() {
      for (Node node = parent; node != null; node = node.parent) {
        node.visibility = null;
      }
    }

    @SuppressWarnings("SameParameterValue")
    final <N> N findParent(Class<N> type) {
      for (Node node = this; node != null; node = node.parent) {
        if (type.isInstance(node)) return type.cast(node);
      }
      return null;
    }
  }


  private static class ProjectNode extends Node<RootNode> {
    volatile VirtualFileFilter filter;
    volatile boolean showModules;
    final Project project;

    ProjectNode(@NotNull Project project) {
      this.project = project;
    }

    @Override
    public String toString() {
      return project.getName();
    }

    @NotNull
    @Override
    List<RootNode> getChildren(@NotNull List<RootNode> oldList) {
      List<RootNode> list = new SmartList<>();
      Mapper<RootNode> mapper = new Mapper<>(oldList, RootNode::new);
      TreeCollector<VirtualFile> collector = showModules ? null : TreeCollector.createFileRootsCollector();
      VirtualFile ancestor = project.getBaseDir();
      if (ancestor != null && project == findArea(ancestor, project)) {
        if (collector != null) {
          collector.add(ancestor);
        }
        else {
          list.add(mapper.apply(ancestor, project));
        }
      }
      for (Module module : getModules(project)) {
        for (VirtualFile file : getContentRoots(module)) {
          if (collector != null) {
            collector.add(file);
          }
          else {
            list.add(mapper.apply(file, module));
          }
        }
      }
      if (collector != null) collector.get().forEach(file -> list.add(mapper.apply(file, file)));
      return list;
    }
  }


  private static class FileNode extends Node<FileNode> implements ProjectFileNode {
    final VirtualFile file;
    final Object id;

    FileNode(@NotNull VirtualFile file, @NotNull Object id) {
      this.file = file;
      this.id = id;
    }

    @NotNull
    @Override
    public Object getRootID() {
      return id;
    }

    @NotNull
    public VirtualFile getVirtualFile() {
      return file;
    }

    @Override
    public String toString() {
      return file.getName();
    }

    @NotNull
    @Override
    List<FileNode> getChildren(@NotNull List<FileNode> oldList) {
      visibility = ThreeState.NO;

      VirtualFile file = getVirtualFile();
      if (!file.isValid()) return emptyList();

      ProjectNode parent = findParent(ProjectNode.class);
      if (parent == null) return emptyList();

      visibility = ThreeState.UNSURE;

      VirtualFile[] children = file.getChildren();
      if (children == null || children.length == 0) return emptyList();

      List<FileNode> list = new SmartList<>();
      Mapper<FileNode> mapper = new Mapper<>(oldList, FileNode::new);
      for (VirtualFile child : children) {
        if (child.is(VFileProperty.SYMLINK) && VfsUtilCore.isInvalidLink(child)) {
          continue; // ignore invalid symlink
        }
        Object id = getRootID();
        AreaInstance area = findArea(child, parent.project);
        if (area != null && (id instanceof VirtualFile || area.equals(id))) {
          list.add(mapper.apply(child, id));
        }
      }
      return list;
    }

    final void invalidateChildren(Predicate<FileNode> validator) {
      if (valid || !file.isDirectory()) {
        if (validator == null || !validator.test(this)) {
          validator = null; // all children will be invalid
          valid = false;
        }
        for (FileNode node : children) {
          node.invalidateChildren(validator);
        }
      }
    }
  }


  private static class RootNode extends FileNode {
    RootNode(@NotNull VirtualFile file, @NotNull Object id) {
      super(file, id);
    }

    @Override
    public String toString() {
      return file.getPath();
    }
  }
}
