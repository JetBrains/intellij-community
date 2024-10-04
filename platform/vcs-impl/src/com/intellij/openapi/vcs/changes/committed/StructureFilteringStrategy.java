// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.openapi.vcs.changes.ui.DirectoryChangesGroupingPolicy;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.*;


@ApiStatus.Internal
public class StructureFilteringStrategy implements ChangeListFilteringStrategy {
  private final List<ChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private MyUI myUI;
  private final Project myProject;
  private final List<FilePath> mySelection = new ArrayList<>();

  public StructureFilteringStrategy(final Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public CommittedChangesFilterKey getKey() {
    return new CommittedChangesFilterKey(toString(), CommittedChangesFilterPriority.STRUCTURE);
  }

  public String toString() {
    return VcsBundle.message("filter.structure.name");
  }

  @Override
  @Nullable
  public JComponent getFilterUI() {
    if (myUI == null) {
      myUI = new MyUI();
    }
    return myUI.getComponent();
  }

  @Override
  public void setFilterBase(@NotNull List<? extends CommittedChangeList> changeLists) {
    // todo cycle here
    if (myUI == null) {
      myUI = new MyUI();
    }
    myUI.reset();
    myUI.append(changeLists);
  }

  @Override
  public void addChangeListener(@NotNull ChangeListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeChangeListener(@NotNull ChangeListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void resetFilterBase() {
    myUI.reset();
  }

  @Override
  public void appendFilterBase(@NotNull List<? extends CommittedChangeList> changeLists) {
    myUI.append(changeLists);
  }

  @Override
  @NotNull
  public List<CommittedChangeList> filterChangeLists(@NotNull List<? extends CommittedChangeList> changeLists) {
    if (mySelection.size() == 0) {
      return new ArrayList<>(changeLists);
    }
    final ArrayList<CommittedChangeList> result = new ArrayList<>();
    for (CommittedChangeList list : changeLists) {
      if (listMatchesSelection(list)) {
        result.add(list);
      }
    }
    return result;
  }

  private boolean listMatchesSelection(final CommittedChangeList list) {
    for (Change change : list.getChanges()) {
      FilePath path = ChangesUtil.getFilePath(change);
      for (FilePath selPath : mySelection) {
        if (path.isUnder(selPath, false)) {
          return true;
        }
      }
    }
    return false;
  }

  private class MyUI {
    private final JComponent myScrollPane;
    private final Tree myStructureTree;
    private boolean myRendererInitialized;
    private final Set<FilePath> myFilePaths = new HashSet<>();
    private TreeState myState;

    MyUI() {
      myStructureTree = new Tree();
      myStructureTree.setRootVisible(false);
      myStructureTree.setShowsRootHandles(true);
      myStructureTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
        @Override
        public void valueChanged(final TreeSelectionEvent e) {
          final List<FilePath> filePaths = new ArrayList<>(mySelection);

          mySelection.clear();
          final TreePath[] selectionPaths = myStructureTree.getSelectionPaths();
          if (selectionPaths != null) {
            for (TreePath selectionPath : selectionPaths) {
              mySelection.addAll(getFilePathsUnder((ChangesBrowserNode<?>)selectionPath.getLastPathComponent()));
            }
          }

          if (Comparing.haveEqualElements(filePaths, mySelection)) return;

          for (ChangeListener listener : myListeners) {
            listener.stateChanged(new ChangeEvent(this));
          }
        }
      });
      myScrollPane = ScrollPaneFactory.createScrollPane(myStructureTree);
    }

    @NotNull
    private static List<FilePath> getFilePathsUnder(@NotNull ChangesBrowserNode<?> node) {
      List<FilePath> result = Collections.emptyList();
      Object userObject = node.getUserObject();

      if (userObject instanceof FilePath) {
        result = Collections.singletonList(((FilePath)userObject));
      }
      else if (userObject instanceof Module) {
        result = ContainerUtil.map(ModuleRootManager.getInstance((Module)userObject).getContentRoots(), VcsUtil::getFilePath);
      }

      return result;
    }

    public void initRenderer() {
      if (!myRendererInitialized) {
        myRendererInitialized = true;
        myStructureTree.setCellRenderer(new ChangesBrowserNodeRenderer(myProject, BooleanGetter.FALSE, false));
      }
    }

    public JComponent getComponent() {
      return myScrollPane;
    }

    public void reset() {
      myFilePaths.clear();
      myState = TreeState.createOn(myStructureTree, (DefaultMutableTreeNode)myStructureTree.getModel().getRoot());
      myStructureTree.setModel(TreeModelBuilder.buildEmpty());
    }

    public void append(final List<? extends CommittedChangeList> changeLists) {
      final TreeState localState = myState != null && myFilePaths.isEmpty()
                                   ? myState
                                   : TreeState.createOn(myStructureTree, (DefaultMutableTreeNode)myStructureTree.getModel().getRoot());

      for (CommittedChangeList changeList : changeLists) {
        for (Change change : changeList.getChanges()) {
          final FilePath path = ChangesUtil.getFilePath(change);
          if (path.getParentPath() != null) {
            myFilePaths.add(path.getParentPath());
          }
        }
      }

      myStructureTree.setModel(TreeModelBuilder.buildFromFilePaths(myProject, new DirectoryChangesGroupingPolicy.Factory(), myFilePaths));
      localState.applyTo(myStructureTree, myStructureTree.getModel().getRoot());
      myStructureTree.revalidate();
      myStructureTree.repaint();
      initRenderer();
    }
  }
}
