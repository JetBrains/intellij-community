/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yole
 */
public class StructureFilteringStrategy implements ChangeListFilteringStrategy {
  private final List<ChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private MyUI myUI;
  private final Project myProject;
  private final List<FilePath> mySelection = new ArrayList<>();

  public StructureFilteringStrategy(final Project project) {
    myProject = project;
  }

  @Override
  public CommittedChangesFilterKey getKey() {
    return new CommittedChangesFilterKey(toString(), CommittedChangesFilterPriority.STRUCTURE);
  }

  public String toString() {
    return VcsBundle.message("filter.structure.name");
  }

  @Nullable
  public JComponent getFilterUI() {
    if (myUI == null) {
      myUI = new MyUI();
    }
    return myUI.getComponent();
  }

  public void setFilterBase(List<CommittedChangeList> changeLists) {
    // todo cycle here
    if (myUI == null) {
      myUI = new MyUI();
    }
    myUI.reset();
    myUI.append(changeLists);
  }

  public void addChangeListener(ChangeListener listener) {
    myListeners.add(listener);
  }

  public void removeChangeListener(ChangeListener listener) {
    myListeners.remove(listener);
  }

  public void resetFilterBase() {
    myUI.reset();
  }

  public void appendFilterBase(List<CommittedChangeList> changeLists) {
    myUI.append(changeLists);
  }

  @NotNull
  public List<CommittedChangeList> filterChangeLists(List<CommittedChangeList> changeLists) {
    if (mySelection.size() == 0) {
      return changeLists;
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
    private final TreeModelBuilder myBuilder;
    private TreeState myState;

    public MyUI() {
      myStructureTree = new Tree();
      myStructureTree.setRootVisible(false);
      myStructureTree.setShowsRootHandles(true);
      myStructureTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
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
      myBuilder = new TreeModelBuilder(myProject, false);
    }

    @NotNull
    private List<FilePath> getFilePathsUnder(@NotNull ChangesBrowserNode<?> node) {
      List<FilePath> result = Collections.emptyList();
      Object userObject = node.getUserObject();

      if (userObject instanceof FilePath) {
        result = ContainerUtil.list(((FilePath)userObject));
      }
      else if (userObject instanceof Module) {
        result = Arrays.stream(ModuleRootManager.getInstance((Module)userObject).getContentRoots())
          .map(VcsUtil::getFilePath)
          .collect(Collectors.toList());
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
      myState = TreeState.createOn(myStructureTree, (DefaultMutableTreeNode)myStructureTree.getModel().getRoot());
      myStructureTree.setModel(myBuilder.clearAndGetModel());
    }

    public void append(final List<CommittedChangeList> changeLists) {
      final TreeState localState = (myState != null) && myBuilder.isEmpty()
                                   ? myState
                                   : TreeState.createOn(myStructureTree, (DefaultMutableTreeNode)myStructureTree.getModel().getRoot());

      final Set<FilePath> filePaths = new HashSet<>();
      for (CommittedChangeList changeList : changeLists) {
        for (Change change : changeList.getChanges()) {
          final FilePath path = ChangesUtil.getFilePath(change);
          if (path.getParentPath() != null) {
            filePaths.add(path.getParentPath());
          }
        }
      }

      final DefaultTreeModel model = myBuilder.buildModelFromFilePaths(filePaths);
      myStructureTree.setModel(model);
      localState.applyTo(myStructureTree, (DefaultMutableTreeNode)myStructureTree.getModel().getRoot());
      myStructureTree.revalidate();
      myStructureTree.repaint();
      initRenderer();
    }
  }
}
