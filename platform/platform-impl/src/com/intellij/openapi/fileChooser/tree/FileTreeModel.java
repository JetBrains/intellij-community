// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.tree;

import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WSLUtil;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.ui.tree.MapBasedTree;
import com.intellij.ui.tree.MapBasedTree.Entry;
import com.intellij.ui.tree.MapBasedTree.UpdateResult;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.AbstractTreeModel;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.lang.reflect.Method;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class FileTreeModel extends AbstractTreeModel implements InvokerSupplier {

  private static final Logger LOG = Logger.getInstance(FileTreeModel.class);

  private final Invoker invoker = Invoker.forBackgroundThreadWithReadAction(this);
  private final State state;
  private volatile List<Root> roots;

  public FileTreeModel(@NotNull FileChooserDescriptor descriptor, FileRefresher refresher) {
    this(descriptor, refresher, true, false);
  }

  public FileTreeModel(@NotNull FileChooserDescriptor descriptor, FileRefresher refresher, boolean sortDirectories, boolean sortArchives) {
    if (refresher != null) Disposer.register(this, refresher);
    state = new State(descriptor, refresher, sortDirectories, sortArchives, this);
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        invoker.invoke(() -> process(events));
      }
    });
  }

  public void invalidate() {
    invoker.invoke(() -> {
      if (roots != null) {
        for (Root root : roots) {
          root.tree.invalidate();
        }
      }
      treeStructureChanged(state.path, null, null);
    });
  }

  @NotNull
  @Override
  public Invoker getInvoker() {
    return invoker;
  }

  @Override
  public Object getRoot() {
    if (state.path != null) return state;
    if (roots == null) roots = state.getRoots();
    return 1 == roots.size() ? roots.get(0) : null;
  }

  @Override
  public Object getChild(Object object, int index) {
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
  public int getChildCount(Object object) {
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
  public boolean isLeaf(Object object) {
    if (object instanceof Node) {
      Entry<Node> entry = getEntry((Node)object, false);
      if (entry != null) return entry.isLeaf();
    }
    return false;
  }

  @Override
  public int getIndexOfChild(Object object, Object child) {
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

  private boolean hasEntry(VirtualFile file) {
    if (file == null) return false;
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
    return LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path));
  }

  private static final class State {
    private final TreePath path;
    private final FileChooserDescriptor descriptor;
    private final FileRefresher refresher;
    private final boolean sortDirectories;
    private final boolean sortArchives;
    private final List<VirtualFile> roots;
    private final FileTreeModel model;

    private State(FileChooserDescriptor descriptor,
                  FileRefresher refresher,
                  boolean sortDirectories,
                  boolean sortArchives,
                  FileTreeModel model) {
      this.descriptor = descriptor;
      this.refresher = refresher;
      this.sortDirectories = sortDirectories;
      this.sortArchives = sortArchives;
      this.roots = getRoots(descriptor);
      this.path = roots != null && 1 == roots.size() ? null : new TreePath(this);
      this.model = model;
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
      return StringUtil.naturalCompare(one.getName(), two.getName());
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
      if (!isValid(file)) return null;
      if (file.isDirectory()) return file.getChildren();
      if (!descriptor.isChooseJarContents() || !FileElement.isArchive(file)) return null;
      String path = file.getPath() + JarFileSystem.JAR_SEPARATOR;
      VirtualFile jar = JarFileSystem.getInstance().findFileByPath(path);
      return jar == null ? VirtualFile.EMPTY_ARRAY : jar.getChildren();
    }

    private List<Root> getRoots() {
      if (!model.invoker.isValidThread()) {
        LOG.error(new IllegalStateException(Thread.currentThread().getName()));
      }
      List<VirtualFile> files = roots;
      if (files == null) files = getSystemRoots();
      if (files.isEmpty()) return Collections.emptyList();
      return ContainerUtil.map(files, file -> new Root(this, file));
    }

    private static List<VirtualFile> getRoots(FileChooserDescriptor descriptor) {
      List<VirtualFile> list = ContainerUtil.filter(descriptor.getRoots(), State::isValid);
      return list.isEmpty() && descriptor.isShowFileSystemRoots() ? null : list;
    }

    private @NotNull List<VirtualFile> getSystemRoots() {
      List<WSLDistribution> distributions = List.of();
      if (WSLUtil.isSystemCompatible() && Experiments.getInstance().isFeatureEnabled("wsl.p9.show.roots.in.file.chooser")) {
        WslDistributionManager distributionManager = WslDistributionManager.getInstance();
        List<WSLDistribution> lastDistributions = ContainerUtil.notNullize(distributionManager.getLastInstalledDistributions());
        distributions = lastDistributions;
        LOG.debug("WSL distributions: ", distributions);
        distributionManager.getInstalledDistributionsFuture().thenAccept(newDistributions -> {
          // called on a background thread without read action
          if (newDistributions.equals(lastDistributions)) {
            LOG.debug("WSL distributions are up-to-date");
            return;
          }
          LOG.debug("New WSL distributions: ", newDistributions);
          model.invoker.invokeLater(() -> {
            // invokeLater to ensure model.roots are calculated
            setRoots(getLocalAndWslRoots(newDistributions));
          });
        });
      }
      return getLocalAndWslRoots(distributions);
    }

    private static @NotNull List<VirtualFile> getLocalAndWslRoots(@NotNull List<? extends WSLDistribution> distributions) {
      return toVirtualFiles(ContainerUtil.concat(ContainerUtil.newArrayList(FileSystems.getDefault().getRootDirectories()),
                                                 ContainerUtil.map(distributions, WSLDistribution::getUNCRootPath)));
    }

    private void setRoots(@NotNull List<VirtualFile> newRootFiles) {
      List<Root> oldRoots = model.roots;
      if (oldRoots == null) {
        LOG.error("Roots have not been calculated yet, new roots won't be set due to a possible race condition");
        return;
      }
      List<VirtualFile> oldRootFiles = toRootFiles(oldRoots);
      if (LOG.isDebugEnabled()) {
        LOG.debug("New roots: " + newRootFiles + ", old roots: " + oldRootFiles);
      }
      removeRoots(oldRoots, findNewElementIndices(oldRootFiles, newRootFiles));
      List<Root> rootsToAdd = newRootFiles.stream().filter(root -> !oldRootFiles.contains(root))
        .map(root -> new Root(this, root)).collect(Collectors.toList());
      addRoots(model.roots, rootsToAdd);
    }

    private static @NotNull List<VirtualFile> toRootFiles(@NotNull List<Root> roots) {
      return ContainerUtil.map(roots, FileNode::getFile);
    }

    private void removeRoots(@NotNull List<Root> roots, int[] indicesToRemove) {
      if (indicesToRemove.length > 0) {
        List<Root> rootsToRemove = Arrays.stream(indicesToRemove).mapToObj(ind -> roots.get(ind)).toList();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Removing " + toRootFiles(rootsToRemove));
        }
        model.roots = ContainerUtil.filter(roots, root -> !rootsToRemove.contains(root));
        model.treeNodesRemoved(path, indicesToRemove, rootsToRemove.toArray());
      }
    }

    private void addRoots(@NotNull List<Root> roots, @NotNull List<Root> rootsToAdd) {
      if (rootsToAdd.size() > 0) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Adding " + toRootFiles(rootsToAdd));
        }
        model.roots = List.copyOf(ContainerUtil.concat(roots, rootsToAdd));
        model.treeNodesInserted(path, IntStream.range(roots.size(), roots.size() + rootsToAdd.size()).toArray(), rootsToAdd.toArray());
      }
    }

    private static <E> int[] findNewElementIndices(@NotNull List<E> a, @NotNull List<E> b) {
      IntList newIndices = new IntArrayList();
      for (int i = 0; i < a.size(); i++) {
        if (!b.contains(a.get(i))) {
          newIndices.add(i);
        }
      }
      return newIndices.toIntArray();
    }

    private static @NotNull List<VirtualFile> toVirtualFiles(@NotNull List<? extends Path> paths) {
      return paths.stream().map(root -> LocalFileSystem.getInstance().findFileByNioFile(root)).filter(State::isValid).collect(
        Collectors.toList());
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
      if (name == null || comment == null) name = file.getPresentableName();

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

  private static final class Root extends Node {
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
        Method method = ReflectionUtil.getDeclaredMethod(VirtualFileSystemEntry.class, "markDirtyInternal");
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
      if (children.length == 0) return tree.update(parent, Collections.emptyList());
      return tree.update(parent, Arrays.stream(children).filter(state::isVisible).sorted(state::compare).map(file -> {
        Entry<Node> entry = tree.findEntry(file);
        return entry != null && parent == entry.getParentPath()
               ? Pair.create(entry.getNode(), entry.isLeaf())
               : Pair.create(new Node(state, file), state.isLeaf(file));
      }).collect(Collectors.toList()));
    }
  }
}
