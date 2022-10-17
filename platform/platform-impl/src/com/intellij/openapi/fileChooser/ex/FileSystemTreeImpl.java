// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.impl.FileComparator;
import com.intellij.openapi.fileChooser.impl.FileTreeStructure;
import com.intellij.openapi.fileChooser.tree.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.AbstractTreeModel;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

public class FileSystemTreeImpl implements FileSystemTree {
  private final Tree myTree;
  private final FileTreeStructure myTreeStructure;
  private final Project myProject;
  private final ArrayList<Runnable> myOkActions = new ArrayList<>(2);
  private final FileChooserDescriptor myDescriptor;
  private final @NotNull AbstractTreeModel myFileTreeModel;
  private final @NotNull AsyncTreeModel myAsyncTreeModel;

  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public FileSystemTreeImpl(@Nullable final Project project, final FileChooserDescriptor descriptor) {
    this(project, descriptor, new Tree(), null, null, null);
    myTree.setRootVisible(descriptor.isTreeRootVisible());
    myTree.setShowsRootHandles(true);
  }

  public FileSystemTreeImpl(@Nullable final Project project,
                            final FileChooserDescriptor descriptor,
                            final Tree tree,
                            @Nullable TreeCellRenderer renderer,
                            @Nullable final Runnable onInitialized,
                            @Nullable final Convertor<? super TreePath, String> speedSearchConverter) {
    myProject = project;
    if (renderer == null) {
      renderer = new FileRenderer().forTree();
      myFileTreeModel = createFileTreeModel(descriptor, tree);
      myTreeStructure = null;
    }
    else {
      myTreeStructure = new FileTreeStructure(project, descriptor);
      myFileTreeModel = new StructureTreeModel<>(myTreeStructure, getFileComparator(), this);
    }
    myDescriptor = descriptor;
    myTree = tree;
    myAsyncTreeModel = new AsyncTreeModel(myFileTreeModel, this);
    myTree.setModel(myAsyncTreeModel);

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(final TreeSelectionEvent e) {
        processSelectionChange();
      }
    });

    if (speedSearchConverter != null) {
      new TreeSpeedSearch(myTree, false, speedSearchConverter.asFunction());
    }
    else {
      new TreeSpeedSearch(myTree);
    }
    TreeUtil.installActions(myTree);

    myTree.getSelectionModel().setSelectionMode(
      descriptor.isChooseMultiple() ? TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION : TreeSelectionModel.SINGLE_TREE_SELECTION
    );
    registerTreeActions();

    if (renderer == null) {
      renderer = new NodeRenderer() {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
          super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
          final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          if (userObject instanceof FileNodeDescriptor) {
            String comment = ((FileNodeDescriptor)userObject).getComment();
            if (comment != null) {
              append(comment, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
        }
      };
    }
    myTree.setCellRenderer(renderer);
  }

  protected Comparator<? super NodeDescriptor<?>> getFileComparator() {
    return FileComparator.getInstance();
  }

  @NotNull
  protected FileTreeModel createFileTreeModel(@NotNull FileChooserDescriptor descriptor, @NotNull Tree tree) {
    return new FileTreeModel(descriptor, new FileRefresher(true, 3, () -> ModalityState.stateForComponent(tree)));
  }

  private void registerTreeActions() {
    myTree.registerKeyboardAction(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          performEnterAction(true);
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED
    );

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        performEnterAction(false);
        return true;
      }
    }.installOn(myTree);
  }

  private void performEnterAction(boolean toggleNodeState) {
    TreePath path = myTree.getSelectionPath();
    if (path != null) {
      if (isLeaf(path)) {
        fireOkAction();
      }
      else if (toggleNodeState) {
        if (myTree.isExpanded(path)) {
          myTree.collapsePath(path);
        }
        else {
          myTree.expandPath(path);
        }
      }
    }
  }

  public void addOkAction(Runnable action) { myOkActions.add(action); }

  private void fireOkAction() {
    for (Runnable action : myOkActions) {
      action.run();
    }
  }

  public void registerMouseListener(final ActionGroup group) {
    PopupHandler.installPopupMenu(myTree, group, "FileSystemTreePopup");
  }

  @Override
  public boolean areHiddensShown() {
    return myDescriptor.isShowHiddenFiles();
  }

  @Override
  public void showHiddens(boolean showHidden) {
    myDescriptor.withShowHiddenFiles(showHidden);
    updateTree();
  }

  @Override
  public void updateTree() {
    if (myFileTreeModel instanceof FileTreeModel) {
      ((FileTreeModel)myFileTreeModel).invalidate();
    }
    else {
      ((StructureTreeModel<?>)myFileTreeModel).invalidateAsync();
    }
  }

  @Override
  public void dispose() {
  }

  @Override
  public void select(VirtualFile file, @Nullable final Runnable onDone) {
    select(new VirtualFile[]{file}, onDone);
  }

  @Override
  public void select(VirtualFile[] file, @Nullable final Runnable onDone) {
    switch (file.length) {
      case 0 -> {
        myTree.clearSelection();
        if (onDone != null) onDone.run();
      }
      case 1 -> {
        myTree.clearSelection();
        TreeUtil.promiseSelect(myTree, new FileNodeVisitor(file[0])).onProcessed(path -> {
          if (onDone != null) onDone.run();
        });
      }
      default -> {
        myTree.clearSelection();
        TreeUtil.promiseSelect(myTree, Stream.of(file).map(FileNodeVisitor::new)).onProcessed(paths -> {
          if (onDone != null) onDone.run();
        });
      }
    }
  }

  @Override
  public void expand(final VirtualFile file, @Nullable final Runnable onDone) {
    TreeUtil.promiseExpand(myTree, new FileNodeVisitor(file)).onSuccess(path -> {
      if (path != null && onDone != null) onDone.run();
    });
  }

  public Exception createNewFolder(final VirtualFile parentDirectory, final String newFolderName) {
    final Exception[] failReason = new Exception[]{null};
    CommandProcessor.getInstance().executeCommand(
      myProject, new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                VirtualFile parent = parentDirectory;
                for (String name : StringUtil.tokenize(newFolderName, "\\/")) {
                  VirtualFile folder = parent.createChildDirectory(this, name);
                  updateTree();
                  select(folder, null);
                  parent = folder;
                }
              }
              catch (IOException e) {
                failReason[0] = e;
              }
            }
          });
        }
      },
      UIBundle.message("file.chooser.create.new.folder.command.name"),
      null
    );
    return failReason[0];
  }

  public Exception createNewFile(final VirtualFile parentDirectory,
                                 final String newFileName,
                                 final FileType fileType,
                                 final String initialContent) {
    final Exception[] failReason = new Exception[]{null};
    CommandProcessor.getInstance().executeCommand(
      myProject, new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                final String newFileNameWithExtension = newFileName.endsWith('.' + fileType.getDefaultExtension())
                                                        ? newFileName
                                                        : newFileName + '.' + fileType.getDefaultExtension();
                final VirtualFile file = parentDirectory.createChildData(this, newFileNameWithExtension);
                VfsUtil.saveText(file, initialContent != null ? initialContent : "");
                updateTree();
                select(file, null);
              }
              catch (IOException e) {
                failReason[0] = e;
              }
            }
          });
        }
      },
      UIBundle.message("file.chooser.create.new.file.command.name"),
      null
    );
    return failReason[0];
  }

  @Override
  public JTree getTree() { return myTree; }

  @Override
  @Nullable
  public VirtualFile getSelectedFile() {
    final TreePath path = myTree.getSelectionPath();
    if (path == null) return null;
    return getVirtualFile(path);
  }

  @Override
  @Nullable
  public VirtualFile getNewFileParent() {
    final VirtualFile selected = getSelectedFile();
    if (selected != null) return selected;

    final List<VirtualFile> roots = myDescriptor.getRoots();
    return roots.size() == 1 ? roots.get(0) : null;
  }

  @Override
  public <T> T getData(@NotNull DataKey<T> key) {
    return myDescriptor.getUserData(key);
  }

  @Override
  public VirtualFile @NotNull [] getSelectedFiles() {
    final TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) return VirtualFile.EMPTY_ARRAY;

    final List<VirtualFile> files = new ArrayList<>();
    for (TreePath path : paths) {
      VirtualFile file = getVirtualFile(path);
      if (file != null && file.isValid()) {
        files.add(file);
      }
    }
    return VfsUtilCore.toVirtualFileArray(files);
  }

  private boolean isLeaf(TreePath path) {
    Object component = path.getLastPathComponent();
    if (component instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)component;
      return node.isLeaf();
    }
    return myAsyncTreeModel.isLeaf(component);
  }

  public static VirtualFile getVirtualFile(TreePath path) {
    Object component = path.getLastPathComponent();
    if (component instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)component;
      Object userObject = node.getUserObject();
      if (userObject instanceof FileNodeDescriptor) {
        FileNodeDescriptor descriptor = (FileNodeDescriptor)userObject;
        return descriptor.getElement().getFile();
      }
    }
    if (component instanceof FileNode) {
      FileNode node = (FileNode)component;
      return node.getFile();
    }
    return null;
  }

  @Override
  public boolean selectionExists() {
    TreePath[] selectedPaths = myTree.getSelectionPaths();
    return selectedPaths != null && selectedPaths.length != 0;
  }

  @Override
  public boolean isUnderRoots(@NotNull VirtualFile file) {
    final List<VirtualFile> roots = myDescriptor.getRoots();
    if (roots.size() == 0) return true;

    for (VirtualFile root : roots) {
      if (root != null && VfsUtilCore.isAncestor(root, file, false)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void addListener(final Listener listener, final Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  private void fireSelection(@NotNull List<? extends VirtualFile> selection) {
    for (Listener each : myListeners) {
      each.selectionChanged(selection);
    }
  }

  private void processSelectionChange() {
    if (myListeners.size() == 0) return;
    List<VirtualFile> selection = new ArrayList<>();

    final TreePath[] paths = myTree.getSelectionPaths();
    if (paths != null) {
      for (TreePath each : paths) {
        VirtualFile file = getVirtualFile(each);
        if (file != null) {
          selection.add(file);
        }
      }
    }

    fireSelection(selection);
  }
}
