// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.treeWithCheckedNodes;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.changes.ui.PlusMinus;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.subtract;

/**
* @author irengrig
 *
 * see {@link SelectedState}
*/
public class SelectionManager {
  private final SelectedState<VirtualFile> myState;
  private final Convertor<? super DefaultMutableTreeNode, ? extends VirtualFile> myNodeConvertor;
  private PlusMinus<? super VirtualFile> mySelectionChangeListener;

  public SelectionManager(int selectedSize, int queueSize, final Convertor<? super DefaultMutableTreeNode, ? extends VirtualFile> nodeConvertor) {
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
      final Collection<VirtualFile> removed = subtract(old, selectedAfter);
      final Collection<VirtualFile> newlyAdded = subtract(selectedAfter, old);
      for (VirtualFile file : newlyAdded) {
        if (mySelectionChangeListener != null) {
          mySelectionChangeListener.plus(file);
        }
      }
      for (VirtualFile file : removed) {
        if (mySelectionChangeListener != null) {
          mySelectionChangeListener.minus(file);
        }
      }
    }
  }

  public boolean canAddSelection() {
    return myState.canAddSelection();
  }

  public void setSelection(Collection<? extends VirtualFile> files) {
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
    private final Convertor<? super DefaultMutableTreeNode, ? extends VirtualFile> myConvertor;
    private final VirtualFile myVf;

    private StateWorker(DefaultMutableTreeNode node, final Convertor<? super DefaultMutableTreeNode, ? extends VirtualFile> convertor) {
      myNode = node;
      myConvertor = convertor;
      myVf = myConvertor.convert(node);
    }

    public VirtualFile getVf() {
      return myVf;
    }

    public void iterateParents(final SelectedState<? super VirtualFile> states, final PairProcessor<? super VirtualFile, ? super TreeNodeState> parentsProcessor) {
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

  public void setSelectionChangeListener(@Nullable PlusMinus<? super VirtualFile> selectionChangeListener) {
    mySelectionChangeListener = selectionChangeListener;
  }
}