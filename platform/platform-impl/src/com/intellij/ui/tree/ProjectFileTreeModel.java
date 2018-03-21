// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.intellij.ProjectTopics.PROJECT_ROOTS;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES;
import static com.intellij.ui.tree.TreePathUtil.pathToCustomNode;
import static java.util.Collections.emptyList;

public final class ProjectFileTreeModel extends BaseTreeModel<ProjectFileTreeModel.Child> implements InvokerSupplier {
  public interface Child {
    @NotNull
    Module getModule();

    @NotNull
    VirtualFile getVirtualFile();
  }

  private final Invoker invoker = new Invoker.BackgroundThread(this);
  private final ProjectNode root;

  public ProjectFileTreeModel(@NotNull Project project) {
    root = new ProjectNode(project);
    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        onValidThread(() -> {
          root.valid = false; // need to reload content roots
          pathChanged(null);
        });
      }
    });
    connection.subscribe(VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        onValidThread(() -> {
          for (VFileEvent event : events) {
            if (event instanceof VFileCreateEvent) {
              VFileCreateEvent create = (VFileCreateEvent)event;
              invalidate(create.getParent());
            }
            else if (event instanceof VFileCopyEvent) {
              VFileCopyEvent copy = (VFileCopyEvent)event;
              invalidate(copy.getNewParent());
            }
            else if (event instanceof VFileMoveEvent) {
              VFileMoveEvent move = (VFileMoveEvent)event;
              invalidate(move.getNewParent());
              invalidate(move.getOldParent());
              invalidate(move.getFile());
            }
            else {
              VirtualFile file = event.getFile();
              if (file != null) {
                if (event instanceof VFileDeleteEvent) {
                  VirtualFile parent = file.getParent();
                  if (parent != null) invalidate(parent);
                }
                invalidate(file);
              }
            }
          }
        });
      }
    });
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
  public List<Child> getChildren(Object object) {
    Node node = object instanceof Node && isValidThread() ? (Node)object : null;
    if (node == null) return emptyList();
    List<?> children = node.getChildren();
    if (children.isEmpty()) return emptyList();
    List<Child> result = new SmartList<>();
    VirtualFileFilter filter = root.filter;
    for (Object child : children) {
      if (child instanceof FileNode && isVisible((FileNode)child, filter)) {
        result.add((FileNode)child);
      }
    }
    return result;
  }

  private static boolean isVisible(@NotNull FileNode node, @Nullable VirtualFileFilter filter) {
    if (node.module.isDisposed()) return false; // ignore disposed module
    if (!node.file.isValid()) return false; // ignore removed file
    if (filter == null) return true;
    ThreeState visibility = node.visibility;
    if (visibility == ThreeState.NO) return false;
    if (visibility == ThreeState.YES) return true;
    boolean visible;
    if (!node.file.isDirectory()) {
      visible = filter.accept(node.file);
    }
    else {
      List<FileNode> children = node.getChildren();
      visible = !children.stream().allMatch(child -> child.visibility == ThreeState.NO) &&
                (children.stream().anyMatch(child -> child.visibility == ThreeState.YES) ||
                 children.stream().anyMatch(child -> isVisible(child, filter)));
    }
    node.visibility = ThreeState.fromBoolean(visible);
    return visible;
  }

  public void invalidate(VirtualFile file) {
    onValidThread(() -> {
      if (root.project.isDisposed()) return;
      ProjectRootManager manager = ProjectRootManager.getInstance(root.project);
      if (manager == null) return;

      Module module = manager.getFileIndex().getModuleForFile(file);
      if (module == null || module.isDisposed()) return;

      for (RootNode node : root.children) {
        if (node.module == module && node.invalidate(file) && node.valid && root.valid) {
          node.invalidateLater(invoker, this::pathChanged);
        }
      }
    });
  }

  public void setFilter(@Nullable VirtualFileFilter filter) {
    onValidThread(() -> {
      if (root.filter == null && filter == null) return;
      root.filter = filter;
      root.resetVisibility();
      pathChanged(null);
    });
  }

  private void pathChanged(@Nullable TreePath path) {
    onValidThread(() -> treeStructureChanged(path, null, null));
  }


  private static final class Mapper<N extends FileNode> implements BiFunction<VirtualFile, Module, N> {
    private final HashMap<VirtualFile, N> map = new HashMap<>();
    private final BiFunction<VirtualFile, Module, N> function;

    Mapper(@NotNull List<N> list, @NotNull BiFunction<VirtualFile, Module, N> function) {
      list.forEach(node -> map.put(node.file, node));
      this.function = function;
    }

    @NotNull
    @Override
    public final N apply(VirtualFile file, Module module) {
      N node = map.isEmpty() ? null : map.remove(file);
      return node != null && node.module == module ? node : function.apply(file, module);
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
      if (project.isDisposed()) return emptyList();
      ModuleManager moduleManager = ModuleManager.getInstance(project);
      if (moduleManager == null) return emptyList();

      List<RootNode> list = new SmartList<>();
      Mapper<RootNode> mapper = new Mapper<>(oldList, RootNode::new);
      for (Module module : moduleManager.getModules()) {
        if (module.isDisposed()) continue;
        ModuleRootManager manager = ModuleRootManager.getInstance(module);
        if (manager == null) continue;

        for (VirtualFile file : manager.getContentRoots()) {
          list.add(mapper.apply(file, module));
        }
      }
      // invalidate all changed file nodes without notifications
      list.forEach(node -> node.invalidateNow(null));
      return list;
    }
  }


  private static class FileNode extends Node<FileNode> implements Child {
    final VirtualFile file;
    final Module module;

    FileNode(@NotNull VirtualFile file, @NotNull Module module) {
      this.file = file;
      this.module = module;
    }

    @NotNull
    public Module getModule() {
      return module;
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
      if (!file.isValid() || module.isDisposed()) return emptyList();
      ModuleRootManager manager = ModuleRootManager.getInstance(module);
      if (manager == null) return emptyList();
      visibility = ThreeState.UNSURE;

      List<FileNode> list = new SmartList<>();
      Mapper<FileNode> mapper = new Mapper<>(oldList, FileNode::new);
      manager.getFileIndex().iterateContentUnderDirectory(file
        , child -> file.equals(child) || list.add(mapper.apply(child, module))
        , child -> file.equals(child) || file.equals(child.getParent()));

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
    final AtomicLong counter = new AtomicLong();
    final List<VirtualFile> accumulator = new SmartList<>();

    RootNode(@NotNull VirtualFile file, @NotNull Module module) {
      super(file, module);
    }

    @Override
    public String toString() {
      return file.getPath();
    }

    boolean invalidate(VirtualFile file) {
      if (!isAncestor(this.file, file, false)) {
        return false; // the file does not belong this root
      }
      List<VirtualFile> list = accumulator;
      if (!list.isEmpty()) {
        for (VirtualFile ancestor : list) {
          if (isAncestor(ancestor, file, false)) {
            return false; // the file or its parent is already added
          }
        }
        Iterator<VirtualFile> iterator = list.iterator();
        while (iterator.hasNext()) {
          if (isAncestor(file, iterator.next(), false)) {
            iterator.remove(); // remove all children of the file
          }
        }
      }
      list.add(file);
      return true;
    }

    void invalidateLater(@NotNull Invoker invoker, @NotNull Consumer<TreePath> consumer) {
      long count = counter.incrementAndGet();
      invoker.invokeLater(() -> {
        // is this request still actual after 10 ms?
        if (count == counter.get()) {
          ProjectNode parent = findParent(ProjectNode.class);
          if (parent != null && !parent.project.isDisposed()) {
            List<FileNode> list = new SmartList<>();
            invalidateNow(node -> list.add(node));
            if (parent.filter == null) {
              for (FileNode node : list) {
                TreePath path = pathToCustomNode((Node)node, child -> child.parent);
                if (path != null) consumer.accept(path);
              }
            }
            else if (!list.isEmpty()) {
              list.forEach(Node::resetParentVisibility);
              consumer.accept(null);
            }
          }
        }
      }, 10);
    }

    void invalidateNow(Consumer<FileNode> consumer) {
      List<VirtualFile> list = accumulator;
      if (!list.isEmpty()) {
        HashMap<VirtualFile, VirtualFile> map = new HashMap<>();
        list.forEach(file -> map.put(file, file));
        list.clear();
        invalidateChildren(node -> {
          if (!map.containsKey(node.file)) return true;
          if (consumer != null) consumer.accept(node);
          return false;
        });
      }
    }
  }
}
