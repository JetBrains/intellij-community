/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.treeWithCheckedNodes;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.impl.CollectionsDelta;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairProcessor;
import com.intellij.openapi.vcs.changes.ui.PlusMinus;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
* @author irengrig
 *
 * see {@link SelectedState}
*/
public class SelectionManager {
  private final SelectedState<VirtualFile> myState;
  private final Convertor<DefaultMutableTreeNode, VirtualFile> myNodeConvertor;
  private PlusMinus<VirtualFile> mySelectionChangeListener;

  public SelectionManager(int selectedSize, int queueSize, final Convertor<DefaultMutableTreeNode, VirtualFile> nodeConvertor) {
    myNodeConvertor = nodeConvertor;
    myState = new SelectedState<>(selectedSize, queueSize);
  }

  public void toggleSelection(final DefaultMutableTreeNode node) {
    final StateWorker stateWorker = new StateWorker(node, myNodeConvertor);
    final VirtualFile vf = stateWorker.getVf();
    if (vf == null) return;

    final TreeNodeState state = getStateImpl(stateWorker);
    if (TreeNodeState.HAVE_SELECTED_ABOVE.equals(state)) return;
    if (TreeNodeState.CLEAR.equals(state) && (! myState.canAddSelection())) return;

    final HashSet<VirtualFile> old = new HashSet<>(myState.getSelected());

    final TreeNodeState futureState =
      myState.putAndPass(vf, TreeNodeState.SELECTED.equals(state) ? TreeNodeState.CLEAR : TreeNodeState.SELECTED);

    // for those possibly duplicate nodes (i.e. when we have root for module and root for VCS root, each file is shown twice in a tree ->
    // clear all suspicious cached)
    if (! TreeNodeState.SELECTED.equals(futureState)) {
      myState.clearAllCachedMatching(virtualFile -> VfsUtilCore.isAncestor(virtualFile, vf, false));
    }
    stateWorker.iterateParents(myState, (virtualFile, state1) -> {
      if (TreeNodeState.SELECTED.equals(futureState)) {
        myState.putAndPass(virtualFile, TreeNodeState.HAVE_SELECTED_BELOW);
      } else {
        myState.remove(virtualFile);
      }
      return true;
    });
    // todo vf, vf - what is correct?
    myState.clearAllCachedMatching(vf1 -> VfsUtilCore.isAncestor(stateWorker.getVf(), vf1, false));
    for (VirtualFile selected : myState.getSelected()) {
      if (VfsUtilCore.isAncestor(stateWorker.getVf(), selected, true)) {
        myState.remove(selected);
      }
    }
    final Set<VirtualFile> selectedAfter = myState.getSelected();
    if (mySelectionChangeListener != null && ! old.equals(selectedAfter)) {
      final Set<VirtualFile> removed = CollectionsDelta.notInSecond(old, selectedAfter);
      final Set<VirtualFile> newlyAdded = CollectionsDelta.notInSecond(selectedAfter, old);
      if (newlyAdded != null) {
        for (VirtualFile file : newlyAdded) {
          if (mySelectionChangeListener != null) {
            mySelectionChangeListener.plus(file);
          }
        }
      }
      if (removed != null) {
        for (VirtualFile file : removed) {
          if (mySelectionChangeListener != null) {
            mySelectionChangeListener.minus(file);
          }
        }
      }
    }
  }

  public boolean canAddSelection() {
    return myState.canAddSelection();
  }

  public void setSelection(Collection<VirtualFile> files) {
    myState.setSelection(files);
    for (VirtualFile file : files) {
      if (mySelectionChangeListener != null) {
        mySelectionChangeListener.plus(file);
      }
    }
  }

  public TreeNodeState getState(final DefaultMutableTreeNode node) {
    return getStateImpl(new StateWorker(node, myNodeConvertor));
  }

  private TreeNodeState getStateImpl(final StateWorker stateWorker) {
    if (stateWorker.getVf() == null) return TreeNodeState.CLEAR;

    final TreeNodeState stateSelf = myState.get(stateWorker.getVf());
    if (stateSelf != null) return stateSelf;

    final Ref<TreeNodeState> result = new Ref<>();
    stateWorker.iterateParents(myState, (virtualFile, state) -> {
      if (state != null) {
        if (TreeNodeState.SELECTED.equals(state) || TreeNodeState.HAVE_SELECTED_ABOVE.equals(state)) {
          result.set(myState.putAndPass(stateWorker.getVf(), TreeNodeState.HAVE_SELECTED_ABOVE));
        }
        return false; // exit
      }
      return true;
    });

    if (! result.isNull()) return  result.get();

    for (VirtualFile selected : myState.getSelected()) {
      if (VfsUtilCore.isAncestor(stateWorker.getVf(), selected, true)) {
        return myState.putAndPass(stateWorker.getVf(), TreeNodeState.HAVE_SELECTED_BELOW);
      }
    }
    return TreeNodeState.CLEAR;
  }

  public void removeSelection(final VirtualFile elementAt) {
    myState.remove(elementAt);
    myState.clearAllCachedMatching(f -> VfsUtilCore.isAncestor(f, elementAt, false) || VfsUtilCore.isAncestor(elementAt, f, false));
    if (mySelectionChangeListener != null) {
      mySelectionChangeListener.minus(elementAt);
    }
  }

  private static class StateWorker {
    private final DefaultMutableTreeNode myNode;
    private final Convertor<DefaultMutableTreeNode, VirtualFile> myConvertor;
    private VirtualFile myVf;

    private StateWorker(DefaultMutableTreeNode node, final Convertor<DefaultMutableTreeNode, VirtualFile> convertor) {
      myNode = node;
      myConvertor = convertor;
      myVf = myConvertor.convert(node);
    }

    public VirtualFile getVf() {
      return myVf;
    }

    public void iterateParents(final SelectedState<VirtualFile> states, final PairProcessor<VirtualFile, TreeNodeState> parentsProcessor) {
      DefaultMutableTreeNode current = (DefaultMutableTreeNode) myNode.getParent();
      // up cycle
      while (current != null) {
        final VirtualFile file = myConvertor.convert(current);
        if (file == null) return;

        final TreeNodeState state = states.get(file);
        if (! parentsProcessor.process(file, state)) return;
        current = (DefaultMutableTreeNode)current.getParent();
      }
    }
  }

  public void setSelectionChangeListener(@Nullable PlusMinus<VirtualFile> selectionChangeListener) {
    mySelectionChangeListener = selectionChangeListener;
  }
}