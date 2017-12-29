/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.fileChooser.tree;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.ui.tree.Identifiable;
import com.intellij.ui.tree.MapBasedTree;
import com.intellij.ui.tree.MapBasedTree.Entry;
import com.intellij.ui.tree.MapBasedTree.UpdateResult;
import com.intellij.ui.tree.Searchable;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.ui.tree.AbstractTreeModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.Icon;
import javax.swing.tree.TreePath;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.util.Disposer.register;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.text.StringUtil.naturalCompare;
import static com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES;
import static com.intellij.util.ReflectionUtil.getDeclaredMethod;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * @author Sergey.Malenkov
 */
public final class FileTreeModel extends AbstractTreeModel implements Identifiable, Searchable, InvokerSupplier {
  private final Invoker invoker = new Invoker.BackgroundThread(this);
  private final State state;
  private volatile List<Root> roots;

  public FileTreeModel(@NotNull FileChooserDescriptor descriptor, FileRefresher refresher) {
    this(descriptor, refresher, true, false);
  }

  public FileTreeModel(@NotNull FileChooserDescriptor descriptor, FileRefresher refresher, boolean sortDirectories, boolean sortArchives) {
    if (refresher != null) register(this, refresher);
    state = new State(descriptor, refresher, sortDirectories, sortArchives);
    getApplication().getMessageBus().connect(this).subscribe(VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        invoker.invokeLaterIfNeeded(() -> process(events));
      }
    });
  }

  public void invalidate() {
    invoker.invokeLaterIfNeeded(() -> {
      if (roots != null) {
        for (Root root : roots) {
          root.tree.invalidate();
        }
      }
      treeStructureChanged(state.path, null, null);
    });
  }

  @Override
  public Object getUniqueID(@NotNull TreePath path) {
    Object object = path.getLastPathComponent();
    TreePath parent = path.getParentPath();
    return parent != null && object instanceof Node
           ? getUniqueID(parent, (Node)object, new ArrayDeque<>())
           : parent != null || object != state ? null : state.toString();
  }

  private Object getUniqueID(TreePath path, Node node, ArrayDeque<String> deque) {
    deque.addFirst(node.getName());
    Object object = path.getLastPathComponent();
    TreePath parent = path.getParentPath();
    return parent != null && object instanceof Node
           ? getUniqueID(parent, (Node)object, deque)
           : parent != null || object != state ? null : deque.toArray();
  }

  @NotNull
  @Override
  public Promise<TreePath> getTreePath(Object object) {
    if (object == null) return Promises.rejectedPromise();
    if (object instanceof String && object.equals(state.toString())) return Promises.resolvedPromise(state.path);
    AsyncPromise<TreePath> promise = new AsyncPromise<>();
    invoker.invokeLaterIfNeeded(() -> {
      if (object instanceof Object[]) {
        resolveID(promise, (Object[])object);
      }
      else if (object instanceof VirtualFile) {
        resolveFile(promise, (VirtualFile)object);
      }
      else if (object instanceof String) {
        VirtualFile file = findFile((String)object);
        if (file != null) {
          resolveFile(promise, file);
        }
        else {
          promise.setError("file not found");
        }
      }
      else {
        promise.setError("unsupported object");
      }
    });
    return promise;
  }

  private void resolveID(AsyncPromise<TreePath> promise, Object[] array) {
    if (array.length > 0) {
      if (roots == null) roots = state.getRoots();
      for (Root root : roots) {
        Entry<Node> child = root.tree.getRootEntry();
        if (child != null && Objects.equals(child.getNode().getName(), array[0])) {
          resolveID(promise, array, 1, root, child);
          return;
        }
      }
      promise.setError("root entry not found");
    }
    else {
      promise.setResult(state.path);
    }
  }

  private void resolveID(AsyncPromise<TreePath> promise, Object[] array, int index, Root root, Entry<Node> entry) {
    if (index < array.length) {
      if (entry.isLoadingRequired()) {
        root.updateChildren(state, entry);
      }
      for (int i = 0; i < entry.getChildCount(); i++) {
        Entry<Node> child = entry.getChildEntry(i);
        if (child != null && Objects.equals(child.getNode().getName(), array[index])) {
          resolveID(promise, array, index + 1, root, child);
          return;
        }
      }
      promise.setError("entry not found");
    }
    else {
      promise.setResult(entry);
    }
  }

  private void resolveFile(AsyncPromise<TreePath> promise, VirtualFile file) {
    if (roots == null) roots = state.getRoots();
    for (Root root : roots) {
      if (resolveFile(promise, file, root, root.tree.getRootEntry())) {
        return;
      }
    }
    promise.setError("root entry not found");
  }

  private boolean resolveFile(AsyncPromise<TreePath> promise, VirtualFile file, Root root, Entry<Node> entry) {
    if (entry != null) {
      if (entry.getNode().getFile().equals(file)) {
        promise.setResult(entry);
        return true;
      }
      if (VfsUtilCore.isAncestor(entry.getNode().getFile(), file, true)) {
        if (entry.isLoadingRequired()) {
          root.updateChildren(state, entry);
        }
        for (int i = 0; i < entry.getChildCount(); i++) {
          if (resolveFile(promise, file, root, entry.getChildEntry(i))) {
            return true;
          }
        }
        promise.setError("entry not found");
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public Invoker getInvoker() {
    return invoker;
  }

  @Override
  public final Object getRoot() {
    return state;
  }

  @Override
  public final Object getChild(Object object, int index) {
    if (object == state) {
      if (roots == null) roots = state.getRoots();
      if (0 <= index && index < roots.size()) return roots.get(index);
    }
    else if (object instanceof Node) {
      Entry<Node> entry = getEntry((Node)object, true);
      if (entry != null) return entry.getChild(index);
    }
    return null;
  }

  @Override
  public final int getChildCount(Object object) {
    if (object == state) {
      if (roots == null) roots = state.getRoots();
      return roots.size();
    }
    else if (object instanceof Node) {
      Entry<Node> entry = getEntry((Node)object, true);
      if (entry != null) return entry.getChildCount();
    }
    return 0;
  }

  @Override
  public final boolean isLeaf(Object object) {
    if (object != state && object instanceof Node) {
      Entry<Node> entry = getEntry((Node)object, false);
      if (entry != null) return entry.isLeaf();
    }
    return false;
  }

  @Override
  public final int getIndexOfChild(Object object, Object child) {
    if (object == state) {
      if (roots == null) roots = state.getRoots();
      for (int i = 0; i < roots.size(); i++) {
        if (child == roots.get(i)) return i;
      }
    }
    else if (object instanceof Node && child instanceof Node) {
      Entry<Node> entry = getEntry((Node)object, true);
      if (entry != null) return entry.getIndexOf((Node)child);
    }
    return -1;
  }

  @Override
  public void valueForPathChanged(TreePath path, Object newValue) {
  }

  private boolean hasEntry(VirtualFile file) {
    if (roots != null) {
      for (Root root : roots) {
        Entry<Node> entry = root.tree.findEntry(file);
        if (entry != null) return true;
      }
    }
    return false;
  }

  private Entry<Node> getEntry(Node node, boolean loadChildren) {
    if (roots != null) {
      for (Root root : roots) {
        Entry<Node> entry = root.tree.getEntry(node);
        if (entry != null) {
          if (loadChildren && entry.isLoadingRequired()) {
            root.updateChildren(state, entry);
            //TODO: update updated
          }
          return entry;
        }
      }
    }
    return null;
  }

  private void process(List<? extends VFileEvent> events) {
    if (roots == null) return;

    HashSet<VirtualFile> files = new HashSet<>();
    HashSet<VirtualFile> parents = new HashSet<>();
    for (VFileEvent event : events) {
      if (event instanceof VFilePropertyChangeEvent) {
        if (hasEntry(event.getFile())) files.add(event.getFile());
      }
      else if (event instanceof VFileCreateEvent) {
        VFileCreateEvent create = (VFileCreateEvent)event;
        if (hasEntry(create.getParent())) parents.add(create.getParent());
      }
      else if (event instanceof VFileCopyEvent) {
        VFileCopyEvent copy = (VFileCopyEvent)event;
        if (hasEntry(copy.getNewParent())) parents.add(copy.getNewParent());
      }
      else if (event instanceof VFileMoveEvent) {
        VFileMoveEvent move = (VFileMoveEvent)event;
        if (hasEntry(move.getNewParent())) parents.add(move.getNewParent());
        if (hasEntry(move.getOldParent())) parents.add(move.getOldParent());
      }
      else if (event instanceof VFileDeleteEvent) {
        VirtualFile file = event.getFile();
        if (hasEntry(file)) {
          files.add(file);
          //TODO:for all roots
          file = file.getParent();
          parents.add(hasEntry(file) ? file : null);
        }
      }
    }
    for (VirtualFile parent : parents) {
      for (Root root : roots) {
        Entry<Node> entry = root.tree.findEntry(parent);
        if (entry != null) {
          UpdateResult<Node> update = root.updateChildren(state, entry);
          //TODO:listeners.isEmpty
          boolean removed = !update.getRemoved().isEmpty();
          boolean inserted = !update.getInserted().isEmpty();
          boolean contained = !update.getContained().isEmpty();
          if (!removed && !inserted && !contained) continue;

          if (!removed && inserted) {
            if (listeners.isEmpty()) continue;
            listeners.treeNodesInserted(update.getEvent(this, entry, update.getInserted()));
            continue;
          }
          if (!inserted && removed) {
            if (listeners.isEmpty()) continue;
            listeners.treeNodesRemoved(update.getEvent(this, entry, update.getRemoved()));
            continue;
          }
          treeStructureChanged(entry, null, null);
        }
      }
    }
    for (VirtualFile file : files) {
      //TODO:update
    }
    //TODO:on valid thread / entry - mark as valid
  }

  private static VirtualFile findFile(String path) {
    return LocalFileSystem.getInstance().findFileByPath(toSystemIndependentName(path));
  }

  private static final class State {
    private final TreePath path;
    private final FileChooserDescriptor descriptor;
    private final FileRefresher refresher;
    private final boolean sortDirectories;
    private final boolean sortArchives;
    private List<VirtualFile> roots;

    private State(FileChooserDescriptor descriptor, FileRefresher refresher, boolean sortDirectories, boolean sortArchives) {
      this.path = new TreePath(this);
      this.descriptor = descriptor;
      this.refresher = refresher;
      this.sortDirectories = sortDirectories;
      this.sortArchives = sortArchives;
      this.roots = getRoots(descriptor);
    }

    private int compare(VirtualFile one, VirtualFile two) {
      if (one == null && two == null) return 0;
      if (one == null) return -1;
      if (two == null) return 1;
      if (sortDirectories) {
        boolean isDirectory = one.isDirectory();
        if (isDirectory != two.isDirectory()) return isDirectory ? -1 : 1;
        if (!isDirectory && sortArchives && descriptor.isChooseJarContents()) {
          boolean isArchive = FileElement.isArchive(one);
          if (isArchive != FileElement.isArchive(two)) return isArchive ? -1 : 1;
        }
      }
      return naturalCompare(one.getName(), two.getName());
    }

    private static boolean isValid(VirtualFile file) {
      return file != null && file.isValid();
    }

    private boolean isVisible(VirtualFile file) {
      return isValid(file) && descriptor.isFileVisible(file, descriptor.isShowHiddenFiles());
    }

    private boolean isLeaf(VirtualFile file) {
      if (file == null || file.isDirectory()) return false;
      return !descriptor.isChooseJarContents() || !FileElement.isArchive(file);
    }

    private VirtualFile[] getChildren(VirtualFile file) {
      if (file.isDirectory()) return file.getChildren();
      if (!descriptor.isChooseJarContents() || !FileElement.isArchive(file)) return null;
      String path = file.getPath() + JarFileSystem.JAR_SEPARATOR;
      VirtualFile jar = JarFileSystem.getInstance().findFileByPath(path);
      return jar == null ? VirtualFile.EMPTY_ARRAY : jar.getChildren();
    }

    private List<Root> getRoots() {
      List<VirtualFile> files = roots;
      if (files == null) files = getSystemRoots();
      if (files == null || files.isEmpty()) return emptyList();
      return files.stream().map(file -> new Root(this, file)).collect(toList());
    }

    private static List<VirtualFile> getRoots(FileChooserDescriptor descriptor) {
      List<VirtualFile> list = descriptor.getRoots().stream().filter(State::isValid).collect(toList());
      return list.isEmpty() && descriptor.isShowFileSystemRoots() ? null : list;
    }

    private static List<VirtualFile> getSystemRoots() {
      File[] roots = File.listRoots();
      return roots == null || roots.length == 0
             ? emptyList()
             : Arrays
               .stream(roots)
               .map(root -> findFile(root.getAbsolutePath()))
               .filter(State::isValid)
               .collect(toList());
    }

    @Override
    public String toString() {
      return descriptor.getTitle();
    }
  }

  private static class Node extends FileNode {
    private boolean invalid;

    private Node(State state, VirtualFile file) {
      super(file);
      if (state.refresher != null && !state.refresher.isRecursive()) state.refresher.register(file);
      updateContent(state);
    }

    private boolean updateContent(State state) {
      VirtualFile file = getFile();
      if (file == null) return updateName(state.descriptor.getTitle());

      Icon icon = state.descriptor.getIcon(file);
      String name = state.descriptor.getName(file);
      String comment = state.descriptor.getComment(file);
      if (name == null || comment == null) name = file.getName();

      boolean updated = false;
      if (updateIcon(icon)) updated = true;
      if (updateName(name)) updated = true;
      if (updateComment(comment)) updated = true;
      if (updateValid(file.isValid())) updated = true;
      if (updateHidden(FileElement.isFileHidden(file))) updated = true;
      if (updateSpecial(file.is(VFileProperty.SPECIAL))) updated = true;
      if (updateSymlink(file.is(VFileProperty.SYMLINK))) updated = true;
      if (updateWritable(file.isWritable())) updated = true;
      return updated;
    }

    @Override
    public String toString() {
      return getName();
    }
  }

  private static class Root extends Node {
    private final MapBasedTree<VirtualFile, Node> tree;

    private Root(State state, VirtualFile file) {
      super(state, file);
      if (state.refresher != null && state.refresher.isRecursive()) state.refresher.register(file);
      tree = new MapBasedTree<>(false, node -> node.getFile(), state.path);
      tree.onInsert(node -> markDirtyInternal(node.getFile()));
      tree.updateRoot(Pair.create(this, state.isLeaf(file)));
    }

    private static void markDirtyInternal(VirtualFile file) {
      if (file instanceof VirtualFileSystemEntry) {
        Method method = getDeclaredMethod(VirtualFileSystemEntry.class, "markDirtyInternal");
        if (method != null) {
          try {
            method.invoke(file);
          }
          catch (Exception ignore) {
          }
        }
      }
    }

    private UpdateResult<Node> updateChildren(State state, Entry<Node> parent) {
      VirtualFile[] children = state.getChildren(parent.getNode().getFile());
      if (children == null) return tree.update(parent, null);
      if (children.length == 0) return tree.update(parent, emptyList());
      return tree.update(parent, Arrays.stream(children).filter(state::isVisible).sorted(state::compare).map(file -> {
        Entry<Node> entry = tree.findEntry(file);
        return entry != null && parent == entry.getParentPath()
               ? Pair.create(entry.getNode(), entry.isLeaf())
               : Pair.create(new Node(state, file), state.isLeaf(file));
      }).collect(toList()));
    }
  }
}
